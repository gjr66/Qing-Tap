package local.qingtap.adskip;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AdTestBatch {
    enum Status { MATCH, REVIEW, ERROR }

    static final class Result {
        final Status status;
        final String pkg;
        final List<String> candidates;
        final float x;
        final float y;
        final long millis;
        final String evidence;
        final boolean buttonRead;

        Result(Status status, String pkg, List<String> candidates, float x, float y, long millis) {
            this(status, pkg, candidates, x, y, millis, "", false);
        }

        Result(Status status, String pkg, List<String> candidates, float x, float y, long millis, String evidence) {
            this(status, pkg, candidates, x, y, millis, evidence, false);
        }

        Result(Status status, String pkg, List<String> candidates, float x, float y, long millis, String evidence,
               boolean buttonRead) {
            this.status = status;
            this.pkg = pkg;
            this.candidates = candidates;
            this.x = x;
            this.y = y;
            this.millis = millis;
            this.evidence = evidence;
            this.buttonRead = buttonRead;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("status", status.name()).put("app", pkg)
                    .put("candidates", new JSONArray(candidates)).put("x", x).put("y", y).put("ms", millis)
                    .put("evidence", evidence).put("button_read", buttonRead);
        }

        static Result read(JSONObject value) throws JSONException {
            if (value == null) return null;
            List<String> candidates = new ArrayList<>();
            JSONArray apps = value.optJSONArray("candidates");
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) candidates.add(apps.getString(i));
            }
            return new Result(Status.valueOf(value.getString("status")), value.optString("app"), candidates,
                    (float) value.optDouble("x", -1), (float) value.optDouble("y", -1), value.optLong("ms"),
                    value.optString("evidence"), value.optBoolean("button_read"));
        }
    }

    static final class Item {
        final Uri image;
        String preview = "";
        Bitmap thumbnail;
        Result quick;
        Result detail;
        String savedApp = "";

        Item(Uri image) {
            this.image = image;
        }

        boolean resolved() {
            return !savedApp.isEmpty() || best() != null;
        }

        Result best() {
            if (quick != null && quick.status == Status.MATCH) return quick;
            if (detail != null && detail.status == Status.MATCH) return detail;
            return null;
        }

        Result marker() {
            Result match = best();
            if (match != null) return match;
            return detail != null && detail.x >= 0 ? detail : null;
        }
    }

    static final class Scan {
        final Result result;
        final String preview;

        final ScreenshotLesson lesson;

        Scan(Result result, String preview) {
            this(result, preview, null);
        }

        Scan(Result result, String preview, ScreenshotLesson lesson) {
            this.result = result;
            this.preview = preview;
            this.lesson = lesson;
        }
    }

    static final class DetailedReader implements AutoCloseable {
        private final Context context;
        private ScreenshotOcr engine;
        private boolean attempted;
        private final Map<String, Set<String>> brands = new HashMap<>();

        DetailedReader(Context context) {
            this.context = context;
            try {
                for (AppCatalog.Entry app : AppCatalog.load(context, Collections.emptySet())) {
                    String label = ScreenshotReview.clean(app.label);
                    if (label.length() >= 2 && !label.equals(ScreenshotReview.clean(app.pkg))) addBrand(label, app.pkg);
                    if ("tv.danmaku.bili".equals(app.pkg)) addBrand("bilibili", app.pkg);
                    if ("com.cmcc.cmvideo".equals(app.pkg)) addBrand("咪咕", app.pkg);
                    if ("com.tencent.qqlive".equals(app.pkg)) addBrand("tencentvideo", app.pkg);
                }
            } catch (SecurityException unavailable) {
                android.util.Log.w("QingTap", "Installed app names unavailable", unavailable);
            }
        }

        private void addBrand(String label, String pkg) {
            brands.computeIfAbsent(label, unused -> new LinkedHashSet<>()).add(pkg);
        }

        ScreenshotReview read(int[] pixels, int width, int height) {
            try {
                if (!attempted) {
                    attempted = true;
                    byte[] detector = asset("det.onnx");
                    byte[] recognizer = asset("rec.onnx");
                    try (InputStream words = context.getAssets().open("ocr/dictionary.txt")) {
                        engine = new ScreenshotOcr(detector, recognizer, words);
                    }
                }
                return engine == null ? null : ScreenshotReview.inspect(engine.read(pixels, width, height),
                        brands, width, height);
            } catch (IOException | ai.onnxruntime.OrtException | RuntimeException | LinkageError unavailable) {
                android.util.Log.w("QingTap", "Offline screenshot OCR unavailable", unavailable);
                return null;
            }
        }

        private byte[] asset(String name) throws IOException {
            try (InputStream input = context.getAssets().open("ocr/" + name);
                 ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[65536];
                int count;
                while ((count = input.read(chunk)) != -1) data.write(chunk, 0, count);
                return data.toByteArray();
            }
        }

        @Override public void close() {
            if (engine == null) return;
            try {
                engine.close();
            } catch (ai.onnxruntime.OrtException error) {
                android.util.Log.w("QingTap", "Closing screenshot OCR failed", error);
            }
        }
    }

    private final SharedPreferences prefs;
    final List<Item> items = new ArrayList<>();

    AdTestBatch(Context context) {
        prefs = context.getSharedPreferences("ad_test_batch", Context.MODE_PRIVATE);
        try {
            JSONArray rows = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                try {
                    Item item = new Item(Uri.parse(row.getString("image")));
                    item.preview = row.optString("preview");
                    item.quick = Result.read(row.optJSONObject("quick"));
                    item.detail = Result.read(row.optJSONObject("detail"));
                    item.savedApp = row.optString("saved_app");
                    items.add(item);
                } catch (JSONException | IllegalArgumentException malformed) {
                    // Skip the damaged row.
                }
            }
        } catch (JSONException malformed) {
            // Start with an empty batch if the saved list cannot be read.
        }
    }

    void save() {
        JSONArray rows = new JSONArray();
        for (Item item : items) {
            try {
                JSONObject row = new JSONObject().put("image", item.image.toString()).put("preview", item.preview)
                        .put("saved_app", item.savedApp);
                if (item.quick != null) row.put("quick", item.quick.json());
                if (item.detail != null) row.put("detail", item.detail.json());
                rows.put(row);
            } catch (JSONException malformed) {
                // Leave the rest of the batch readable.
            }
        }
        prefs.edit().putString("items", rows.toString()).apply();
    }

    void clearProcessed() {
        items.removeIf(Item::resolved);
        save();
    }

    boolean apply(Context context, Item item, Scan scan, boolean detailed) {
        if (!scan.preview.isEmpty()) item.preview = scan.preview;
        if (detailed) item.detail = scan.result;
        else {
            item.quick = scan.result;
            item.detail = null;
        }
        if (scan.lesson == null || !scan.lesson.save(context, scan.result.pkg)) return false;
        item.savedApp = scan.result.pkg;
        return true;
    }

    static Scan scan(Context context, Uri image, List<TaughtTargets.Target> lessons, DetailedReader reader,
                     boolean needsPreview) {
        boolean detailed = reader != null;
        long start = SystemClock.elapsedRealtime();
        Bitmap original = null;

        try {
            original = AdImage.read(context, image);
            if (original == null) throw new IOException("Unable to decode image");
            ScreenshotFrame imageFrame = ScreenshotFrame.read(original, detailed ? 760 : 540);
            int width = imageFrame.width, height = imageFrame.height;
            int[] pixels = imageFrame.pixels;
            String preview = needsPreview ? LessonPreview.encode(original) : "";
            VisionFinder.Point damai = DamaiSplash.find(pixels, width, height);
            ScreenshotFrame ruleFrame = ScreenshotFrame.read(original, VisionFinder.LESSON_FRAME_WIDTH);
            if (damai != null) {
                float x = damai.x / (float) width, y = damai.y / (float) height;
                ScreenshotLesson lesson = ScreenshotLesson.create(original, ruleFrame, x, y);
                if (lesson == null) lesson = ScreenshotLesson.create(original, imageFrame, x, y);
                Status status = lesson == null ? Status.REVIEW : Status.MATCH;
                return new Scan(new Result(status, DamaiSplash.PACKAGE, Collections.emptyList(),
                        x, y, SystemClock.elapsedRealtime() - start, "大麦开屏规则", true), preview, lesson);
            }
            SceneFingerprint scene = ruleFrame.scene();
            List<TaughtSearch.Match> hits = TaughtSearch.search(ruleFrame.pixels, ruleFrame.width,
                    ruleFrame.height, lessons, scene, detailed);
            TaughtSearch.Match winner = TaughtSearch.unambiguous(hits, ruleFrame.width, ruleFrame.height);
            if (winner != null) {
                boolean sameScene = scene != null && scene.matches(winner.lesson.scene);
                float x = sameScene ? (float) winner.lesson.xRatio() : winner.point.x / (float) ruleFrame.width;
                float y = sameScene ? (float) winner.lesson.yRatio() : winner.point.y / (float) ruleFrame.height;
                ScreenshotLesson lesson = sameScene ? ScreenshotLesson.create(original, ruleFrame, x, y) : null;
                return new Scan(new Result(Status.MATCH, winner.lesson.pkg, Collections.emptyList(), x, y,
                        SystemClock.elapsedRealtime() - start, "已有学习规则"), preview, lesson);
            }
            Set<String> apps = new LinkedHashSet<>();
            for (TaughtSearch.Match hit : hits) apps.add(hit.lesson.pkg);
            if (!detailed) {
                return new Scan(new Result(Status.REVIEW, "", new ArrayList<>(apps), -1, -1,
                        SystemClock.elapsedRealtime() - start), preview);
            }

            ScreenshotReview read = reader.read(pixels, width, height);
            if (read != null) apps.addAll(read.apps);
            ScreenshotOcr.Line button = read == null ? null : read.button;
            VisionFinder.Point suspect = button == null ? VisionFinder.find(pixels, width, height) : null;
            float x = button != null ? button.box.x() / width : suspect != null ? suspect.x / (float) width : -1;
            float y = button != null ? button.box.y() / height : suspect != null ? suspect.y / (float) height : -1;
            String evidence = read == null ? "文字识别不可用" : button == null ? "" : "读到「" + button.text + "」";
            ScreenshotLesson lesson = read != null && read.safeToTeach && apps.size() == 1
                    ? ScreenshotLesson.create(original, ruleFrame, x, y) : null;
            String pkg = apps.size() == 1 ? apps.iterator().next() : "";
            return new Scan(new Result(lesson == null ? Status.REVIEW : Status.MATCH, pkg, new ArrayList<>(apps),
                    x, y, SystemClock.elapsedRealtime() - start, evidence, button != null && button.confidence >= .85f),
                    preview, lesson);
        } catch (IOException | RuntimeException failed) {
            return new Scan(new Result(Status.ERROR, "", new ArrayList<>(), -1, -1,
                    SystemClock.elapsedRealtime() - start), "");
        } finally {
            if (original != null) original.recycle();
        }
    }
}
