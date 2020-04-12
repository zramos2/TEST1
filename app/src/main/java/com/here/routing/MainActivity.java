/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.routing;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.mapviewlite.MapScene;
import com.here.sdk.mapviewlite.MapStyle;
import com.here.sdk.mapviewlite.MapViewLite;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private PermissionsRequestor permissionsRequestor;
    private MapViewLite mapView;
    private RoutingExample routingExample;
    private FusedLocationProviderClient fusedLocationClient;
    public GeoCoordinates lastKnownLocation;
    public String finalLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get a MapView instance from layout.
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);


        handleAndroidPermissions();
        handleIntent(getIntent());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLocation();


    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            query = finalLocation;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.search_icon).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            //Called when the user submits the query. This could be due to a key press on
            // the keyboard or due to pressing a submit button.
            //https://developer.android.com/reference/android/widget/SearchView.OnQueryTextListener#onQueryTextSubmit(java.lang.String)
            @Override
            public boolean onQueryTextSubmit(String query) {
                routingExample.saveLocation(lastKnownLocation.latitude, lastKnownLocation.longitude);       //gets last known location (current location)
                routingExample.getFinalLocation(query);                                                     //gets destination location
                routingExample.geocodeAnAddress();                                                          //searches for the destination location
                return true;    //by returning true to indicate that it has handled the submit request.
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        return true;
    }


    //This function gets the last known location
    private void fetchLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            // Logic to handle location object
                            Double latitude = location.getLatitude();
                            Double longitude = location.getLongitude();

                            lastKnownLocation = new GeoCoordinates(latitude, longitude);

                            Log.d("Last known location: ", "Latitude = " + latitude + "\nLongitude = " + longitude);

                        }
                    }
                });
    }


    public GeoCoordinates getLastKnownLocation() {
        return lastKnownLocation;
    }

    private void handleAndroidPermissions() {
        permissionsRequestor = new PermissionsRequestor(this);
        permissionsRequestor.request(new PermissionsRequestor.ResultListener(){

            @Override
            public void permissionsGranted() {
                loadMapScene();
            }

            @Override
            public void permissionsDenied() {
                Log.e(TAG, "Permissions denied by user.");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsRequestor.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void loadMapScene() {
        // Load a scene from the SDK to render the map with a map style.
        mapView.getMapScene().loadScene(MapStyle.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(@Nullable MapScene.ErrorCode errorCode) {
                if (errorCode == null) {
                    routingExample = new RoutingExample(MainActivity.this, mapView);
                } else {
                    Log.d(TAG, "onLoadScene failed: " + errorCode.toString());
                }
            }
        });
    }

    public void addRouteButtonClicked(View view) {
        //fetchLocation();
        routingExample.saveLocation(lastKnownLocation.latitude, lastKnownLocation.longitude);
        routingExample.addRoute();
    }

    public void destinationButtonClicked(View view) {
        routingExample.onGeocodeButtonClicked();
    }

    public void addWaypointsButtonClicked(View view) {
        routingExample.addWaypoints();
    }

    public void clearMapButtonClicked(View view) {
        routingExample.clearMap();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
}
