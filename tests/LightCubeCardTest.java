package local.qingtap.adskip;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;

final class LightCubeCardTest {
    static void run() throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path root = Paths.get(LightCubeCardTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        while (root != null && !Files.isDirectory(root.resolve("app/src/main/assets/lightcube")))
            root = root.getParent();
        if (root == null) throw new AssertionError("project samples unavailable");
        Path assets = root.resolve("app/src/main/assets/lightcube");
        LightCubeModal modal = new LightCubeModal(read(assets.resolve("modal-badge.png"),52).pixels,
                read(assets.resolve("modal-tab.png"),26).pixels);
        ScreenshotFrame popup = read(root.resolve("samples/lightcube_modal_controls.png"),760);
        MiniProgramVision.Target close = modal.locate(popup);
        if (close == null || !close.lockerModal || Math.abs(close.x-964/1080f)>.008
                || Math.abs(close.y-618/2376f)>.006) throw new AssertionError("dismiss only the product modal X");
        for (int y=490; y<540; y++) Arrays.fill(popup.pixels,y*760+62,y*760+148,0xff909090);
        if (modal.locate(popup)!=null) throw new AssertionError("unmarked popups stay open");
        ScreenshotFrame badge = read(assets.resolve("ad-badge.png"), 42);
        LightCubeCard detector = new LightCubeCard(read(assets.resolve("pickup.png"), 84).pixels, badge.pixels);
        ScreenshotFrame f = read(root.resolve("samples/locker_page.jpg"), 760);
        if (detector.locate(f) != null) throw new AssertionError("must preserve operator login");
        if (detector.locate(read(root.resolve("samples/lightcube_cover.jpg"), 760)) != null)
            throw new AssertionError("must preserve splash handling");
        // Replace the login panel in the recorded pickup layout with a known card fixture.
        for (int y=1160; y<1545; y++) Arrays.fill(f.pixels,y*760+40,y*760+720,0xff345153);
        for (int y=0; y<23; y++) for (int x=0; x<42; x++) {
            int v=badge.pixels[y*42+x]&255;
            int c=95+v*160/255;
            f.pixels[(1186+y)*760+69+x]=0xff000000 | c<<16 | c<<8 | c;
        }
        AdRules.Box slot=detector.locate(f);
        if (slot==null || slot.left>40 || slot.right<720 || slot.top>1160 || slot.top<1110
                || slot.bottom<1545 || slot.bottom>1554)
            throw new AssertionError("cover entire card between controls and tabs");
        for(int y=slot.top;y<slot.bottom;y++) Arrays.fill(f.pixels,y*760+slot.left,y*760+slot.right,0xffffffff);
        if(detector.locate(f)!=null) throw new AssertionError("blank card cannot renew mask");
    }

    private static ScreenshotFrame read(Path path, int width) throws Exception {
        BufferedImage input=ImageIO.read(path.toFile());
        int height=Math.round(input.getHeight()*width/(float)input.getWidth());
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(input,0,0,width,height,null);
        g.dispose();
        return new ScreenshotFrame(width,height,image.getRGB(0,0,width,height,null,0,width));
    }
}
