package local.qingtap.adskip;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Architectural regression: the actual compiled service must not launch another application.
// An AccessibilityNode package/window does not supply a trustworthy cloned-profile launch identity.
public final class CloneSafetyTest {
    public static void main(String[] args) throws Exception {
        String pkg = "tv.danmaku.bili";
        if (!WindowIdentity.matches(pkg, 17, pkg, 17)
                || WindowIdentity.matches(pkg, 17, pkg, 18)
                || WindowIdentity.matches(pkg, -1, pkg, -1)
                || WindowIdentity.matches(pkg, 17, "com.tencent.mm", 17)
                || WindowIdentity.matches(pkg, 17, null, 17)) {
            throw new AssertionError("same package in another or unknown window must never match");
        }
        try (InputStream input = CloneSafetyTest.class.getResourceAsStream("AdSkipService.class")) {
            if (input == null) throw new AssertionError("compiled accessibility service missing");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            String pool = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            for (String obsolete : new String[]{"memoryClear", "hintClear", "feedbackClear", "forgetHook"}) {
                if (pool.contains(obsolete)) throw new AssertionError("stale service callback: " + obsolete);
            }
            for (String forbidden : new String[]{"startActivity", "startActivities", "startIntentSender",
                    "getLaunchIntentForPackage", "requestHomeEntry", "HomeEntryFinder", "bilibili://home"}) {
                if (pool.contains(forbidden)) throw new AssertionError("cross-profile launch path returned: " + forbidden);
            }
        }
        System.out.println("PASS window identity isolates same-package clones; compiled service has no external app launch path");
    }
}
