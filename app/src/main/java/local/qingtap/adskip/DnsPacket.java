package local.qingtap.adskip;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

// The VPN routes only the virtual resolver address. This class handles its IPv4 UDP DNS packets.
final class DnsPacket {
    static final String CLIENT = "192.0.2.1";
    static final String RESOLVER = "192.0.2.2";
    static final int MTU = 1500;
    private static final int CLIENT_IP = 0xc0000201;
    private static final int RESOLVER_IP = 0xc0000202;
    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
            "mi.gdt.qq.com", "t.gdt.qq.com", "pgdt.gtimg.cn", "sdk.e.qq.com"));

    final int port;
    final byte[] query;
    final String name;
    private final int questionEnd;

    private DnsPacket(int port, byte[] query, String name, int questionEnd) {
        this.port = port;
        this.query = query;
        this.name = name;
        this.questionEnd = questionEnd;
    }

    static boolean blocked(String name) {
        String host = name.toLowerCase(Locale.ROOT);
        return AD_HOSTS.contains(host) || host.equals("tanx.com") || host.endsWith(".tanx.com");
    }

    static DnsPacket read(byte[] packet, int size) {
        if (size < 40 || (packet[0] & 0xf0) != 0x40 || packet[9] != 17
                || (u16(packet, 6) & 0xbfff) != 0
                || ip(packet, 12) != CLIENT_IP || ip(packet, 16) != RESOLVER_IP) return null;
        int header = (packet[0] & 15) * 4;
        int length = u16(packet, 2);
        if (header < 20 || length > size || length < header + 20
                || u16(packet, header + 2) != 53 || u16(packet, header + 4) != length - header) return null;
        byte[] query = Arrays.copyOfRange(packet, header + 8, length);
        if ((query[2] & 0xf8) != 0 || u16(query, 4) != 1
                || u16(query, 6) != 0 || u16(query, 8) != 0) return null;
        Question question = question(query);
        if (question == null) return null;
        return new DnsPacket(u16(packet, header), query, question.name, question.end);
    }

    // Leave the question and request's RD bit intact. An empty NXDOMAIN answer has no cache TTL.
    byte[] error(int rcode) {
        byte[] reply = Arrays.copyOf(query, questionEnd);
        reply[2] = (byte) (0x80 | (query[2] & 1));
        reply[3] = (byte) (0x80 | rcode);
        Arrays.fill(reply, 6, 12, (byte) 0);
        return reply;
    }

    byte[] response(byte[] answer) {
        if (answer == null || answer.length < 12 || answer.length > MTU - 28
                || (answer[2] & 0xfa) != 0x80 || u16(answer, 4) != 1) return error(2);
        Question reply = question(answer);
        if (reply == null || !name.equalsIgnoreCase(reply.name)
                || u16(query, questionEnd - 4) != u16(answer, reply.end - 4)
                || u16(query, questionEnd - 2) != u16(answer, reply.end - 2)) return error(2);
        byte[] result = answer.clone();
        // Android's resolver may assign its own transaction ID to the upstream query.
        result[0] = query[0];
        result[1] = query[1];
        return result;
    }

    byte[] toUdp(byte[] answer) {
        byte[] out = new byte[28 + answer.length];
        out[0] = 0x45;
        put16(out, 2, out.length);
        out[8] = 64;
        out[9] = 17;
        putIp(out, 12, RESOLVER_IP);
        putIp(out, 16, CLIENT_IP);
        put16(out, 10, checksum(out));
        put16(out, 20, 53);
        put16(out, 22, port);
        put16(out, 24, out.length - 20);
        // A zero UDP checksum is valid for IPv4.
        System.arraycopy(answer, 0, out, 28, answer.length);
        return out;
    }

    private static Question question(byte[] dns) {
        StringBuilder name = new StringBuilder();
        int at = 12;
        while (at < dns.length && at < 267) {
            int count = dns[at++] & 255;
            if (count == 0) return at + 4 <= dns.length ? new Question(name.toString(), at + 4) : null;
            if (count > 63 || at + count >= dns.length || at + count >= 267) return null;
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < count; i++) {
                int ch = dns[at++] & 255;
                // Refuse embedded separators instead of mistaking a single DNS label for a domain suffix.
                if (ch <= 32 || ch >= 127 || ch == '.') return null;
                name.append((char) ch);
            }
        }
        return null;
    }

    private static final class Question {
        final String name;
        final int end;

        Question(String name, int end) {
            this.name = name;
            this.end = end;
        }
    }

    private static int ip(byte[] bytes, int at) {
        return u16(bytes, at) << 16 | u16(bytes, at + 2);
    }

    private static void putIp(byte[] bytes, int at, int value) {
        put16(bytes, at, value >>> 16);
        put16(bytes, at + 2, value);
    }

    static int u16(byte[] bytes, int at) {
        return (bytes[at] & 255) << 8 | bytes[at + 1] & 255;
    }

    static void put16(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    private static int checksum(byte[] bytes) {
        int sum = 0;
        for (int at = 0; at < 20; at += 2) sum += u16(bytes, at);
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return ~sum & 65535;
    }
}
