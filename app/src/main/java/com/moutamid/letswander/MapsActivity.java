package com.moutamid.letswander;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import com.google.android.gms.location.FusedLocationProviderClient;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.moutamid.letswander.databinding.ActivityMapsBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, TextToSpeech.OnInitListener {
    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    Intent mServiceIntent;
    private GeofenceForegroundService mYourService;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private List<Marker> markerList = new ArrayList<>();
    private TextToSpeech textToSpeech;
    private HashMap<String, String> ttsParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button suggestPlace = findViewById(R.id.openWebsiteButton);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, this);
        ttsParams = new HashMap<>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "uniqueId");

        startInitService();
        requestLocationUpdates();
        fetchMarkerDataFromDatabase();
        requestLocationPermissions();

        /*suggestPlace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String websiteUrl = "https://forms.gle/BphPVbpQRspri5gr9";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(getApplicationContext(), "No web browser app found", Toast.LENGTH_SHORT).show();
                }
            }
        });*/
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            startGeofenceService();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult.getLastLocation() != null) {
                    LatLng userLocation = new LatLng(
                            locationResult.getLastLocation().getLatitude(),
                            locationResult.getLastLocation().getLongitude()
                    );
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f));

                    checkGeofenceAndReadDescription(locationResult.getLastLocation());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    private void fetchMarkerDataFromDatabase() {
        DatabaseReference databaseReference = Constants.databaseReference().child("Markers");

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mMap.clear();

                for (DataSnapshot markerSnapshot : snapshot.getChildren()) {
                    String title = markerSnapshot.child("title").getValue(String.class);
                    String description = markerSnapshot.child("description").getValue(String.class);
                    double latitude = markerSnapshot.child("latitude").getValue(Double.class);
                    double longitude = markerSnapshot.child("longitude").getValue(Double.class);

                    LatLng coordinate = new LatLng(latitude, longitude);

                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(coordinate)
                            .title(title)
                            .snippet(description));

                    marker.setTag(description);
                    markerList.add(marker);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        showMarkerDialog((String) marker.getTitle(), (String) marker.getTag());
        return true;
    }

    private void showMarkerDialog(String title, String description) {
        textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, ttsParams);
    }

    private void checkGeofenceAndReadDescription(Location location) {
        for (Marker marker : markerList) {
            float[] distance = new float[1];
            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    marker.getPosition().latitude, marker.getPosition().longitude,
                    distance
            );

            if (distance[0] <= 12000) {
                showMarkerDescription(marker);
                break;
            }
        }
    }

    private void showMarkerDescription(Marker marker) {
        String description = (String) marker.getTag();

        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop(); // Stop any ongoing speech
        }

        textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, ttsParams);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language data missing or not supported", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void startInitService() {
        mYourService = new GeofenceForegroundService();
        mServiceIntent = new Intent(this, mYourService.getClass());
        if (!isMyServiceRunning(mYourService.getClass())) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                startForegroundService(mServiceIntent);
            } else {
                startService(mServiceIntent);
            }
        }
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i("Service status", "Running");
                return true;
            }
        }
        Log.i("Service status", "Not running");
        return false;
    }

    private void setupGeofences(List<Marker> markerList) {
        ArrayList<Geofence> geofenceList = new ArrayList<>();

        for (Marker marker : markerList) {
            double latitude = marker.getPosition().latitude;
            double longitude = marker.getPosition().longitude;
            float radius = 12000.0f; // Set the geofence radius as needed

            Geofence geofence = new Geofence.Builder()
                    .setRequestId(marker.getId()) // Use a unique identifier for each geofence
                    .setCircularRegion(latitude, longitude, radius)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build();

            geofenceList.add(geofence);
        }

        // Build the geofencing request
        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .addGeofences(geofenceList)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .build();

        // Request geofence monitoring
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                    .addOnSuccessListener(this, aVoid -> {
                        // Geofences added successfully
                    })
                    .addOnFailureListener(this, e -> {
                        // Geofences could not be added
                    });
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGeofenceService();
            } else {
                // Permission denied, handle it (e.g., show a message to the user)
            }
        }
    }
    private void startGeofenceService() {
        Intent serviceIntent = new Intent(this, GeofenceForegroundService.class);
        startService(serviceIntent);
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceForegroundService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
