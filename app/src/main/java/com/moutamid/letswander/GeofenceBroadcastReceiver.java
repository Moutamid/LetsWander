package com.moutamid.letswander;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;
import java.util.Locale;

public class GeofenceBroadcastReceiver extends BroadcastReceiver implements TextToSpeech.OnInitListener {
    private TextToSpeech textToSpeech;

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            if (triggeringGeofences != null && !triggeringGeofences.isEmpty()) {
                String description = triggeringGeofences.get(0).getRequestId(); // Use RequestId as description
                speakDescription(context, description);
            }
        }
    }

    private void speakDescription(Context context, final String description) {
        textToSpeech = new TextToSpeech(context, this);
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Called when speech starts
            }

            @Override
            public void onDone(String utteranceId) {
                // Called when speech is done, stop the TextToSpeech engine
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    textToSpeech.shutdown();
                }
            }

            @Override
            public void onError(String utteranceId) {
                // Called if there's an error during speech
            }
        });

        if (textToSpeech != null) {
            textToSpeech.setLanguage(Locale.US);
            textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, null, "uniqueId");
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // TextToSpeech initialization successful
        } else {
            // TextToSpeech initialization failed
        }
    }
}
