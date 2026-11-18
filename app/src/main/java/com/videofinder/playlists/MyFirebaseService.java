package com.videofinder.playlists;

import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        super.onMessageReceived(msg);
        
        // Safety guard: only allow https URLs from pushes
        String url = msg.getData().get("url");
        if (url != null && !url.startsWith("https://")) {
            url = null;
        }
        
        // TODO: Add your existing notification builder logic here
        // Use 'url' safely when creating the PendingIntent
    }
}