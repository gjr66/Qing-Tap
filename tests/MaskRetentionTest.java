package local.qingtap.adskip;
public final class MaskRetentionTest {
    public static void main(String[] args) {
        MaskRetention retention=new MaskRetention();
        check(!retention.keep("ad",false,0),"never invent a cover");
        retention.observe("ad",100);
        check(retention.keep("ad",false,800),"partial read gets bounded retry");
        check(!retention.keep("ad",true,101),"confirmed absence clears immediately");
        check(!retention.keep("ad",false,1001),"unavailable anchor cannot leave stale cover");
        retention.clear(); check(!retention.keep("ad",false,101),"window change resets retention");
        System.out.println("PASS mask partial-read retention and immediate verified removal");
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
