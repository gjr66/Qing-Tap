package local.qingtap.adskip;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class AppSelectionTest {
    public static void main(String[] args) {
        AppSelection state = new AppSelection(set("both", "onlySplash"), set("both", "onlyInline"),
                set("oldOff"), set("oldOff"), set(), set());
        Set<String> installed = set("both", "onlySplash", "onlyInline", "oldOff", "newApp");
        check(state.addNew(installed) == 1);
        check(state.splash.equals(set("both", "onlySplash", "newApp")));
        check(state.inline.equals(set("both", "onlyInline", "newApp")));
        check(state.addNew(installed) == 0);

        state.setEnabled("both", true, false);
        check(!state.inline.contains("both") && state.splash.contains("both"));
        check(state.blockedInline.contains("both") && !state.blockedSplash.contains("both"));
        check(state.addNew(installed) == 0 && !state.inline.contains("both"));

        state.setBlocked("both", false, true);
        check(!state.selected().contains("both"));
        check(state.blockedSplash.contains("both") && state.blockedInline.contains("both"));
        check(state.addNew(installed) == 0);

        state.setBlocked("both", true, false);
        check(!state.selected().contains("both"));
        check(state.blockedSplash.contains("both") && !state.blockedInline.contains("both"));
        check(state.addNew(installed) == 1);
        check(!state.splash.contains("both") && state.inline.contains("both"));

        state.setEnabled("both", false, true);
        check(state.splash.contains("both") && state.inline.contains("both"));
        check(!state.blocked().contains("both"));
        state.setBlocked("newApp", true, true);

        AppSelection reloaded = reload(state);
        check(reloaded.addNew(installed) == 0);
        check(reloaded.splash.contains("newApp") && !reloaded.inline.contains("newApp"));
        check(reloaded.blockedInline.contains("newApp") && !reloaded.blockedSplash.contains("newApp"));
        check(reloaded.blockedSplash.contains("oldOff") && reloaded.blockedInline.contains("oldOff"));
        reloaded.setBlocked("newApp", true, false);
        reloaded = reload(reloaded);
        check(reloaded.splash.contains("newApp") && !reloaded.inline.contains("newApp"));
        check(reloaded.addNew(set("onlySplash", "onlyInline")) == 0);
        check(reloaded.addNew(installed) == 1);
        check(reloaded.inline.contains("newApp") && reloaded.pendingInline.isEmpty());
        check(!reloaded.inline.contains("onlySplash") && !reloaded.splash.contains("onlyInline"));
        check(reloaded.addNew(installed) == 0);

        reloaded.setBlocked("newApp", false, true);
        reloaded.setBlocked("newApp", false, false);
        reloaded.setBlocked("newApp", false, true);
        check(reloaded.addNew(installed) == 0);
        check(reloaded.blockedSplash.contains("newApp") && reloaded.inline.contains("newApp"));

        reloaded.setBlocked("newApp", false, false);
        reloaded.setEnabled("newApp", false, true);
        check(reloaded.pendingSplash.isEmpty() && reloaded.addNew(installed) == 0);
        System.out.println("PASS independent category blacklists and bulk re-add after unblocking");
    }

    private static AppSelection reload(AppSelection state) {
        return new AppSelection(state.splash, state.inline, state.blockedSplash, state.blockedInline,
                state.pendingSplash, state.pendingInline);
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
}
