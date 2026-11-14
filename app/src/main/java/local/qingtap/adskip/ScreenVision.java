package local.qingtap.adskip;

// Bridge between the accessibility service (which owns the window, the viewport and the gesture tap)
// and the optional screen capture service (which owns the pixels). Requests are synchronous on the
// accessibility thread: one frame, one analysis, then a tap.
final class ScreenVision {
    interface Grabber {
        void grab(Sink sink);
    }

    interface Sink {
        void frame(int width, int height, int[] argb);
    }

    private static volatile Grabber grabber;

    private ScreenVision() {}

    static void attach(Grabber candidate) {
        grabber = candidate;
    }

    static void detach(Grabber candidate) {
        if (grabber == candidate) grabber = null;
    }

    static boolean available() {
        return grabber != null;
    }

    static void request(Sink sink) {
        Grabber current = grabber;
        if (current != null) current.grab(sink);
    }
}
