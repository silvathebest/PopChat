package com.skymanov.popchat.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.skymanov.popchat.R;
import com.skymanov.popchat.activities.ChatActivity;
import com.skymanov.popchat.models.User;
import com.skymanov.popchat.utilities.Constants;

import java.util.Random;

public class MessagingService extends FirebaseMessagingService {

    String GROUP_KEY_MESSAGE_RECEIVED = "com.skymanov.popchat.MESSAGE_RECEIVED";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d("FCM", "Token: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        User user = new User();
        user.id = remoteMessage.getData().get(Constants.KEY_USER_ID);
        user.name = remoteMessage.getData().get(Constants.KEY_NAME);
        user.token = remoteMessage.getData().get(Constants.KEY_FCM_TOKEN);

        int notificationId = new Random().nextInt();
        String channelId = "chat_message";
        Intent intent = new Intent(this, ChatActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Constants.KEY_USER, user);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        FirebaseFirestore database = FirebaseFirestore.getInstance();

        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(user.id).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String image = task.getResult().getString(Constants.KEY_IMAGE);
                builder.setSmallIcon(R.drawable.ic_notification);
                builder.setLargeIcon(Constants.getDecodedImage(image));
                builder.setContentTitle(user.name);
                builder.setContentText(remoteMessage.getData().get(Constants.KEY_MESSAGE));
                builder.setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(remoteMessage.getData().get(Constants.KEY_MESSAGE)));
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                builder.setContentIntent(pendingIntent);
                builder.setAutoCancel(true);
                builder.setGroup(GROUP_KEY_MESSAGE_RECEIVED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    CharSequence channelName = "Chat Message";
                    String channelDescription = "This notification channel is used for chat message notifications";
                    int importance = NotificationManager.IMPORTANCE_HIGH;
                    NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
                    channel.setDescription(channelDescription);
                    NotificationManager notificationManager = getSystemService(NotificationManager.class);
                    notificationManager.createNotificationChannel(channel);
                }

                NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
                notificationManagerCompat.notify(notificationId, builder.build());
            }
        });

    }
}
