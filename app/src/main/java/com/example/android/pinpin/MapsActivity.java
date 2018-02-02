package com.example.android.pinpin;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
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
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient client;
    public static final int REQUEST_LOCATION_CODE = 99;
    private ArrayList<Marker> mapMarkers = new ArrayList<>();

    // For alert dialog
    final Context context = this;

    // Reads in the coordinates from the database and adds/removes pins from the map
    final Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            System.out.println("NEW TIME CYCLE");

            FirebaseDatabase database = FirebaseDatabase.getInstance();
            final DatabaseReference myRef = database.getReference("Coordinates/");

            myRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Get list of coordinates from the database
                        ArrayList<LatLng> dbCoords = new ArrayList<>();
                        collectCoordinates((Map<String,Object>) dataSnapshot.getValue(), dbCoords);

                        addMarkers(dbCoords);
                        removeMarkers(dbCoords);
                    }
                    else {
                        for (Marker m : mapMarkers) {
                            m.remove();
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

            timerHandler.postDelayed(this, 15000); // Update every 15 seconds
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Read in coordinates from the database
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // Reads in all the coordinates in the database
    // TODO: May want to change this for only coords within x miles of user
    private void collectCoordinates(Map<String, Object> coords, ArrayList<LatLng> dbCoords) {
        for (Map.Entry<String, Object> entry : coords.entrySet()) {
            // Get coord map
            Map singleCoord = (Map) entry.getValue();

            // Get lat and lng and append to list
            Double lat = ((Double) singleCoord.get("Lat"));
            Double lng = ((Double) singleCoord.get("Lng"));

            // I dont know why i have to add this check but it works
            if (lat != null && lng != null) {
                LatLng l = new LatLng(lat, lng);
                dbCoords.add(l);
            }
        }
    }

    // Adds all the markers from the database onto the map
    private void addMarkers(ArrayList<LatLng> dbCoords) {
        for (LatLng l : dbCoords) {
            Marker marker = mMap.addMarker(new MarkerOptions().position(l));

            // Add the marker to array of markers on map
            mapMarkers.add(marker);
        }
    }

    // Removes all the markers on the map that don't exist on the database anymore
    private void removeMarkers(ArrayList<LatLng> dbCoords) {
        boolean exists = false;

        for (Marker m : mapMarkers) {
            for (LatLng l : dbCoords) {
                // Marker exists
                if (l.latitude == m.getPosition().latitude && l.longitude == m.getPosition().longitude) {
                    exists = true;
                }
            }
            
            if (!exists) {
                m.remove();
            }

            exists = false;
        }
    }

    // For handling permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission is granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (client == null) {
                            buildGoogleApiClient();
                        }

                        mMap.setMyLocationEnabled(true);
                    }
                    //Permission denied
                    else {
                        Toast.makeText(this, "Permission Denied!", Toast.LENGTH_LONG).show();
                    }
                }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        // Add a marker on tap
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng pin) {
                // Add the marker to the map
                Marker marker = mMap.addMarker(new MarkerOptions().position(pin));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pin, 18));

                // Add the marker to array of markers on map
                mapMarkers.add(marker);

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Coordinates/");

                // Make a unique id based on lat and lng
                String hash = String.valueOf(pin.latitude) + String.valueOf(pin.longitude);
                String id = String.valueOf(hash.hashCode());

                // Write lat and long to database
                ref.child(id).child("Lat").setValue(pin.latitude);
                ref.child(id).child("Lng").setValue(pin.longitude);
            }
        });

        // Delete a marker on tap
        // TODO: Might have to also add users unique id to firebase so user cant delete other users pins
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                final Marker m = marker;

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Confirm Delete?");
                alertDialogBuilder
                    .setMessage("Delete this Pin?")
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            // Delete the lat & long entry from database
                            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Coordinates/");

                            // Get the unique id based on lat and lng
                            double lat = m.getPosition().latitude;
                            double lng = m.getPosition().longitude;
                            String hash = String.valueOf(lat) + String.valueOf(lng);
                            String id = String.valueOf(hash.hashCode());

                            // Remove the entry from the database
                            ref.child(id).removeValue();

                            // Remove marker from map
                            m.remove();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.cancel();
                        }
                    });

                // Show alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

                return false;
            }
        });
    }

    protected synchronized void buildGoogleApiClient() {
        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        client.connect();
    }

    // When search button is clicked
    public void onClick(View v) {
        if (v.getId() == R.id.B_search) {
            EditText tf_location = (EditText)findViewById(R.id.TF_location);
            String location = tf_location.getText().toString();
            List<Address> addressList = null;
            MarkerOptions mo = new MarkerOptions();

            if (! location.equals("")) {
                Geocoder geo = new Geocoder(this);

                try {
                    addressList = geo.getFromLocationName(location, 5);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Put marker on addresses
                for (int i = 0; i < addressList.size(); i++) {
                    Address myAddress = addressList.get(i);
                    LatLng latLng = new LatLng(myAddress.getLatitude(), myAddress.getLongitude());
                    mo.position(latLng);
                    mo.title("TEST");
                    mMap.addMarker(mo);
                }
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Move map to current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));

        // Stop location updates after setting. Prob comment out after
        if (client != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest locationRequest = new LocationRequest();

        locationRequest.setInterval(1000); //1000 milliseconds
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
        }
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check if user has given permission previously and denied request
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            // Ask user for permission
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            return false;
        }
        else {
            return true;
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}