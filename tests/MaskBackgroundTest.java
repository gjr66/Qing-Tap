package local.qingtap.adskip;
import java.util.Arrays;
public final class MaskBackgroundTest {
    public static void main(String[] args) {
        int w=400,h=800; int[] pixels=new int[w*h];
        AdRules.Box ad=new AdRules.Box(40,200,360,400);
        Arrays.fill(pixels,0xfff7f7f7);
        for(int y=200;y<400;y++)for(int x=40;x<360;x++)pixels[y*w+x]=0xffff0000;
        MaskBackground.Patch patch=MaskBackground.sample(pixels,w,h,ad,Arrays.asList(ad),0xff000000);
        check(patch.near(MaskBackground.Patch.solid(0xfff7f7f7)),"ad pixels never used for background");
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff000000|((70+y/20)<<16)|((140+x/20)<<8)|240;
        patch=MaskBackground.sample(pixels,w,h,ad,Arrays.asList(ad),0xff000000);
        check((patch.bottomLeft>>16&255)>(patch.topLeft>>16&255),"vertical gradient");
        check((patch.topRight>>8&255)>(patch.topLeft>>8&255),"horizontal gradient");
        AdRules.Box whole=new AdRules.Box(0,0,w,h);
        patch=MaskBackground.sample(pixels,w,h,ad,Arrays.asList(whole),0xff123456);
        check(patch.near(MaskBackground.Patch.solid(0xff123456)),"no safe sample uses fallback");
        patch=MaskBackground.sample(null,w,h,ad,Arrays.asList(ad),0xff123456);
        check(patch.near(MaskBackground.Patch.solid(0xff123456)),"invalid capture fallback");
        System.out.println("PASS quiet mask background edge sampling, gradients and ad pixel exclusion");
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
