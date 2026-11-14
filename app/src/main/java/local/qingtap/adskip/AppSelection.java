package local.qingtap.adskip;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class AppSelection {
    final Set<String> splash;
    final Set<String> inline;
    final Set<String> blockedSplash;
    final Set<String> blockedInline;
    final Set<String> pendingSplash;
    final Set<String> pendingInline;

    AppSelection(Set<String> splash, Set<String> inline, Set<String> blockedSplash, Set<String> blockedInline,
                 Set<String> pendingSplash, Set<String> pendingInline) {
        this.splash = new HashSet<>(splash);
        this.inline = new HashSet<>(inline);
        this.blockedSplash = new HashSet<>(blockedSplash);
        this.blockedInline = new HashSet<>(blockedInline);
        this.splash.removeAll(blockedSplash);
        this.inline.removeAll(blockedInline);
        this.pendingSplash = new HashSet<>(pendingSplash);
        this.pendingSplash.removeAll(blockedSplash);
        this.pendingSplash.removeAll(this.splash);
        this.pendingInline = new HashSet<>(pendingInline);
        this.pendingInline.removeAll(blockedInline);
        this.pendingInline.removeAll(this.inline);
    }

    Set<String> selected() {
        Set<String> all = new HashSet<>(splash);
        all.addAll(inline);
        return all;
    }

    Set<String> blocked() {
        Set<String> all = new HashSet<>(blockedSplash);
        all.addAll(blockedInline);
        return all;
    }

    int addNew(Collection<String> eligible) {
        int added = 0;
        for (String pkg : eligible) {
            // Keep existing single-category choices unless the other category was explicitly unblocked.
            boolean newApp = !splash.contains(pkg) && !inline.contains(pkg);
            boolean addSplash = (newApp || pendingSplash.contains(pkg))
                    && !blockedSplash.contains(pkg) && splash.add(pkg);
            boolean addInline = (newApp || pendingInline.contains(pkg))
                    && !blockedInline.contains(pkg) && inline.add(pkg);
            if (addSplash) pendingSplash.remove(pkg);
            if (addInline) pendingInline.remove(pkg);
            if (addSplash || addInline) added++;
        }
        return added;
    }

    void setEnabled(String pkg, boolean embedded, boolean enabled) {
        Set<String> chosen = embedded ? inline : splash;
        Set<String> blocked = embedded ? blockedInline : blockedSplash;
        Set<String> pending = embedded ? pendingInline : pendingSplash;
        pending.remove(pkg);
        if (enabled) {
            blocked.remove(pkg);
            chosen.add(pkg);
        } else if (chosen.remove(pkg)) {
            blocked.add(pkg);
        }
    }

    void setBlocked(String pkg, boolean embedded, boolean block) {
        Set<String> chosen = embedded ? inline : splash;
        Set<String> blocked = embedded ? blockedInline : blockedSplash;
        Set<String> pending = embedded ? pendingInline : pendingSplash;
        if (block) {
            chosen.remove(pkg);
            pending.remove(pkg);
            blocked.add(pkg);
        } else if (blocked.remove(pkg)) {
            pending.add(pkg);
        }
    }
}
