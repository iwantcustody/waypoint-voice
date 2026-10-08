package com.waypointvoice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Notices when the Bluetooth device you picked as "my car" connects, then opens the app
 * (if you allowed "Display over other apps") or shows a tap-to-open notification.
 */
public class CarReceiver extends BroadcastReceiver {
    static final String PREFS = "car";
    static final String CHANNEL = "car";
    static final int NOTE_ID = 11;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null || !BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;
        SharedPreferences p = prefs(c);
        String want = p.getString("addr", "");
        if (want.isEmpty()) return;
        BluetoothDevice dev;
        try { dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); } catch (Exception e) { return; }
        if (dev == null || !want.equalsIgnoreCase(dev.getAddress())) return;
        String name = p.getString("name", "your car");

        Intent open = new Intent(c, MainActivity.class);
        open.setAction(Intent.ACTION_MAIN);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        open.putExtra("fromCar", name);

        if (p.getBoolean("autoOpen", false) && Settings.canDrawOverlays(c)) {
            try { c.startActivity(open); return; } catch (Exception ignored) { }
        }
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Car connected", NotificationManager.IMPORTANCE_HIGH));
            }
            PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_nav)
                    .setContentTitle("Connected to " + name)
                    .setContentText("Tap to open Shotgun")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            nm.notify(NOTE_ID, n);
        } catch (Exception ignored) { }
    }
}
