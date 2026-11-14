package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Targets the user taught by pointing at a screenshot. Unlike the automatic memory these carry a small
// grayscale fingerprint of the crop around the tapped spot, so a live frame can be compared instead of
// blindly trusting a coordinate.
final class TaughtTargets {
    interface Store {
        String read();

        void write(String value);
    }

    static final class Target {
        final String pkg;
        final String activity; // "*" means any activity of that package
        final int xMilli;
        final int yMilli;
        final int aspectMilli;
        final int[] template;
        final int[] fingerprint;
        private final String fingerprintData;
        final String preview;
        final SceneFingerprint scene;
        final String id;
        int hits;
        int misses;

        Target(String pkg, String activity, int xMilli, int yMilli, int aspectMilli, int[] template,
               String fingerprintData, String preview, SceneFingerprint scene, int hits, int misses) {
            this(pkg, activity, xMilli, yMilli, aspectMilli, template, fingerprintData, preview, scene, hits, misses, "");
        }

        Target(String pkg, String activity, int xMilli, int yMilli, int aspectMilli, int[] template,
               String fingerprintData, String preview, SceneFingerprint scene, int hits, int misses, String id) {
            this.pkg = pkg;
            this.activity = activity == null || activity.isEmpty() ? "*" : activity;
            this.xMilli = xMilli;
            this.yMilli = yMilli;
            this.aspectMilli = aspectMilli;
            this.template = template;
            this.fingerprintData = fingerprintData;
            int separator = fingerprintData.indexOf('|');
            this.fingerprint = AdFingerprint.decode(separator < 0 ? fingerprintData
                    : fingerprintData.substring(0, separator));
            this.preview = preview;
            this.scene = scene;
            this.id = id;
            this.hits = hits;
            this.misses = misses;
        }

        Target(String pkg, String activity, int xMilli, int yMilli, int aspectMilli, int[] template,
               int hits, int misses) {
            this(pkg, activity, xMilli, yMilli, aspectMilli, template, "", "", null, hits, misses);
        }

        String key() {
            return pkg + "/" + activity + (id.isEmpty() ? "" : "/" + id);
        }

        double xRatio() {
            return xMilli / 1000.0;
        }

        double yRatio() {
            return yMilli / 1000.0;
        }

        boolean hasFingerprint() {
            return fingerprint != null && fingerprint.length == AdFingerprint.LENGTH;
        }

        boolean hasTemplate() {
            return template != null && template.length == TEMPLATE_SIZE * TEMPLATE_SIZE;
        }
    }

    static final class Sample {
        final double x, y, aspect;
        final int[] template;
        final SceneFingerprint scene;
        final String preview;

        private Sample(double x, double y, double aspect, int[] template, SceneFingerprint scene, String preview) {
            this.x = x;
            this.y = y;
            this.aspect = aspect;
            this.template = template;
            this.scene = scene;
            this.preview = preview;
        }

        static Sample from(int[] pixels, int width, int height, double x, double y) {
            if (!(x >= 0 && x < 1 && y >= 0 && y < 1)) return null;
            double savedX = Math.round(x * 1000) / 1000.0;
            double savedY = Math.round(y * 1000) / 1000.0;
            int[] template = VisionFinder.templateAt(pixels, width, height, savedX, savedY);
            if (!VisionFinder.usableTemplate(template)) return null;
            return new Sample(savedX, savedY, width / (double) height, template,
                    SceneFingerprint.of(pixels, width, height), "");
        }

        Sample withPreview(String image) {
            return new Sample(x, y, aspect, template, scene, image);
        }
    }

    boolean saveSample(String pkg, Sample sample) {
        if (pkg == null || pkg.isEmpty() || sample == null) return false;
        save(pkg, "*", sample.x, sample.y, sample.aspect, sample.template, null, sample.preview, sample.scene);
        return true;
    }

    static final int TEMPLATE_SIZE = 16;
    private final Store store;
    private final List<Target> targets = new ArrayList<>();
    private boolean loaded;

    TaughtTargets(Store store) {
        this.store = store;
    }

    // Exact activity first, then the wildcard entry, so a specific page can override a broad lesson.
    Target find(String pkg, String activity) {
        List<Target> matches = forWindow(pkg, activity);
        return matches.isEmpty() ? null : matches.get(0);
    }

