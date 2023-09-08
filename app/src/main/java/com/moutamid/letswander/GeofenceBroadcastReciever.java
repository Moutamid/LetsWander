package com.moutamid.letswander;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.maps.model.LatLng;
import com.moutamid.letswander.activities.MapsActivity;
import com.moutamid.letswander.models.MarkerData;

import java.util.List;
import java.util.Locale;

public class GeofenceBroadcastReciever extends BroadcastReceiver {
    private Context context;
    private static final String TAG = "GeofenceBroadcastReceiver";
    private TextToSpeech textToSpeech;
    private String descriptionToSpeak;

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        List<MarkerData> markerDataList = MapsActivity.markerDataList;

        if (geofencingEvent.hasError()) {
            Log.d(TAG, "onReceive: Error receiving geofence event...");
            return;
        }

        List<Geofence> geofenceList = geofencingEvent.getTriggeringGeofences();
        for (Geofence geofence : geofenceList) {
            Log.d(TAG, "onReceive: " + geofence.getRequestId());
        }

        int transitionType = geofencingEvent.getGeofenceTransition();

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
            for (Geofence geofence : geofenceList) {
                String geofenceRequestId = geofence.getRequestId();
                Toast.makeText(context, "Geofence Transition" + transitionType, Toast.LENGTH_SHORT).show();

                for (MarkerData markerData : markerDataList) {

                    LatLng latLng = new LatLng(markerData.getLatitude(), markerData.getLongitude());
                    String location = String.valueOf(latLng);

                    if (geofenceRequestId.equals(location)) {
                        Toast.makeText(context, "Entered", Toast.LENGTH_SHORT).show();
                        Log.d("TTS Geofence", "Entered");
                        descriptionToSpeak = markerData.getDescription(); // Set the description to speak
                        onInit(TextToSpeech.SUCCESS);
                        break;
                    }
                }
            }
        } else {
            Log.e(TAG, "Invalid geofencing intent");
        }
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(context, "Text-to-speech language not supported.", Toast.LENGTH_SHORT).show();
            } else {
                if (descriptionToSpeak != null && !descriptionToSpeak.isEmpty()) {
                    textToSpeech.speak(descriptionToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        } else {
            Toast.makeText(context, "Text-to-speech initialization failed.", Toast.LENGTH_SHORT).show();
        }
    }
}
