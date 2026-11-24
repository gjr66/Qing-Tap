package local.qingtap.adskip;

import java.util.Arrays;
import java.util.List;

public final class MiniProgramVisionTest {
    private static ScreenshotOcr.Line line(int l, int t, int r, int b, String text, float confidence) {
        return new ScreenshotOcr.Line(new ScreenshotOcr.Box(l, t, r, b), text, confidence);
    }

    public static void main(String[] args) throws Exception {
        LightCubeCardTest.run();
        // Actual bundled-model OCR on the user's cover screenshot, resized to 760 x 1671.
        MiniProgramVision.Target actual = MiniProgramVision.inspect(Arrays.asList(
                line(599,197,715,233,"4秒|关闭",.9659f),
                line(136,313,206,348,"广告",.9999f)),760,1671);
        check(actual != null && Math.abs(actual.x-.90263f) < .001f, "actual screenshot OCR output");
        check(MiniProgramVision.inspect(Arrays.asList(
                line(285,609,301,623,"12",.9949f),
                line(280,651,308,668,"08",.9971f)),760,1671) == null, "actual normal-page OCR output");

        ScreenshotOcr.Line badge = line(160,365,230,395,"广告⌄",.97f);
        ScreenshotOcr.Line close = line(690,230,850,260,"4秒 | 关闭",.96f);
        MiniProgramVision.Target first = MiniProgramVision.inspect(Arrays.asList(badge, close), 874, 1920);
        check(first != null, "screenshot cover ad layout");
        MiniProgramVision.Target next = MiniProgramVision.inspect(Arrays.asList(badge,
                line(690,230,850,260,"3秒丨关闭",.96f)),874,1920);
        check(first.near(next), "countdown tick keeps same live OCR target");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,230,735,260,"4秒",.96f),
                line(760,230,850,260,"关闭",.96f)),874,1920) != null,"split OCR close/counter");
        ScreenshotOcr.Line explicit = line(710,315,845,350,"跳过广告",.96f);
        check(MiniProgramVision.inspect(Arrays.asList(badge, explicit),874,1920) != null,
                "explicit ad dismissal below the capsule");
        check(MiniProgramVision.inspect(Arrays.asList(explicit),874,1920) == null,
                "explicit OCR text still requires a separate ad badge");
        check(MiniProgramVision.inspect(Arrays.asList(close),874,1920) == null,"independent badge required");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,230,850,260,"关闭",.99f)),874,1920) != null,
                "a bare close needs a badge and corner position, then is actionable");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(390,430,510,460,"关闭",.99f)),874,1920) == null,
                "a centre close is never treated as an ad dismissal");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,120,850,180,"4秒关闭",.99f)),874,1920) == null,"capsule band");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,230,850,260,"4秒关闭",.75f)),874,1920) == null,"low confidence");
        check(MiniProgramVision.inspect(Arrays.asList(badge,close,line(20,400,400,440,"确认支付",.99f)),874,1920) == null,"payment screen");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,230,850,260,"关闭小程序",.99f)),874,1920) == null,
                "capsule close label never becomes an ad target");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(690,230,850,260,"×",.95f)),874,1920) != null,
                "an icon close is accepted only with a badge and corner position");
        check(MiniProgramVision.inspect(Arrays.asList(badge,line(390,430,440,470,"×",.95f)),874,1920) == null,
                "a centre icon is refused");

        List<ScreenshotOcr.Line> locker = Arrays.asList(line(150,370,720,440,"扫描取件小程序码",.99f),
                line(140,1000,730,1115,"去扫码取件",.99f),line(150,1170,730,1270,"去领红包",.99f),
                line(150,1700,230,1740,"广告",.99f));
        check(MiniProgramVision.inspect(locker,874,1920) == null,
                "normal locker page embedded ad must not trigger cover close");
        List<ScreenshotOcr.Line> operatorLogin = Arrays.asList(
                line(150,1380,230,1420,"广告⌄",.96f),
                line(330,1410,540,1450,"中国移动",.95f),
                line(280,1490,690,1550,"手机号登入",.97f),
                line(340,1600,600,1660,"其他方式",.96f),
                line(150,1760,500,1800,"运营商会员办理",.95f));
        check(MiniProgramVision.inspect(operatorLogin,874,1920) == null,
                "operator login advertisement is protected even with an ad badge");

        // Alipay interstitials: badge at the top, explicit close at the bottom edge. The top pass only
        // finds the badge; the bottom strip pass receives the shifted lines.
        List<ScreenshotOcr.Line> top = Arrays.asList(badge, line(136,313,206,348,"广告",.99f));
        check(MiniProgramVision.inspect(top,874,1920) == null, "top pass alone has no close");
        check(MiniProgramVision.hasBadge(top,874,1920), "top pass proves a badge before bottom OCR");
        List<ScreenshotOcr.Line> bottom = Arrays.asList(
                line(690,1680,850,1720,"关闭广告",.95f));
        check(MiniProgramVision.inspectBottom(bottom,874,1920) != null,
                "bottom explicit close under a top badge");
        check(MiniProgramVision.inspectBottom(Arrays.asList(line(370,1680,500,1720,"关闭广告",.95f)),874,1920) != null,
                "bottom centre explicit close is accepted by the bottom pass");
        check(MiniProgramVision.inspectBottom(Arrays.asList(line(390,430,510,460,"关闭",.95f)),874,1920) == null,
                "bottom pass still refuses a middle control");

        check(MiniProgramVision.inspect(Arrays.asList(
                line(150,240,450,320,"开",.99f),
                line(470,300,500,330,"1",.99f),
                line(130,850,330,950,"扫一扫",.99f)),874,1920) == null,
                "U净 red-packet cards carry no ad badge and are not guessed at");
        check(!MiniAdText.countdownClose("评论4") && !MiniAdText.countdownClose("关闭小程序4")
                && !MiniAdText.countdownClose("99秒关闭"),"unrelated labels");
        check(MiniAdText.explicitClose("关闭广告") && MiniAdText.explicitClose("关闭此广告")
                && MiniAdText.explicitClose("点击跳过"),"explicit ad wording");
        MiniProgramVision.Target five = new MiniProgramVision.Target(.90f,.13f,.86f,"5秒｜关闭",5);
        MiniProgramVision.Target four = new MiniProgramVision.Target(.90f,.13f,.86f,"4秒｜关闭",4);
        MiniProgramVision.Target zero = new MiniProgramVision.Target(.90f,.13f,.86f,"0秒｜关闭",0);
        check(four.confirms(five) && !five.confirms(four) && !five.confirms(five),
                "locker needs a falling timer");
        check(!zero.confirms(four) && !four.confirms(first) && !first.confirms(four)
                && !four.confirms(new MiniProgramVision.Target(.4f,.2f,.4f,"5秒｜关闭",5)),
                "no expired, mixed-source or relocated locker taps");
        System.out.println("PASS mini cover OCR evidence, countdown, capsule band, bottom interstitials, operator-login safeguard and locker/U净 pages");
    }

    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
