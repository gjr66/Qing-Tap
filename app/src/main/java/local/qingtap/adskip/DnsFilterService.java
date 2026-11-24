package local.qingtap.adskip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DnsResolver;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class DnsFilterService extends VpnService {
    static final String WECHAT = "dns_wechat";
    static final String ALIPAY = "dns_alipay";
    static final String STOP = "local.qingtap.adskip.STOP_DNS";
    private static final String CHANNEL = "local_dns_filter";
    private static final int NOTIFICATION = 5301;

    static volatile String status = "已停止";
    private static volatile Session running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Session session;

    static boolean active() { return running != null; }
    static String summary() {
        Session current = running;
        if (current == null) return status;
        return "运行中 · " + current.apps + " · 已拦截 " + current.blocked.get()
                + " 次 DNS 查询 · 转发成功 " + current.forwarded.get() + " 次"
                + (current.errors.get() == 0 ? "" : " · 解析失败 " + current.errors.get() + " 次");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            end("已停止");
            return START_NOT_STICKY;
        }
        if (session != null) return START_NOT_STICKY;
        try {
            startForeground(NOTIFICATION, notification());
            if (Build.VERSION.SDK_INT < 29 || prepare(this) != null) {
                end("未获得 VPN 权限或系统不支持");
                return START_NOT_STICKY;
            }
            Builder builder = new Builder().setSession("清弹 DNS 过滤").setMtu(DnsPacket.MTU)
                    .addAddress(DnsPacket.CLIENT, 32).addDnsServer(DnsPacket.RESOLVER)
                    .addRoute(DnsPacket.RESOLVER, 32).allowFamily(OsConstants.AF_INET6);
            boolean wx = intent.getBooleanExtra(WECHAT, false)
                    && include(builder, MiniProgramRules.WECHAT);
            boolean ali = intent.getBooleanExtra(ALIPAY, false)
                    && include(builder, MiniProgramRules.ALIPAY);
            if (!wx && !ali) {
                end("没有选中已安装的应用");
                return START_NOT_STICKY;
            }
            ParcelFileDescriptor descriptor = builder.establish();
            if (descriptor == null) {
                end("VPN 未建立，请重新授权");
                return START_NOT_STICKY;
            }
            session = new Session(descriptor, wx && ali ? "微信、支付宝" : wx ? "微信" : "支付宝");
            running = session;
            Session started = session;
            new Thread(() -> read(started), "QingTap DNS").start();
        } catch (RuntimeException unavailable) {
            end("VPN 启动失败");
        }
        return START_NOT_STICKY;
    }

    private boolean include(Builder builder, String pkg) {
        try {
            builder.addAllowedApplication(pkg);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    private Notification notification() {
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "清弹 DNS 过滤", NotificationManager.IMPORTANCE_LOW));
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, DnsFilterService.class).setAction(STOP), flags);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_adskip)
                .setContentTitle("清弹 DNS 过滤运行中").setContentText("仅处理所选应用的域名查询")
                .setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "关闭过滤", stop).build()).build();
    }

    private static final class Session {
        final ParcelFileDescriptor fd;
        final String apps;
        final Semaphore slots = new Semaphore(24);
        final Map<CancellationSignal, Runnable> pending = new HashMap<>();
        final AtomicInteger blocked = new AtomicInteger();
        final AtomicInteger forwarded = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        volatile boolean stopped;

        Session(ParcelFileDescriptor fd, String apps) {
            this.fd = fd;
            this.apps = apps;
        }

        synchronized void send(DnsPacket packet, byte[] answer) {
            if (stopped) return;
            byte[] bytes = packet.toUdp(answer);
            try {
                Os.write(fd.getFileDescriptor(), bytes, 0, bytes.length);
            } catch (ErrnoException | IOException unavailable) {
                // Drop a packet if the TUN buffer is full. Android can retry the DNS query.
            }
        }

        synchronized void close() {
            stopped = true;
            try { fd.close(); } catch (IOException ignored) { }
        }
    }

    private void read(Session current) {
        byte[] buffer = new byte[DnsPacket.MTU];
        StructPollfd ready = new StructPollfd();
        ready.fd = current.fd.getFileDescriptor();
        ready.events = (short) OsConstants.POLLIN;
        StructPollfd[] polling = { ready };
        try {
            while (!current.stopped) {
                if (Os.poll(polling, 250) == 0 || current.stopped) continue;
                if ((ready.revents & OsConstants.POLLIN) == 0) break;
                int count;
                try {
                    count = Os.read(ready.fd, buffer, 0, buffer.length);
                } catch (ErrnoException retry) {
                    if (retry.errno == OsConstants.EAGAIN || retry.errno == OsConstants.EINTR) continue;
                    throw retry;
                }
                if (count <= 0) break;
                DnsPacket packet = DnsPacket.read(buffer, count);
                if (packet == null) continue;
                if (DnsPacket.blocked(packet.name)) {
                    current.blocked.incrementAndGet();
                    current.send(packet, packet.error(3));
                } else if (current.slots.tryAcquire()) {
                    main.post(() -> resolve(current, packet));
                } else {
                    current.errors.incrementAndGet();
                    current.send(packet, packet.error(2));
                }
            }
        } catch (ErrnoException | IOException unavailable) {
            // The foreground handler closes the interface and restores normal routing.
        } finally {
            main.post(() -> { if (session == current) end("VPN 已断开"); });
        }
    }

    private void resolve(Session current, DnsPacket packet) {
        if (current.stopped) return;
        CancellationSignal signal = new CancellationSignal();
        Runnable timeout = () -> finish(current, packet, signal, null);
        current.pending.put(signal, timeout);
        main.postDelayed(timeout, 4500);
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                finish(current, packet, signal, null);
                return;
            }
            DnsResolver.getInstance().rawQuery(network, packet.query, DnsResolver.FLAG_NO_CACHE_STORE,
                    getMainExecutor(), signal, new DnsResolver.Callback<byte[]>() {
                        @Override public void onAnswer(byte[] answer, int rcode) {
                            finish(current, packet, signal, answer);
                        }
                        @Override public void onError(DnsResolver.DnsException error) {
                            finish(current, packet, signal, null);
                        }
                    });
        } catch (RuntimeException unavailable) {
            finish(current, packet, signal, null);
        }
    }

    private void finish(Session current, DnsPacket packet, CancellationSignal signal, byte[] answer) {
        Runnable timeout = current.pending.remove(signal);
        if (timeout == null) return;
        main.removeCallbacks(timeout);
        if (answer == null) signal.cancel();
        current.slots.release();
        byte[] reply = packet.response(answer);
        if ((reply[3] & 15) == 2) current.errors.incrementAndGet();
        else current.forwarded.incrementAndGet();
        current.send(packet, reply);
    }

    private void closeSession() {
        Session current = session;
        session = null;
        if (current == null) return;
        if (running == current) running = null;
        current.close();
        for (Map.Entry<CancellationSignal, Runnable> entry : current.pending.entrySet()) {
            main.removeCallbacks(entry.getValue());
            entry.getKey().cancel();
        }
        current.pending.clear();
    }

    private void end(String message) {
        status = message;
        closeSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onRevoke() {
        main.post(() -> end("VPN 权限已撤销"));
    }

    @Override public void onDestroy() {
        closeSession();
        super.onDestroy();
    }
}
