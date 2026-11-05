package com.guardiao.dados;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Bloqueio local: só os apps selecionados são roteados para a VPN e os pacotes
 * são descartados. NÃO é um proxy, não reencaminha tráfego, não espiona dados.
 * Apps não selecionados continuam fora desta VPN (salvo políticas do sistema).
 */
public class BlockVpnService extends VpnService {
    public static final String ACTION_START = "com.guardiao.dados.START";
    public static final String ACTION_STOP = "com.guardiao.dados.STOP";
    public static final String EXTRA_PACKAGES = "packages";
    private static final int NOTIFICATION_ID = 401;
    private static final String CHANNEL_ID = "guardiao_bloqueio";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicLong BYTES_DISCARDED = new AtomicLong(0);
    private static final AtomicInteger APPLIED_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SKIPPED_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SESSION_GENERATION = new AtomicInteger(0);
    private static final Set<String> APPLIED_PACKAGES = Collections.synchronizedSet(new HashSet<>());
    private static volatile String LAST_ERROR = "";

    private volatile boolean keepRunning = false;
    private ParcelFileDescriptor tunnel;
    private Thread readerThread;

    public static boolean isActive() { return ACTIVE.get(); }
    public static long bytesDiscarded() { return BYTES_DISCARDED.get(); }
    public static int getAppliedCount() { return APPLIED_COUNT.get(); }
    public static int getSkippedCount() { return SKIPPED_COUNT.get(); }
    public static String getLastError() { return LAST_ERROR; }
    public static Set<String> getAppliedPackages() {
        synchronized (APPLIED_PACKAGES) { return new HashSet<>(APPLIED_PACKAGES); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopBlocking();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        ArrayList<String> targets = intent.getStringArrayListExtra(EXTRA_PACKAGES);
        if (targets == null || targets.isEmpty() || VpnService.prepare(this) != null) {
            LAST_ERROR = "VPN sem autorização ou sem apps escolhidos";
            stopBlocking();
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = makeNotification(targets.size());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            LAST_ERROR = "Falha ao iniciar serviço: " + e.getClass().getSimpleName();
            Log.e("GuardiaoVpn", "Nao foi possivel iniciar em primeiro plano", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopBlocking(); // encerra uma sessão anterior, se existir
        LAST_ERROR = "";
        try {
            Builder builder = new Builder()
                .setSession("Guardião de Dados • bloqueio local")
                .setMtu(1500)
                .addAddress("10.253.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:253::2", 128)
                .addRoute("::", 0);
            int validCount = 0;
            for (String pkg : targets) {
                if (pkg == null || pkg.equals(getPackageName())) continue;
                try {
                    builder.addAllowedApplication(pkg);
                    validCount++;
                    APPLIED_PACKAGES.add(pkg);
                } catch (PackageManager.NameNotFoundException ex) {
                    SKIPPED_COUNT.incrementAndGet();
                    Log.w("GuardiaoVpn", "Aplicativo foi removido: " + pkg);
                }
            }
            APPLIED_COUNT.set(validCount);
            if (validCount == 0) {
                LAST_ERROR = "Nenhum app válido para bloquear";
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            tunnel = builder.establish();
            if (tunnel == null) throw new IOException("VPN nao autorizada pelo Android");
            keepRunning = true;
            BYTES_DISCARDED.set(0);
            ACTIVE.set(true);
            final int session = SESSION_GENERATION.get();
            final ParcelFileDescriptor activeTun = tunnel;
            readerThread = new Thread(() -> consumeDiscardedPackets(activeTun, session), "Guardiao-VPN-TUN");
            readerThread.start();
        } catch (Exception e) {
            LAST_ERROR = "Falha ao ativar VPN: " + e.getClass().getSimpleName();
            Log.e("GuardiaoVpn", "Falha ao ativar bloqueio", e);
            stopBlocking();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void consumeDiscardedPackets(ParcelFileDescriptor local, int session) {
        if (local == null) return;
        try (FileInputStream incoming = new FileInputStream(local.getFileDescriptor())) {
            byte[] packet = new byte[32767];
            while (keepRunning && session == SESSION_GENERATION.get()) {
                int length = incoming.read(packet);
                if (length < 0) break;
                if (length > 0) BYTES_DISCARDED.addAndGet(length);
                // NADA é enviado para a internet: o pacote é descartado.
            }
        } catch (IOException e) {
            if (keepRunning) Log.w("GuardiaoVpn", "Interface VPN foi encerrada", e);
        } finally {
            // Uma sessão antiga não deve marcar como inativa a VPN recém reconfigurada.
            if (session == SESSION_GENERATION.get()) {
                keepRunning = false;
                ACTIVE.set(false);
            }
        }
    }

    private void stopBlocking() {
        keepRunning = false;
        SESSION_GENERATION.incrementAndGet();
        ACTIVE.set(false);
        APPLIED_COUNT.set(0);
        SKIPPED_COUNT.set(0);
        APPLIED_PACKAGES.clear();
        if (tunnel != null) {
            try { tunnel.close(); } catch (IOException ignored) {}
            tunnel = null;
        }
        readerThread = null;
    }

    @Override public void onRevoke() {
        stopBlocking();
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        stopBlocking();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CHANNEL_ID,
            "Bloqueio de aplicativos", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Mostra quando o bloqueio local está ativo");
        mgr.createNotificationChannel(c);
    }

    private Notification makeNotification(int count) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, BlockVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Guardião de Dados • bloqueio ativo")
            .setContentText(count + " app(s) selecionado(s) sem internet")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(
                R.drawable.ic_notification, "Desativar", stopPending).build())
            .build();
    }
}
