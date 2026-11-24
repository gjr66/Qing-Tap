package local.qingtap.adskip;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class DnsPacketTest {
    public static void main(String[] args) {
        byte[] wire = request("MI.GDT.QQ.COM", 1);
        DnsPacket packet = DnsPacket.read(wire, wire.length);
        check(packet != null && DnsPacket.blocked(packet.name), "Tencent ad query");
        check(DnsPacket.blocked("adx.tanx.com"), "Tanx suffix");
        for (String host : Arrays.asList("weixin.qq.com", "alipay.com", "gw.alipayobjects.com",
                "notanx.com", "tanx.com.example", "mi.gdt.qq.com.example")) {
            check(!DnsPacket.blocked(host), host + " must pass");
        }

        byte[] blocked = packet.toUdp(packet.error(3));
        check((blocked[31] & 15) == 3 && DnsPacket.u16(blocked, 28) == 0xbb17, "NXDOMAIN preserves ID");
        check(DnsPacket.u16(blocked, 20) == 53 && DnsPacket.u16(blocked, 22) == 49000, "reverse UDP route");
        check(blocked[15] == 2 && blocked[19] == 1, "reverse IP addresses");
        check(DnsPacket.u16(blocked, 2) == blocked.length, "packet length");
        int sum = 0;
        for (int at = 0; at < 20; at += 2) sum += DnsPacket.u16(blocked, at);
        check(((sum & 65535) + (sum >>> 16)) == 65535, "IPv4 checksum");

        byte[] upstream = packet.error(0);
        DnsPacket.put16(upstream, 0, 0xabcd);
        byte[] resolved = packet.response(upstream);
        check((resolved[3] & 15) == 0 && DnsPacket.u16(resolved, 0) == 0xbb17, "resolver ID adaptation");
        upstream[13] = 'X';
        check((packet.response(upstream)[3] & 15) == 2, "wrong question is rejected");
        check((packet.response(null)[3] & 15) == 2, "upstream failure produces SERVFAIL");

        check(DnsPacket.read(wire, wire.length - 1) == null, "truncated IP packet");
        wire[16] = 8;
        check(DnsPacket.read(wire, wire.length) == null, "non-DNS route");
        wire = request("example.org", 28);
        wire[7] = 1;
        check(DnsPacket.read(wire, wire.length) == null, "fragment");
        wire[7] = 0;
        wire[40] = (byte) 0xc0;
        check(DnsPacket.read(wire, wire.length) == null, "compressed query rejected safely");
        System.out.println("PASS DNS scope, filtering, response framing, and malformed input");
    }

    private static byte[] request(String domain, int type) {
        byte[] wire = new byte[28 + 12 + domain.length() + 2 + 4];
        wire[0] = 0x45;
        wire[9] = 17;
        wire[12] = wire[16] = (byte) 192;
        wire[14] = wire[18] = 2;
        wire[15] = 1;
        wire[19] = 2;
        DnsPacket.put16(wire, 2, wire.length);
        DnsPacket.put16(wire, 20, 49000);
        DnsPacket.put16(wire, 22, 53);
        DnsPacket.put16(wire, 24, wire.length - 20);
        DnsPacket.put16(wire, 28, 0xbb17);
        wire[30] = 1;
        wire[33] = 1;
        int at = 40;
        for (String label : domain.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            wire[at++] = (byte) bytes.length;
            System.arraycopy(bytes, 0, wire, at, bytes.length);
            at += bytes.length;
        }
        DnsPacket.put16(wire, at + 1, type);
        DnsPacket.put16(wire, at + 3, 1);
        return wire;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
