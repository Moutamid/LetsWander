package com.moutamid.letswander.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.moutamid.letswander.activities.MapsActivity;
import com.moutamid.letswander.models.MarkerData;

import java.util.List;

public class GeofenceForegroundService extends Service {
    private static final String TAG = "GeofenceForegroundService";
    private static final String NOTIFICATION_CHANNEL_ID = "example.permanence";
    private TextToSpeech textToSpeech;
    private List<MarkerData> markerDataList;
    public static final String ACTION_GEOFENCE_TRANSITION = "com.moutamid.letswander.GEOFENCE_TRANSITION";

    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            startMyOwnForeground();
        } else {
            startForeground(1, new Notification());
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.GREEN);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_GEOFENCE_TRANSITION.equals(action)) {
                handleGeofenceTransition(intent);
            }
        }
        Notification notification = createNotification();
        startForeground(2, notification);

        return START_STICKY;
    }

    private Notification createNotification() {

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running in the background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        return notification;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartService");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
        Log.i("Service status", "Restarted");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(),
                this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent =
                PendingIntent.getService(getApplicationContext(),
                        1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
        super.onTaskRemoved(rootIntent);
        Log.i("Service status", "Restarted");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void handleGeofenceTransition(Intent intent) {
        if (GeofencingEvent.fromIntent(intent) != null) {
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

            if (geofencingEvent.hasError()) {
                int errorCode = geofencingEvent.getErrorCode();
                return;
            }

            int transitionType = geofencingEvent.getGeofenceTransition();

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
                for (Geofence geofence : triggeringGeofences) {
                    String geofenceRequestId = geofence.getRequestId();

                    for (MarkerData markerData : markerDataList) {
                        if (markerData.getId().equals(geofenceRequestId)) {
                            textToSpeech.speak(markerData.getDescription(), TextToSpeech.QUEUE_FLUSH, null, null);
                            break;
                        }
                    }
                }
            }
        } else {
            Log.e(TAG, "Invalid geofencing intent");
        }
    }
}
