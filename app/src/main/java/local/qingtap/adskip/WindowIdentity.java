package local.qingtap.adskip;

// Package names can be identical in the main app and its clone. The window in the triggering
// accessibility event must also match the current root/control before any operation is scheduled.
final class WindowIdentity {
    static boolean matches(String expectedPackage, int expectedWindow, CharSequence actualPackage, int actualWindow) {
        return expectedPackage != null && actualPackage != null && expectedWindow >= 0
                && actualWindow == expectedWindow && expectedPackage.contentEquals(actualPackage);
    }

    private WindowIdentity() {}
}
