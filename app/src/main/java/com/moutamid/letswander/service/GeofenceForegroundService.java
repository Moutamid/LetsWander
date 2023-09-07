package com.moutamid.letswander.service;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.moutamid.letswander.Constants;
import com.moutamid.letswander.activities.MapsActivity;
import com.moutamid.letswander.models.MarkerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeofenceForegroundService extends Service {
    private static final String TAG = "GeofenceForegroundService";
    private static final String NOTIFICATION_CHANNEL_ID = "example.permanence";
    private TextToSpeech textToSpeech;
    private GeofencingClient geofencingClient;
    private List<MarkerData> markerDataList;
    public static final String ACTION_GEOFENCE_TRANSITION = "com.moutamid.letswander.GEOFENCE_TRANSITION";

    public void onCreate() {
        super.onCreate();
        geofencingClient = LocationServices.getGeofencingClient(this);
        initializeTextToSpeech(); // Initialize Text-to-Speech
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            startMyOwnForeground();
        } else {
            startForeground(1, new Notification());
        }
    }

    // Initialize Text-to-Speech
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(getApplicationContext(), new OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "TTS language not supported");
                    }
                } else {
                    Log.e(TAG, "TTS initialization failed");
                }
            }
        });
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

        // Fetch data from Firebase
        fetchMarkerDataFromFirebase();

        Notification notification = createNotification();
        startForeground(2, notification);

        return START_STICKY;
    }

    private void fetchMarkerDataFromFirebase() {
        // Use your Constants class to get the reference to the Firebase database
        DatabaseReference databaseReference = Constants.databaseReference().child("Markers");

        // Add a ValueEventListener to listen for changes in the database
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    markerDataList = new ArrayList<>();

                    // Iterate through the dataSnapshot to get your data
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        // Parse your data and use it as needed
                        String id = snapshot.child("id").getValue(String.class);
                        double latitude = snapshot.child("latitude").getValue(Double.class);
                        double longitude = snapshot.child("longitude").getValue(Double.class);
                        String title = snapshot.child("title").getValue(String.class);
                        String description = snapshot.child("description").getValue(String.class);
                        boolean star = snapshot.child("star").getValue(Boolean.class);

                        // Create a MarkerData object
                        MarkerData markerData = new MarkerData(id, latitude, longitude, title, description, star);

                        // Add the MarkerData object to the list
                        markerDataList.add(markerData);
                    }
                    setUpGeofences();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle errors, if any
                Log.e(TAG, "Firebase Database Error: " + databaseError.getMessage());
            }
        });
    }

    private void setUpGeofences() {
        if (markerDataList != null) {
            Log.d("marker datas", "not null");
            for (MarkerData markerData : markerDataList) {
                if (!markerData.getStar()) {
                    Log.d("marker datas", "ID : " + markerData.getId());
                    Geofence geofence = createGeofence(markerData, 12);
                    GeofencingRequest geofencingRequest = createGeofencingRequest(geofence);
                    addGeofence(geofencingRequest);
                }
                Log.d("marker datas", "it is star");
            }
        }
        Log.d("marker datas", "null");
    }

    private GeofencingRequest createGeofencingRequest(Geofence geofence) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.addGeofence(geofence);
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        return builder.build();
    }

    private void addGeofence(GeofencingRequest geofencingRequest) {
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("Geofence", "Successfully Added");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("Geofence", "Failed to Add");
                        }
                    });
        } else {
            requestLocationPermission();
        }
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceForegroundService.class);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Geofence createGeofence(MarkerData markerData, int radiusInMeters) {
        String geofenceId = markerData.getId();
        Geofence.Builder builder = new Geofence.Builder();
        builder.setRequestId(geofenceId)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER);
        builder.setCircularRegion(markerData.getLatitude(), markerData.getLongitude(), radiusInMeters);
        builder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        Log.d("createGeofence", "createGeofence : " + geofenceId);
        return builder.build();
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

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

    private void requestLocationPermission() {
        // Create an intent to send to your main activity to request permissions
        Intent intent = new Intent(this, MapsActivity.class);
        intent.setAction(MapsActivity.ACTION_REQUEST_LOCATION_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

}
