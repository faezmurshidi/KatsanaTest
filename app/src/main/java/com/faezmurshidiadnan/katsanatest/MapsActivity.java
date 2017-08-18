package com.faezmurshidiadnan.katsanatest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.List;
import java.util.concurrent.TimeUnit;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap map;
    private Polyline gpsTrack;
    private GoogleApiClient googleApiClient;
    private LatLng lastKnownLatLng;
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    protected Location mStartLocation;
    protected Location mLastLocation;
    private float cumulativeDistance;
    private Double mLatitude;
    private Double mLongitude;
    private long startTime;
    private long currentTime;
    private TextView distance;
    private TextView speed;
    private float maxSpeed;

    private SupportMapFragment mapFragment;

    private TextView duration;
    private GraphView graph;

    private LineGraphSeries<DataPoint> series;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/nfs.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );
        setContentView(R.layout.test);

        if(!canAccessLocation()){
            getPermission();
        }

        //speedo
        distance = findViewById(R.id.distance);
        speed = findViewById(R.id.max_speed);
        duration = findViewById(R.id.duration);
        graph = findViewById(R.id.graph);
        cumulativeDistance = 0;
        maxSpeed = 0;

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        setupGraph();


    }

    private void setupGraph() {
        series = new LineGraphSeries<DataPoint>();
        series.setDrawBackground(true);
        series.setColor(Color.argb(255, 255, 60, 60));
        series.setBackgroundColor(Color.argb(100, 204, 119, 119));
        series.setDrawDataPoints(true);
        graph.addSeries(series);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScrollable(true);
        // customize a little bit viewport
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(150);
        viewport.setScrollable(true);

        GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
        gridLabel.setVerticalAxisTitle("KM/H");
        gridLabel.setHorizontalLabelsVisible(true);
        gridLabel.setHumanRounding(false);
        gridLabel.setPadding(33);
    }

    private void getPermission() {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        getLastLocation();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {/* ... */}

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {/* ... */}
                }).check();
    }

    private boolean canAccessLocation() {
        return !(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        getLastLocation();
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.color(Color.CYAN);
        polylineOptions.width(4);
        gpsTrack = map.addPolyline(polylineOptions);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        map.setMyLocationEnabled(true);

    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            startTime = System.currentTimeMillis();
                            mStartLocation = task.getResult();
                            mLastLocation = mStartLocation;
                            mLatitude = mStartLocation.getLatitude();
                            mLongitude = mStartLocation.getLongitude();
                            startMarker(mLatitude, mLongitude);


                        } else {
                            Log.w(TAG, "getLastLocation:exception", task.getException());

                        }
                    }
                });
    }

    private void startMarker(Double mLatitude, Double mLongitude) {
        LatLng current = new LatLng(mLatitude, mLongitude);
        map.addMarker(new MarkerOptions().position(current).title("You start here!"));
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 15));

    }


    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient.isConnected() && googleApiClient!=null) {
            stopLocationUpdates();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if(!canAccessLocation()){
            getPermission();
        }
        if (googleApiClient.isConnected() && googleApiClient!=null) {
            startLocationUpdates();
        }

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        lastKnownLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        currentTime = System.currentTimeMillis();

        long dDuration = currentTime - startTime;
        long min = TimeUnit.MILLISECONDS.toMinutes(dDuration);
        updateTrack();
        updateSpedo(location,min);

    }

    private void updateGraph(long min, float currentSpeed) {

        series.appendData(new DataPoint(min, currentSpeed), true, 1000);

    }

    private void updateSpedo(Location mCurrentLocation, long min) {
        if (mCurrentLocation.hasSpeed()) {
            float currentSpeed = mCurrentLocation.getSpeed();
            updateGraph(min,currentSpeed);
            //max speed
            float speedy = mCurrentLocation.getSpeed() + (3600/1000); //in km/h
            if(speedy>maxSpeed){
                maxSpeed = speedy;
                speed.setText(String.format("%.2f", maxSpeed));
            }
        }

        float currentDistance = mLastLocation.distanceTo(mCurrentLocation)/1000; //in km
        if (currentDistance != 0) {
            //cumulative distance
            cumulativeDistance+=currentDistance;
            distance.setText(String.format("%.2f", cumulativeDistance));
            mLastLocation = mCurrentLocation;
        }

        duration.setText(String.format("%.1f", (double)min));
    }

    protected void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, this);
    }

    private void updateTrack() {
        List<LatLng> points = gpsTrack.getPoints();
        points.add(lastKnownLatLng);
        gpsTrack.setPoints(points);
    }

}