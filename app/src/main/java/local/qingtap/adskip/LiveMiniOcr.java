package local.qingtap.adskip;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// One worker owns the native sessions. Never load models or run OCR on the accessibility thread.
final class LiveMiniOcr implements AutoCloseable {
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    final LightCubeCover locker;
    final LightCubeCard card;
    final LightCubeModal modal;
    private ScreenshotOcr engine;
    private boolean attempted;
    private volatile boolean closed;
    LiveMiniOcr(Context context) {
        this.context = context;
        LightCubeCover loaded = null;
        try {
            loaded = new LightCubeCover(reference("cabinet", 28, 28), reference("initializing", 28, 28),
                    reference("countdown", 90, 120), reference("title", 112, 15));
        } catch (java.io.IOException invalid) {
            android.util.Log.w("QingTap", "Locker launch images unavailable", invalid);
        }
        locker = loaded;
        LightCubeCard loadedCard = null;
        try {
            loadedCard = new LightCubeCard(reference("pickup", 84, 19), reference("ad-badge", 42, 23));
        } catch (java.io.IOException missing) {
            android.util.Log.w("QingTap", "Locker card references unavailable", missing);
        }
        card = loadedCard;
        LightCubeModal loadedModal = null;
        try {
            loadedModal = new LightCubeModal(reference("modal-badge", 52, 20), reference("modal-tab", 26, 27));
        } catch (java.io.IOException missing) {
            android.util.Log.w("QingTap", "Locker modal references unavailable", missing);
        }
        modal = loadedModal;
    }

    private int[] reference(String name, int width, int height) throws java.io.IOException {
        try (InputStream input = context.getAssets().open("lightcube/" + name + ".png")) {
            android.graphics.Bitmap image = android.graphics.BitmapFactory.decodeStream(input);
            if (image == null) throw new java.io.IOException(name);
            try {
                if (image.getWidth() != width || image.getHeight() != height)
                    throw new java.io.IOException(name);
                int[] pixels = new int[width * height];
                image.getPixels(pixels, 0, width, 0, 0, width, height);
                return pixels;
            } finally { image.recycle(); }
        }
    }
    void warm() { worker.execute(this::load); }
    private void load() {
        if (attempted) return;
        attempted = true;
        try (InputStream words = context.getAssets().open("ocr/dictionary.txt")) {
            engine = new ScreenshotOcr(asset("det.onnx"), asset("rec.onnx"), words);
        } catch (Exception | LinkageError unavailable) {
            android.util.Log.w("QingTap", "Mini-app OCR unavailable", unavailable);
        }
    }
    void read(ScreenshotFrame frame, Consumer<MiniProgramVision.Target> callback) {
        if (closed) { callback.accept(null); return; }
        try { worker.execute(() -> {
            load();
            MiniProgramVision.Target target = null;
            try {
                if (engine != null) {
                    // Top half: WeChat cover ads and most mini-app interstitials put “4秒｜关闭” here.
                    List<ScreenshotOcr.Line> top = engine.read(frame.pixels, frame.width,
                            Math.max(32, frame.height / 2));
                    target = MiniProgramVision.inspect(top, frame.width, frame.height);
                    // Bottom strip: Alipay often keeps the ad badge at the top and “关闭广告” at the
                    // bottom edge. Read it only when the top pass already proved an ad badge and no
                    // payment/authorization wording was seen.
                    if (target == null && !MiniProgramVision.sensitive(top)
                            && MiniProgramVision.hasBadge(top, frame.width, frame.height)) {
                        int bottomTop = frame.height * 70 / 100;
                        int bottomHeight = frame.height - bottomTop;
                        if (bottomHeight > 40) {
                            int[] pixels = new int[frame.width * bottomHeight];
                            System.arraycopy(frame.pixels, bottomTop * frame.width, pixels, 0, pixels.length);
                            List<ScreenshotOcr.Line> bottom = shift(
                                    engine.read(pixels, frame.width, bottomHeight), bottomTop);
                            target = MiniProgramVision.inspectBottom(bottom, frame.width, frame.height);
                        }
                    }
                }
            } catch (Exception | LinkageError unavailable) {
                android.util.Log.w("QingTap", "Mini-app OCR read failed", unavailable);
            }
            callback.accept(target);
        }); } catch (java.util.concurrent.RejectedExecutionException stopped) { callback.accept(null); }
    }

    private static List<ScreenshotOcr.Line> shift(List<ScreenshotOcr.Line> lines, int offset) {
        List<ScreenshotOcr.Line> shifted = new ArrayList<>(lines.size());
        for (ScreenshotOcr.Line line : lines) {
            shifted.add(new ScreenshotOcr.Line(new ScreenshotOcr.Box(line.box.left,
                    line.box.top + offset, line.box.right, line.box.bottom + offset),
                    line.text, line.confidence));
        }
        return shifted;
    }

    private byte[] asset(String name) throws java.io.IOException {
        try (InputStream input = context.getAssets().open("ocr/" + name);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[65536]; int n;
            while ((n = input.read(chunk)) != -1) bytes.write(chunk, 0, n);
            return bytes.toByteArray();
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> {
            if (engine != null) try { engine.close(); } catch (Exception ignored) { }
        });
        worker.shutdown();
    }
}
