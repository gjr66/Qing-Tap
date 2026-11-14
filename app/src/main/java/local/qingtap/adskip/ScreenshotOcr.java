package local.qingtap.adskip;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// PP-OCR detection, BGR preprocessing and CTC decoding use the same models as 广告观察.
final class ScreenshotOcr implements AutoCloseable {
    static final class Box {
        final int left, top, right, bottom;

        Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() { return right - left; }
        int height() { return bottom - top; }
        float x() { return (left + right) / 2f; }
        float y() { return (top + bottom) / 2f; }
    }

    static final class Line {
        final Box box;
        final String text;
        final float confidence;

        Line(Box box, String text, float confidence) {
            this.box = box;
            this.text = text;
            this.confidence = confidence;
        }
    }

    private static final float[] MEAN = {.485f, .456f, .406f};
    private static final float[] STD = {.229f, .224f, .225f};
    private final OrtEnvironment environment;
    private final OrtSession detector;
    private final OrtSession recognizer;
    private final String detectorInput;
    private final String recognizerInput;
    private final List<String> dictionary;

    ScreenshotOcr(byte[] det, byte[] rec, InputStream words) throws IOException, OrtException {
        dictionary = new ArrayList<>();
        dictionary.add("");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(words, StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) dictionary.add(word);
        }
        dictionary.add(" ");
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            detector = environment.createSession(det, options);
            try {
                recognizer = environment.createSession(rec, options);
            } catch (OrtException | RuntimeException failure) {
                detector.close();
                throw failure;
            }
        }
        detectorInput = detector.getInputNames().iterator().next();
        recognizerInput = recognizer.getInputNames().iterator().next();
    }

    List<Line> read(int[] pixels, int width, int height) throws OrtException {
        float scale = Math.min(1, 1728f / Math.max(width, height));
        int w = Math.max(32, Math.round(width * scale / 32) * 32);
        int h = Math.max(32, Math.round(height * scale / 32) * 32);
        List<Box> boxes;
        Box frame = new Box(0, 0, width, height);
        try (OnnxTensor input = tensor(pixels, width, frame, w, h, true);
             OrtSession.Result result = detector.run(Collections.singletonMap(detectorInput, input))) {
            OnnxTensor output = (OnnxTensor) result.get(0);
            long[] shape = output.getInfo().getShape();
            int mapW = (int) shape[shape.length - 1], mapH = (int) shape[shape.length - 2];
            boxes = boxes(output.getFloatBuffer(), mapW, mapH, width, height);
        }
        List<Line> lines = new ArrayList<>();
        for (Box box : boxes) {
            // Inspect chrome and compact text that could be a close control in a popup.
            if (box.y() > height * .22 && box.y() < height * .70
                    && (box.width() > width * .29 || box.height() > height * .09)) continue;
            Line line = recognize(pixels, width, box);
            if (line != null) lines.add(line);
        }
        return lines;
    }

    private Line recognize(int[] pixels, int width, Box box) throws OrtException {
        int w = Math.max(16, Math.min(960, (int) Math.ceil(6.0 * box.width() / box.height()) * 8));
        try (OnnxTensor input = tensor(pixels, width, box, w, 48, false);
             OrtSession.Result result = recognizer.run(Collections.singletonMap(recognizerInput, input))) {
            OnnxTensor output = (OnnxTensor) result.get(0);
            long[] shape = output.getInfo().getShape();
            int steps = (int) shape[1], classes = (int) shape[2];
            if (classes != dictionary.size()) throw new IllegalStateException("OCR dictionary mismatch");
            FloatBuffer scores = output.getFloatBuffer();
            StringBuilder text = new StringBuilder();
            int previous = -1, count = 0;
            float confidence = 0;
            for (int step = 0; step < steps; step++) {
                float maximum = scores.get();
                int best = 0;
                for (int c = 1; c < classes; c++) {
                    float score = scores.get();
                    if (score > maximum) {
                        maximum = score;
                        best = c;
                    }
                }
                if (best != 0 && best != previous) {
                    text.append(dictionary.get(best));
                    confidence += maximum;
                    count++;
                }
                previous = best;
            }
            return count == 0 || confidence / count < .60f ? null
                    : new Line(box, text.toString(), confidence / count);
        }
    }

    private OnnxTensor tensor(int[] pixels, int width, Box box, int w, int h, boolean detection)
            throws OrtException {
        int area = w * h;
        float[] buffer = new float[3 * area];
        for (int y = 0; y < h; y++) {
            float sy = Math.max(box.top, Math.min(box.bottom - 1, box.top + (y + .5f) * box.height() / h - .5f));
            int top = (int) sy, bottom = Math.min(box.bottom - 1, top + 1);
            float dy = sy - top;
            for (int x = 0; x < w; x++) {
                float sx = Math.max(box.left, Math.min(box.right - 1, box.left + (x + .5f) * box.width() / w - .5f));
                int left = (int) sx, right = Math.min(box.right - 1, left + 1);
                float dx = sx - left;
                int a = pixels[top * width + left], b = pixels[top * width + right];
                int c = pixels[bottom * width + left], d = pixels[bottom * width + right];
                for (int channel = 0; channel < 3; channel++) {
                    int shift = channel * 8;
                    float upper = ((a >> shift & 255) * (1 - dx) + (b >> shift & 255) * dx);
                    float lower = ((c >> shift & 255) * (1 - dx) + (d >> shift & 255) * dx);
                    float value = (upper * (1 - dy) + lower * dy) / 255f;
                    buffer[channel * area + y * w + x] = detection
                            ? (value - MEAN[channel]) / STD[channel] : (value - .5f) * 2;
                }
            }
        }
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(buffer), new long[]{1, 3, h, w});
    }

    private static List<Box> boxes(FloatBuffer map, int w, int h, int width, int height) {
        byte[] mask = new byte[w * h];
        for (int i = 0; i < mask.length; i++) {
            if (map.get(i) <= .30f) continue;
            mask[i] = 1;
            if (i % w + 1 < w) mask[i + 1] = 1;
            if (i + w < mask.length) {
                mask[i + w] = 1;
                if (i % w + 1 < w) mask[i + w + 1] = 1;
            }
        }
        List<Box> boxes = new ArrayList<>();
        int[] queue = new int[mask.length];
        for (int seed = 0; seed < mask.length && boxes.size() < 120; seed++) {
            if (mask[seed] != 1) continue;
            mask[seed] = 2;
            queue[0] = seed;
            int count = 1, left = w, right = 0, top = h, bottom = 0;
            float sum = 0;
            for (int head = 0; head < count; head++) {
                int i = queue[head], x = i % w, y = i / w;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                sum += map.get(i);
                if (x > 0) count = push(mask, queue, count, i - 1);
                if (x + 1 < w) count = push(mask, queue, count, i + 1);
                if (y > 0) count = push(mask, queue, count, i - w);
                if (y + 1 < h) count = push(mask, queue, count, i + w);
            }
            int bw = right - left + 1, bh = bottom - top + 1;
            if (count < 7 || sum / count < .48f || bw < 3 || bh < 3 || bh > bw * 2.6) continue;
            double pad = bw * bh * 1.6 / (2 * (bw + bh));
            Box box = new Box(Math.max(0, (int) Math.floor((left - pad) * width / w)),
                    Math.max(0, (int) Math.floor((top - pad) * height / h)),
                    Math.min(width, (int) Math.ceil((right + pad + 1) * width / w)),
                    Math.min(height, (int) Math.ceil((bottom + pad + 1) * height / h)));
            if (box.width() >= 5 && box.height() >= 5) boxes.add(box);
        }
        return boxes;
    }

    private static int push(byte[] mask, int[] queue, int count, int next) {
        if (mask[next] != 1) return count;
        mask[next] = 2;
        queue[count] = next;
        return count + 1;
    }

    @Override public void close() throws OrtException {
        try {
            recognizer.close();
        } finally {
            detector.close();
        }
    }
}
