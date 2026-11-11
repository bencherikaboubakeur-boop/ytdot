package com.ytdot.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        String title = msg.getData().get("title");
        String body = msg.getData().get("body");
        String url = msg.getData().get("url");
        if (title == null && msg.getNotification() != null) title = msg.getNotification().getTitle();
        if (body == null && msg.getNotification() != null) body = msg.getNotification().getBody();
        if (title == null) title = "Message";
        if (body == null) body = "";

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("pub", "Announcements", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }

        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("url", url);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify((int) System.currentTimeMillis(), new NotificationCompat.Builder(this, "pub")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }
}
