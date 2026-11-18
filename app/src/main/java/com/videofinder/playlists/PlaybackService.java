package com.videofinder.playlists;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class PlaybackService extends Service {

    private static final int NOTIF_ID = 42;
    private static final String CHANNEL = "playback";

    /** Set by MainActivity — called when user taps Stop in the notification. */
    public static Runnable onStopRequested;

    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoFinder:background");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(6 * 60 * 60 * 1000L); // safety cap: 6 hours
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            if (onStopRequested != null) onStopRequested.run(); // runs on main thread
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "Background playback", NotificationManager.IMPORTANCE_LOW));
            }
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, PlaybackService.class);
        stop.setAction("STOP");
        PendingIntent piStop = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setOngoing(true)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Video playing in background")
                .setContentText("Tap to return to the app")
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_media_pause, "Stop", piStop)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { stopSelf(); }

    @Override public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
