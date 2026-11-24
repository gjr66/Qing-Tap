package local.qingtap.adskip;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Quiet, touch-consuming covers: no labels, border or shadow. Keep the same windows and blend
// surrounding background colours. A normal tap does nothing; long-press restores the slot.
final class AdMaskController {
    private final AccessibilityService service;
    private final WindowManager manager;
    private final Map<String, View> views = new LinkedHashMap<>();
    private final Map<String, CampusAdRules.Region> slots = new LinkedHashMap<>();
    private final Map<String, MaskBackground.Patch> patches = new LinkedHashMap<>();
    private final Set<String> restored = new HashSet<>();
    private final MaskRetention retention = new MaskRetention();
    private String pkg = "";
    private int window = -1;
    private long revision;

    AdMaskController(AccessibilityService service) {
        this.service = service;
        manager = service.getSystemService(WindowManager.class);
    }

    boolean visible() { return !views.isEmpty(); }
    long revision() { return revision; }
    Set<String> activeIds() {
        Set<String> result = new HashSet<>();
        for (CampusAdRules.Region region : slots.values()) result.add(region.id);
        return result;
    }

    void update(String pkg, int window, List<CampusAdRules.Region> regions, Set<String> checkedIds) {
        if (!this.pkg.equals(pkg) || this.window != window) {
            reset();
            this.pkg = pkg;
            this.window = window;
        }
        long now = SystemClock.uptimeMillis();
        Set<String> keep = new HashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CampusAdRules.Region region : regions) {
            if (restored.contains(region.id)) continue;
            retention.observe(region.id, now);
            int count = counts.containsKey(region.id) ? counts.get(region.id) + 1 : 0;
            counts.put(region.id, count);
            String key = region.id + "/" + count;
            keep.add(key);
            CampusAdRules.Region previous = slots.get(key);
            if (previous != null && same(previous.box, region.box)) continue;
            AdRules.Box box = region.box;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(box.width(), box.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = box.left;
            params.y = box.top;
            params.setTitle("清弹");
            if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
            View view = views.get(key);
            boolean fresh = view == null;
            if (fresh) {
                view = new View(service);
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                view.setContentDescription("长按临时显示此区域");
                view.setClickable(true);
                view.setOnClickListener(clicked -> { });
                view.setOnLongClickListener(clicked -> {
                    restored.add(region.id);
                    for (String other : new HashSet<>(slots.keySet())) {
                        if (slots.get(other).id.equals(region.id)) remove(other);
                    }
                    return true;
                });
                view.setBackground(new PatchDrawable(patches.containsKey(region.id) ? patches.get(region.id)
                        : MaskBackground.Patch.solid(fallback(region.id))));
            }
            try {
                if (fresh) manager.addView(view, params);
                else manager.updateViewLayout(view, params);
                views.put(key, view);
                slots.put(key, region);
                revision++;
            } catch (RuntimeException unavailable) { remove(key); }
        }
        for (String key : new HashSet<>(slots.keySet())) {
            CampusAdRules.Region slot = slots.get(key);
            if (!keep.contains(key) && !retention.keep(slot.id, checkedIds.contains(slot.id), now)) remove(key);
        }
    }

    void colourFromFrame(int[] pixels, int width, int height, int displayWidth, int displayHeight) {
        if (displayWidth <= 0 || displayHeight <= 0) return;
        List<AdRules.Box> excluded = new ArrayList<>();
        for (CampusAdRules.Region region : slots.values()) excluded.add(scale(region.box, width, height, displayWidth, displayHeight));
        for (String key : new ArrayList<>(slots.keySet())) {
            CampusAdRules.Region region = slots.get(key);
            MaskBackground.Patch patch = MaskBackground.sample(pixels, width, height,
                    scale(region.box, width, height, displayWidth, displayHeight), excluded, fallback(region.id));
            MaskBackground.Patch before = patches.get(region.id);
            if (patch.near(before)) continue;
            patches.put(region.id, patch);
            View view = views.get(key);
            if (view != null) view.setBackground(new PatchDrawable(patch));
        }
    }

    void clear() { for (String key : new HashSet<>(views.keySet())) remove(key); }
    void reset() {
        clear(); restored.clear(); retention.clear(); patches.clear(); pkg = ""; window = -1;
    }

    private void remove(String key) {
        View view = views.remove(key);
        slots.remove(key);
        if (view != null) revision++;
        if (view != null) try { manager.removeViewImmediate(view); } catch (RuntimeException ignored) { }
    }

    private static int fallback(String id) {
        if (LightCubeCard.ID.equals(id)) return 0xffffffff;
        return id.contains("home_title_") || id.contains("home_hz_") ? 0xff62adfb : 0xfff7f7f7;
    }
    private static AdRules.Box scale(AdRules.Box b, int w, int h, int dw, int dh) {
        return new AdRules.Box(b.left * w / dw, b.top * h / dh, b.right * w / dw, b.bottom * h / dh);
    }
    private static boolean same(AdRules.Box a, AdRules.Box b) {
        return a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }
    private static final class PatchDrawable extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        PatchDrawable(MaskBackground.Patch patch) {
            bitmap = Bitmap.createBitmap(new int[]{patch.topLeft, patch.topRight, patch.bottomLeft, patch.bottomRight},
                    2, 2, Bitmap.Config.ARGB_8888);
        }
        @Override public void draw(Canvas canvas) { canvas.drawBitmap(bitmap, new Rect(0, 0, 2, 2), getBounds(), paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
