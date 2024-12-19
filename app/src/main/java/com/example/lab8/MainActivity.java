package com.example.lab8;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "MainActivity";
    private static final long TRACKING_INTERVAL = 30000; // 1 second

    private MapView map;
    private LocationManager locationManager;
    private Marker currentLocationMarker;
    private Polyline pathPolyline;
    private List<NamedGeoPoint> visitedPlaces;
    private boolean isTrackingStarted = false;
    private TextView timerTextView;
    private long startTime = 0L;

    private class NamedGeoPoint extends GeoPoint {
        String name;

        NamedGeoPoint(double latitude, double longitude, String name) {
            super(latitude, longitude);
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        timerTextView = findViewById(R.id.timerTextView);

        Button startTrackingButton = findViewById(R.id.startTrackingButton);
        Button stopTrackingButton = findViewById(R.id.stopTrackingButton);
        Button addPlaceButton = findViewById(R.id.addPlaceButton);
        Button showVisitedPlacesButton = findViewById(R.id.showVisitedPlacesButton);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        visitedPlaces = new ArrayList<>();
        pathPolyline = new Polyline();
        pathPolyline.setColor(Color.BLUE);
        map.getOverlays().add(pathPolyline);

        startTrackingButton.setOnClickListener(v -> startTracking());
        stopTrackingButton.setOnClickListener(v -> stopTracking());
        addPlaceButton.setOnClickListener(v -> addPlace());
        showVisitedPlacesButton.setOnClickListener(v -> showVisitedPlaces());

        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            initializeMap();
        }
    }

    private void initializeMap() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location lastKnownLocation = getLastKnownLocation();
            if (lastKnownLocation != null) {
                updateMapLocation(lastKnownLocation);
                visitedPlaces.add(new NamedGeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), "Начальная точка"));
            } else {
                Toast.makeText(this, "Не удалось получить местоположение. Пожалуйста, подождите...", Toast.LENGTH_SHORT).show();
                requestLocationUpdates();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    private void clearTrackingData() {
        visitedPlaces.clear();
        pathPolyline.setPoints(new ArrayList<>());
        map.getOverlays().clear();
        map.getOverlays().add(pathPolyline);
        if (currentLocationMarker != null) {
            map.getOverlays().add(currentLocationMarker);
        }
        map.invalidate();
        timerTextView.setText("0:00");
        startTime = 0L;
        isTrackingStarted = false;
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    private void stopLocationUpdates() {
        locationManager.removeUpdates(this);
    }

    private void updateMapLocation(Location location) {
        Log.d(TAG, "Updating map location: " + location.getLatitude() + ", " + location.getLongitude());
        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        IMapController mapController = map.getController();
        mapController.setZoom(15.0);
        mapController.setCenter(startPoint);

        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map);
            map.getOverlays().add(currentLocationMarker);
        }
        currentLocationMarker.setPosition(startPoint);

        if (isTrackingStarted) {
            pathPolyline.addPoint(startPoint);
        }

        map.invalidate();
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            clearTrackingData();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, TRACKING_INTERVAL, 10, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, TRACKING_INTERVAL, 10, this);
            isTrackingStarted = true;
            Toast.makeText(this, "Отслеживание начато", Toast.LENGTH_SHORT).show();
            startTime = SystemClock.uptimeMillis();
            updateTimer();
        } else {
            Toast.makeText(this, "Необходимо разрешение на использование геолокации", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopTracking() {
        stopLocationUpdates();
        isTrackingStarted = false;
        Toast.makeText(this, "Отслеживание остановлено", Toast.LENGTH_SHORT).show();
    }

    private void updateTimer() {
        if (isTrackingStarted) {
            long timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            int seconds = (int) (timeInMilliseconds / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            timerTextView.setText(String.format("%d:%02d", minutes, seconds));
            timerTextView.postDelayed(this::updateTimer, 1000);
        }
    }

    private void showVisitedPlaces() {
        map.getOverlays().clear();
        map.getOverlays().add(pathPolyline);
        if (currentLocationMarker != null) {
            map.getOverlays().add(currentLocationMarker);
        }

        for (NamedGeoPoint point : visitedPlaces) {
            Marker marker = new Marker(map);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(point.name);
            map.getOverlays().add(marker);
        }
        map.invalidate();
    }

    private void addPlace() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Добавить место");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_place, null);
        final EditText input = viewInflated.findViewById(R.id.input);
        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String placeName = input.getText().toString();
            if (!placeName.isEmpty()) {
                Location location = getLastKnownLocation();
                if (location != null) {
                    NamedGeoPoint newPlace = new NamedGeoPoint(location.getLatitude(), location.getLongitude(), placeName);
                    visitedPlaces.add(newPlace);
                    Toast.makeText(MainActivity.this, "Место добавлено: " + placeName, Toast.LENGTH_SHORT).show();
                    showVisitedPlaces();
                } else {
                    Toast.makeText(MainActivity.this, "Не удалось получить текущее местоположение", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateMapLocation(location);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.d(TAG, provider + " включен");
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.d(TAG, provider + " отключен");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeMap();
        } else {
            Toast.makeText(this, "Разрешение на использование геолокации не предоставлено", Toast.LENGTH_SHORT).show();
        }
    }
}