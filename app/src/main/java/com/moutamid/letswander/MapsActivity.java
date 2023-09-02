package com.moutamid.letswander;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Button btnReturnToLocation;
    private List<Marker> markerList = new ArrayList<>();

    private TextToSpeech textToSpeech;
    private HashMap<String, String> ttsParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btnReturnToLocation = findViewById(R.id.btnReturnToLocation);
        geofencingClient = LocationServices.getGeofencingClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            requestLocationUpdates();
        }

        btnReturnToLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnToCurrentLocation();
            }
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult.getLastLocation() != null) {
                    LatLng userLocation = new LatLng(
                            locationResult.getLastLocation().getLatitude(),
                            locationResult.getLastLocation().getLongitude()
                    );
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f));

                    // Check for geofence and read description
                    checkGeofenceAndReadDescription(locationResult.getLastLocation());
                }
            }
        };
    }

    private void returnToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);
        fetchMarkerDataFromDatabase();
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void fetchMarkerDataFromDatabase() {
        DatabaseReference databaseReference = Constants.databaseReference().child("Markers");

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                BitmapDescriptor icon;
                mMap.clear();

                for (DataSnapshot markerSnapshot : snapshot.getChildren()) {
                    String markerId = markerSnapshot.getKey();
                    double latitude = markerSnapshot.child("latitude").getValue(Double.class);
                    double longitude = markerSnapshot.child("longitude").getValue(Double.class);
                    String title = markerSnapshot.child("title").getValue(String.class);
                    String description = markerSnapshot.child("description").getValue(String.class);
                    Boolean isStar = markerSnapshot.child("isStar").getValue(Boolean.class);

                    LatLng coordinate = new LatLng(latitude, longitude);

                    if (!isStar) {
                        icon = BitmapDescriptorFactory.fromResource(R.drawable.blue_dot_marker);
                        Geofence geofence = new Geofence.Builder()
                                .setRequestId(markerId)
                                .setCircularRegion(latitude, longitude, 12000)
                                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                                .build();

                        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                                .addGeofence(geofence)
                                .build();

                        if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MapsActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                        } else {
                            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent());
                        }
                    }

                    else {
                        icon = BitmapDescriptorFactory.fromResource(R.drawable.green_star_marker);
                    }

                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(coordinate)
                            .title(title)
                            .snippet(description)
                            .icon(icon));
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
        showMarkerDialog(marker.getTitle(), (String) marker.getTag());
        return true;
    }

    private void showMarkerDialog(String title, String description) {
        textToSpeech = new TextToSpeech(this, this);
        ttsParams = new HashMap<>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "uniqueId");

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

            // Assuming 12000 is the radius of your geofence in meters
            if (distance[0] <= 12000) {
                // User is inside the geofence of this marker
                showMarkerDescription(marker);
                break; // You can choose to break here if you want to stop checking other markers
            }
        }
    }

    private void showMarkerDescription(Marker marker) {
        String description = (String) marker.getTag();

        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop(); // Stop any ongoing speech
        }

        textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, ttsParams);
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
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
}