    List<Target> forWindow(String pkg, String activity) {
        load();
        List<Target> matches = new ArrayList<>();
        List<Target> fallback = new ArrayList<>();
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            if (!target.pkg.equals(pkg)) continue;
            if ("*".equals(target.activity)) fallback.add(target);
            else if (target.activity.equals(activity)) matches.add(target);
        }
        matches.addAll(fallback);
        return matches;
    }

    void save(String pkg, String activity, double xRatio, double yRatio, double aspect, int[] template,
              int[] fingerprint) {
        save(pkg, activity, xRatio, yRatio, aspect, template, fingerprint, "");
    }

    void save(String pkg, String activity, double xRatio, double yRatio, double aspect, int[] template,
              int[] fingerprint, String preview) {
        save(pkg, activity, xRatio, yRatio, aspect, template, fingerprint, preview, null);
    }

    void save(String pkg, String activity, double xRatio, double yRatio, double aspect, int[] template,
              int[] fingerprint, String preview, SceneFingerprint scene) {
        load();
        String normalized = activity == null || activity.isEmpty() ? "*" : activity;
        String id = UUID.randomUUID().toString();
        int xMilli = (int) Math.round(xRatio * 1000);
        int yMilli = (int) Math.round(yRatio * 1000);
        int hits = 0, misses = 0;
        for (int i = 0; !preview.isEmpty() && i < targets.size(); i++) {
            Target previous = targets.get(i);
            if (previous.pkg.equals(pkg) && previous.activity.equals(normalized) && previous.preview.equals(preview)) {
                id = previous.id;
                if (previous.xMilli == xMilli && previous.yMilli == yMilli) {
                    hits = previous.hits;
                    misses = previous.misses;
                }
                targets.remove(i);
                break;
            }
        }
        targets.add(new Target(pkg, normalized, xMilli, yMilli, (int) Math.round(aspect * 1000), template,
                AdFingerprint.encode(fingerprint), preview, scene, hits, misses, id));
        save();
    }

    // Kept so the older callers and tests keep compiling.
    void save(String pkg, String activity, double xRatio, double yRatio, double aspect, int[] template) {
        save(pkg, activity, xRatio, yRatio, aspect, template, null);
    }

    void confirmed(Target target) {
        record(target, true);
    }

    void missed(Target target) {
        record(target, false);
    }

    private void record(Target target, boolean matched) {
        if (target == null) return;
        load();
        for (Target stored : targets) {
            if (!stored.key().equals(target.key())) continue;
            if (matched) stored.hits++;
            else stored.misses++;
            save();
            break;
        }
    }

    List<Target> all() {
        load();
        return new ArrayList<>(targets);
    }

    void remove(String key) {
        load();
        targets.removeIf(target -> target.key().equals(key));
        save();
    }

    boolean move(String key, String pkg) {
        load();
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (!target.key().equals(key)) continue;
            if (target.pkg.equals(pkg)) return true;
            String id = target.id.isEmpty() ? UUID.randomUUID().toString() : target.id;
            targets.set(i, new Target(pkg, "*", target.xMilli, target.yMilli, target.aspectMilli,
                    target.template, target.fingerprintData, target.preview, target.scene,
                    target.hits, target.misses, id));
            save();
            return true;
        }
        return false;
    }

    int size() {
        load();
        return targets.size();
    }

    void clear() {
        load();
        targets.clear();
        save();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 8) continue;
            try {
                int[] template = decodeTemplate(parts[7]);
                targets.add(new Target(parts[0], parts[1], Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), template,
                        parts.length > 8 ? parts[8] : "", parts.length > 9 ? parts[9] : "",
                        parts.length > 10 ? SceneFingerprint.decode(parts[10]) : null,
                        Integer.parseInt(parts[5]), Integer.parseInt(parts[6]), parts.length > 11 ? parts[11] : ""));
            } catch (NumberFormatException ignored) {
                // Half written line.
            }
        }
    }

    private void save() {
        StringBuilder builder = new StringBuilder();
        for (Target target : targets) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(target.pkg).append('\t')
                    .append(target.activity).append('\t')
                    .append(target.xMilli).append('\t')
                    .append(target.yMilli).append('\t')
                    .append(target.aspectMilli).append('\t')
                    .append(target.hits).append('\t')
                    .append(target.misses).append('\t')
                    .append(encodeTemplate(target.template)).append('\t')
                    .append(target.fingerprintData).append('\t')
                    .append(target.preview).append('\t')
                    .append(target.scene == null ? "" : target.scene.encode()).append('\t')
                    .append(target.id);
        }
        store.write(builder.toString());
    }

    static String encodeTemplate(int[] template) {
        if (template == null || template.length == 0) return "";
        StringBuilder builder = new StringBuilder(template.length * 3);
        for (int value : template) {
            int clamped = Math.max(0, Math.min(255, value));
            builder.append(Integer.toString(clamped, 36));
            builder.append(',');
        }
        return builder.toString();
    }

    static int[] decodeTemplate(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.split(",");
        int[] template = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty()) continue;
            try {
                template[index] = Integer.parseInt(parts[index], 36);
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return template;
    }
}
