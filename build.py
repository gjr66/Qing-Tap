#!/usr/bin/env python3
"""Build and verify a locally signed QingTap APK using an existing JDK and Android SDK."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-root', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--build-tools', default='35.0.0')
    parser.add_argument('--output-directory')
    parser.add_argument('--signing-key', help='Existing PKCS12 key using alias qingtap and the local test password')
    args = parser.parse_args()
    if not args.sdk_root or not args.java_home:
        parser.error('Set --sdk-root and --java-home, or ANDROID_HOME and JAVA_HOME.')

    root = Path(__file__).resolve().parent
    source = root / 'app/src/main'
    sdk = Path(args.sdk_root).expanduser().resolve()
    jdk = Path(args.java_home).expanduser().resolve() / 'bin'
    tools = sdk / 'build-tools' / args.build_tools
    platform = sdk / 'platforms/android-35/android.jar'
    runtime = root / 'dependencies/onnxruntime-android-1.23.2.aar'
    suffix = '.exe' if os.name == 'nt' else ''
    java = jdk / ('java' + suffix)
    javac = jdk / ('javac' + suffix)
    keytool = jdk / ('keytool' + suffix)
    aapt = tools / ('aapt2' + suffix)
    align = tools / ('zipalign' + suffix)
    d8 = tools / 'lib/d8.jar'
    signer = tools / 'lib/apksigner.jar'
    for path in (java, javac, keytool, aapt, align, d8, signer, platform, runtime):
        if not path.is_file():
            parser.error('Required file missing: ' + str(path))

    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    output = Path(args.output_directory).expanduser().resolve() if args.output_directory else root / 'build' / stamp
    output.mkdir(parents=True, exist_ok=False)
    for name in ('classes', 'test-classes', 'generated', 'dex'):
        (output / name).mkdir()
    log = (output / 'build.log').open('w', encoding='utf-8')

    def run(*command):
        command = [str(value) for value in command]
        print('Running ' + Path(command[0]).name, flush=True)
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            print(line, end='', flush=True)
            log.write(line)
        log.flush()
        if process.wait():
            raise RuntimeError('Build step failed: ' + Path(command[0]).name)

    manifest = ET.parse(source / 'AndroidManifest.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    version = manifest.attrib[android + 'versionName']
    if not version or any(c not in '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-_' for c in version):
        raise ValueError('Invalid version name for output filename')
    run(aapt, 'compile', '--dir', source / 'res', '-o', output / 'resources.zip')
    run(aapt, 'link', '-I', platform, '--manifest', source / 'AndroidManifest.xml',
        '--java', output / 'generated', '-o', output / 'base.apk', output / 'resources.zip')
    ort_classes = output / 'ort-classes.jar'
    with zipfile.ZipFile(runtime) as archive:
        ort_classes.write_bytes(archive.read('classes.jar'))
    app_sources = sorted((source / 'java').rglob('*.java')) + sorted((output / 'generated').rglob('*.java'))
    classpath = os.pathsep.join(map(str, (platform, ort_classes)))
    run(javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', classpath,
        '-d', output / 'classes', *app_sources)
    test_sources = sorted((root / 'tests').glob('*.java'))
    run(javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', output / 'classes',
        '-d', output / 'test-classes', *test_sources)
    test_classpath = os.pathsep.join(map(str, (output / 'classes', output / 'test-classes')))
    for name in ('AdRulesTest', 'SplashReviewTest', 'LearningFlowTest', 'HomeLaunchTest', 'AppSelectionTest'):
        run(java, '-cp', test_classpath, 'local.qingtap.adskip.' + name)
    run(java, '-cp', d8, 'com.android.tools.r8.D8', '--lib', platform,
        '--min-api', '26', '--output', output / 'dex', ort_classes,
        *sorted((output / 'classes').rglob('*.class')))
    with zipfile.ZipFile(output / 'base.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
        for path in sorted((output / 'dex').glob('*.dex')):
            apk.write(path, path.name)
        with zipfile.ZipFile(runtime) as archive:
            for name in archive.namelist():
                if name.startswith('jni/') and name.endswith('.so'):
                    apk.writestr('lib/' + name[4:], archive.read(name))
        for path in sorted((source / 'assets').rglob('*')):
            relative = path.relative_to(source)
            if path.is_file() and not any(part.startswith('.') for part in relative.parts):
                apk.write(path, relative.as_posix())
    run(align, '4', output / 'base.apk', output / 'aligned.apk')

    signing_key = Path(args.signing_key).expanduser().resolve() if args.signing_key else root / 'build/local-test-signing.p12'
    if not signing_key.exists():
        signing_key.parent.mkdir(parents=True, exist_ok=True)
        run(keytool, '-genkeypair', '-keystore', signing_key, '-storetype', 'PKCS12',
            '-storepass', 'android', '-alias', 'qingtap', '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '3650', '-dname', 'CN=QingTap Local Test')
        if os.name != 'nt':
            signing_key.chmod(0o600)
    apk = output / ('QingTap-' + version + '.apk')
    run(java, '-jar', signer, 'sign', '--ks', signing_key, '--ks-key-alias', 'qingtap',
        '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', apk, output / 'aligned.apk')
    run(java, '-jar', signer, 'verify', '--verbose', '--print-certs', apk)
    run(align, '-c', '4', apk)
    run(aapt, 'dump', 'badging', apk)
    with zipfile.ZipFile(apk) as archive:
        bad = archive.testzip()
        if bad:
            raise RuntimeError('Corrupt APK member: ' + bad)
        for name in ('classes.dex', 'AndroidManifest.xml', 'assets/ocr/det.onnx', 'assets/ocr/rec.onnx',
                     'lib/arm64-v8a/libonnxruntime.so', 'lib/arm64-v8a/libonnxruntime4j_jni.so'):
            archive.getinfo(name)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (output / (apk.name + '.sha256')).write_text(digest + '  ' + apk.name + '\n', encoding='utf-8')
    metadata = dict(package=manifest.attrib['package'], version_name=version,
                    version_code=int(manifest.attrib[android + 'versionCode']),
                    apk=apk.name, size_bytes=apk.stat().st_size, sha256=digest,
                    tests='all 5 suites passed', device_tested=False,
                    signing='local test key', build_tools=args.build_tools,
                    minimum_api=26, target_api=35)
    (output / 'build-info.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    log.close()
    print('APK: ' + str(apk), flush=True)
    print('SHA256: ' + digest, flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
