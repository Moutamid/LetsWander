package com.moutamid.letswander.activities;

import android.Manifest;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.moutamid.letswander.Constants;
import com.moutamid.letswander.models.MarkerData;
import com.moutamid.letswander.R;
import com.moutamid.letswander.service.GeofenceForegroundService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, TextToSpeech.OnInitListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private GoogleMap mMap;
    Intent mServiceIntent;
    private GeofenceForegroundService mYourService;
    private String descriptionToSpeak;
    private GeofencingClient geofencingClient;
    private TextToSpeech textToSpeech;
    private List<MarkerData> markerDataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        DatabaseReference markersRef = Constants.databaseReference().child("Markers");
        markerDataList = new ArrayList<>();

        geofencingClient = LocationServices.getGeofencingClient(this);
        textToSpeech = new TextToSpeech(this, this);

        markersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    MarkerData firebaseMarkerData = snapshot.getValue(MarkerData.class);

                    // Convert FirebaseMarkerData to your MarkerData
                    MarkerData markerData = new MarkerData(
                            firebaseMarkerData.getId(),
                            firebaseMarkerData.getLatitude(),
                            firebaseMarkerData.getLongitude(),
                            firebaseMarkerData.getTitle(),
                            firebaseMarkerData.getDescription(),
                            firebaseMarkerData.getStar()
                    );

                    markerDataList.add(markerData);
                }

                // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
                mapFragment.getMapAsync(MapsActivity.this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        for (MarkerData markerData : markerDataList) {
            LatLng location = new LatLng(markerData.getLatitude(), markerData.getLongitude());

            Boolean star = markerData.getStar();

            int width = 48;
            int height = 48;

            BitmapDescriptor markerIcon;
            if (star != null && star.booleanValue()) {
                markerIcon = vectorToBitmap(R.drawable.baseline_star_rate_24, width, height);
            } else {
                markerIcon = vectorToBitmap(R.drawable.baseline_circle_24, width, height);
            }

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(location)
                    .title(markerData.getTitle())
                    .snippet(markerData.getDescription())
                    .icon(markerIcon);

            Marker marker = mMap.addMarker(markerOptions);

            if (star != null && !star) {
                Geofence geofence = createGeofence(location, 12000);
                GeofencingRequest geofencingRequest = createGeofencingRequest(geofence);
                addGeofence(geofencingRequest);
            }
        }

        mMap.setOnMarkerClickListener(this);
        moveToCurrentUserLocation();
    }

    private BitmapDescriptor vectorToBitmap(@DrawableRes int vectorResourceId, int width, int height) {
        Drawable vectorDrawable = ContextCompat.getDrawable(this, vectorResourceId);
        vectorDrawable.setBounds(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }


    @Override
    public boolean onMarkerClick(Marker marker) {
        showCustomDialog(marker.getTitle(), marker.getPosition(), marker.getSnippet());
        descriptionToSpeak = marker.getSnippet(); // Set the description to speak
        onInit(TextToSpeech.SUCCESS); // Initialize TTS
        return true;
    }

    private void moveToCurrentUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15)); // You can adjust the zoom level as needed

                            } else {
                                Toast.makeText(getApplicationContext(), "Location not available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    private Geofence createGeofence(LatLng location, int radiusInMeters) {
        String geofenceId = "your_geofence_id";
        Geofence.Builder builder = new Geofence.Builder();
        builder.setRequestId(geofenceId)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER);
        builder.setCircularRegion(location.latitude, location.longitude, radiusInMeters);
        builder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        return builder.build();
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
                    .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("Geofence", "Successfully Added");
                        }
                    })
                    .addOnFailureListener(this, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("Geofence", "Failed to Add");
                        }
                    });
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    private PendingIntent getGeofencePendingIntent() {
        // Create an Intent to handle the geofence transitions
        Intent intent = new Intent(this, GeofenceForegroundService.class);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showCustomDialog(String title, LatLng location, String description) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.custom_dialog, null);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextView dialogLocation = dialogView.findViewById(R.id.dialog_location);
        TextView dialogDescription = dialogView.findViewById(R.id.dialog_description);

        dialogTitle.setText(title);
        dialogLocation.setText(location.latitude + ", " + location.longitude);
        dialogDescription.setText(description);

        builder.setView(dialogView);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        Button okButton = alertDialog.findViewById(R.id.dialog_ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (textToSpeech != null && textToSpeech.isSpeaking()) {
                    textToSpeech.stop();
                }
                alertDialog.dismiss();
            }
        });
    }


    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Text-to-speech language not supported.", Toast.LENGTH_SHORT).show();
            } else {
                if (descriptionToSpeak != null && !descriptionToSpeak.isEmpty()) {
                    textToSpeech.speak(descriptionToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        } else {
            Toast.makeText(this, "Text-to-speech initialization failed.", Toast.LENGTH_SHORT).show();
        }
    }
}
