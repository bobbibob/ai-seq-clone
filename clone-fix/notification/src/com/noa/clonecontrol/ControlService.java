package com.noa.clonecontrol;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class ControlService extends Service {
    private static final String CHANNEL = "clone-control";
    private static final int ID = 26;
    private static final String CONTROL = "/data/adb/modules/noa_clone_bridge_ids_candidate/control.sh";
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private boolean enabled;
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Автоматизация клонов", NotificationManager.IMPORTANCE_LOW));
        startForeground(ID, notification("Проверяю состояние…"));
        worker.scheduleWithFixedDelay(() -> update("status"), 0, 15, TimeUnit.SECONDS);
    }
    private Notification notification(String text) {
        Intent toggle = new Intent(this, ControlService.class).setAction(enabled ? "OFF" : "ON");
        PendingIntent action = PendingIntent.getService(this, 1, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(enabled ? "AI Clone: включено" : "AI Clone: выключено")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, enabled ? "Выключить" : "Включить", action).build()).build();
    }
    private void update(String command) {
        String text;
        try {
            Process process = new ProcessBuilder("su", "-c", "sh " + CONTROL + " " + command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Нет ответа root-службы");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            if (process.exitValue() != 0) throw new IOException(output.toString().trim());
            String result = output.toString();
            if (!result.startsWith("ON\n") && !result.startsWith("OFF\n")) throw new IOException("Неизвестное состояние службы");
            enabled = result.startsWith("ON\n");
            text = enabled ? "i2v: 10 в день · i2i: 2 в день. Проверка каждые 30 с." : "Автоматическая ротация приостановлена.";
        } catch (Exception error) {
            text = "Ошибка управления: " + error.getMessage();
        }
        getSystemService(NotificationManager.class).notify(ID, notification(text));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ("ON".equals(intent.getAction()) || "OFF".equals(intent.getAction()))) {
            final String command = intent.getAction().toLowerCase(java.util.Locale.ROOT);
            worker.execute(() -> update(command));
        }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
