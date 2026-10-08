package com.waypointvoice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Runs while you're navigating. Android keeps the app (GPS + voice) alive with the screen off
 * as long as this shows its "Navigating to …" notification.
 */
public class NavService extends Service {
    static final String CHANNEL = "nav";
    static final int NOTE_ID = 7;
    static String title = "Navigating";
    static volatile boolean running = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("title") != null) title = intent.getStringExtra("title");
        String text = intent != null && intent.getStringExtra("text") != null ? intent.getStringExtra("text") : "";
        try {
            Notification n = build(this, text);
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTE_ID, n);
        } catch (Exception e) {
            // e.g. location permission was turned off: navigation still works with the screen on
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    static Notification build(Context c, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_nav)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setContentIntent(pi)
                .build();
    }

    static void update(Context c, String text) {
        if (!running) return;
        try {
            c.getSystemService(NotificationManager.class).notify(NOTE_ID, build(c, text));
        } catch (Exception ignored) { }
    }
}
