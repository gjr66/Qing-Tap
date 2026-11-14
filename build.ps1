param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildTools = '35.0.0',
    [string]$OutputDirectory = '',
    [string]$SigningKey = ''
)

$ErrorActionPreference = 'Stop'
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $JavaHome) { throw 'Set JAVA_HOME to JDK 17+ or pass -JavaHome.' }

$platform = Join-Path $SdkRoot 'platforms\android-35\android.jar'
$tools = Join-Path $SdkRoot "build-tools\$BuildTools"
if (-not (Test-Path -LiteralPath (Join-Path $tools 'aapt2.exe')) -and $BuildTools -eq '35.0.0') {
    $tools = Join-Path $SdkRoot 'build-tools\android-15'
}
$jdk = Join-Path $JavaHome 'bin'
$main = Join-Path $PSScriptRoot 'app\src\main'
$runtime = Join-Path $PSScriptRoot 'dependencies\onnxruntime-android-1.23.2.aar'
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $PSScriptRoot ('build\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
$build = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $build) { throw 'Choose a new output directory.' }
if (-not $SigningKey) { $SigningKey = Join-Path $PSScriptRoot 'build\local-test-signing.p12' }
$SigningKey = [IO.Path]::GetFullPath($SigningKey)

function Run([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Executable ($LASTEXITCODE)" }
}

foreach ($required in @($platform, "$tools\aapt2.exe", "$tools\lib\d8.jar",
    "$tools\lib\apksigner.jar", "$tools\zipalign.exe", "$jdk\javac.exe", $runtime)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file missing: $required" }
}

$null = New-Item -ItemType Directory -Force -Path "$build\classes", "$build\test-classes", "$build\generated", "$build\dex"
Run "$tools\aapt2.exe" @('compile', '--dir', "$main\res", '-o', "$build\resources.zip")
Run "$tools\aapt2.exe" @('link', '-I', $platform, '--manifest', "$main\AndroidManifest.xml",
    '--java', "$build\generated", '-o', "$build\base.apk", "$build\resources.zip")
$ort = New-Item -ItemType Directory -Path "$build\ort"
Push-Location $ort.FullName
try {
    Run "$jdk\jar.exe" @('xf', $runtime, 'classes.jar', 'jni')
} finally {
    Pop-Location
}
Copy-Item -LiteralPath "$build\ort\jni" -Destination "$build\ort\lib" -Recurse
$ortClasses = "$build\ort\classes.jar"

$sources = @(Get-ChildItem -LiteralPath "$main\java","$build\generated" -Filter '*.java' -Recurse | ForEach-Object FullName)
Run "$jdk\javac.exe" (@('-encoding', 'UTF-8', '--release', '8', '-classpath', "$platform;$ortClasses",
    '-d', "$build\classes") + $sources)
$testSources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\tests" -Filter '*.java' | ForEach-Object FullName)
Run "$jdk\javac.exe" (@('-encoding', 'UTF-8', '--release', '8', '-classpath', "$build\classes",
    '-d', "$build\test-classes") + $testSources)
Run "$jdk\java.exe" @('-cp', "$build\classes;$build\test-classes", 'local.qingtap.adskip.AdRulesTest')
Run "$jdk\java.exe" @('-cp', "$build\classes;$build\test-classes", 'local.qingtap.adskip.SplashReviewTest')
Run "$jdk\java.exe" @('-cp', "$build\classes;$build\test-classes", 'local.qingtap.adskip.LearningFlowTest')
Run "$jdk\java.exe" @('-cp', "$build\classes;$build\test-classes", 'local.qingtap.adskip.HomeLaunchTest')
Run "$jdk\java.exe" @('-cp', "$build\classes;$build\test-classes", 'local.qingtap.adskip.AppSelectionTest')

$classFiles = @(Get-ChildItem -LiteralPath "$build\classes" -Filter '*.class' -Recurse | ForEach-Object FullName)
Run "$jdk\java.exe" (@('-cp', "$tools\lib\d8.jar", 'com.android.tools.r8.D8', '--lib', $platform,
    '--min-api', '26', '--output', "$build\dex", $ortClasses) + $classFiles)
Run "$jdk\jar.exe" @('uf', "$build\base.apk", '-C', "$build\dex", 'classes.dex', '-C', "$build\ort", 'lib', '-C', $main, 'assets')
Run "$tools\zipalign.exe" @('4', "$build\base.apk", "$build\aligned.apk")

if (-not (Test-Path -LiteralPath $SigningKey)) {
    $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $SigningKey)
    Run "$jdk\keytool.exe" @('-genkeypair', '-keystore', $SigningKey, '-storetype', 'PKCS12',
        '-storepass', 'android', '-alias', 'qingtap', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '3650', '-dname', 'CN=QingTap Local Test')
}

[xml]$manifest = Get-Content -LiteralPath "$main\AndroidManifest.xml" -Raw
$versionName = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
$apk = "$build\QingTap-$versionName.apk"
Run "$jdk\java.exe" @('-jar', "$tools\lib\apksigner.jar", 'sign', '--ks', $SigningKey,
    '--ks-key-alias', 'qingtap', '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false',
    '--out', $apk, "$build\aligned.apk")
Run "$jdk\java.exe" @('-jar', "$tools\lib\apksigner.jar", 'verify', '--verbose', $apk)
Run "$tools\zipalign.exe" @('-c', '4', $apk)
Write-Output "APK: $apk"
