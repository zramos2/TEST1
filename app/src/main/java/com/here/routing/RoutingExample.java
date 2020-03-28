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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;


import com.here.sdk.core.CustomMetadataValue;
import com.here.sdk.core.GeoBox;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.Point2D;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapviewlite.Camera;
import com.here.sdk.mapviewlite.MapImage;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapMarkerImageStyle;
import com.here.sdk.mapviewlite.MapPolyline;
import com.here.sdk.mapviewlite.MapPolylineStyle;
import com.here.sdk.mapviewlite.MapViewLite;
import com.here.sdk.mapviewlite.PickMapItemsCallback;
import com.here.sdk.mapviewlite.PickMapItemsResult;
import com.here.sdk.mapviewlite.PixelFormat;
import com.here.sdk.routing.CalculateRouteCallback;
import com.here.sdk.routing.Maneuver;
import com.here.sdk.routing.ManeuverAction;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RouteLeg;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.RoutingEngine.CarOptions;
import com.here.sdk.routing.RoutingError;
import com.here.sdk.routing.Waypoint;

import com.here.sdk.search.GeocodingEngine;
import com.here.sdk.search.GeocodingOptions;
import com.here.sdk.core.LanguageCode;
import com.here.sdk.search.GeocodingCallback;
import com.here.sdk.search.SearchCategory;
import com.here.sdk.search.SearchError;
import com.here.sdk.search.GeocodingResult;
import com.here.sdk.search.SearchResult;
import com.here.sdk.searchcommon.Address;
import com.here.sdk.core.Metadata;
import com.here.sdk.core.Anchor2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RoutingExample {

    private static final String TAG = RoutingExample.class.getName();

    private Context context;
    private MapViewLite mapView;
    private final List<MapMarker> mapMarkerList = new ArrayList<>();
    private final List<MapPolyline> mapPolylines = new ArrayList<>();
    private RoutingEngine routingEngine;
    private GeoCoordinates startGeoCoordinates;         //This is where you start
    private GeoCoordinates destinationGeoCoordinates;   //This is where you want to go
    private GeocodingEngine geocodingEngine;
    private Camera camera;





    public RoutingExample(Context context, MapViewLite mapView) {
        this.context = context;
        this.mapView = mapView;
        camera = mapView.getCamera();
        camera.setTarget(new GeoCoordinates(41.871657, -87.647428));  //this coordinate = Jane Addams Hull House Museum
        camera.setZoomLevel(14);

        try {
            routingEngine = new RoutingEngine();
        } catch (InstantiationErrorException e) {
            new RuntimeException("Initialization of RoutingEngine failed: " + e.error.name());
        }

        try {
            geocodingEngine = new GeocodingEngine();
        } catch (InstantiationErrorException e) {
            new RuntimeException("Initialization of GeocodingEngine failed: " + e.error.name());
        }

        setTapGestureHandler();


    }

    public void onGeocodeButtonClicked() {
        // Search for the location that belongs to an address and show it on the map.
        geocodeAnAddress();
    }

    private void geocodeAnAddress() {
        // Set map to expected location.
        camera.setTarget(new GeoCoordinates(41.871657, -87.647428));        //this coordinate = Jane Addams Hull House Museum

        String streetName = "750 S Halsted St";

        Toast.makeText(context,"Finding locations in viewport for: " + streetName
                + ". Tap marker to see the coordinates. Check the logs for the address.", Toast.LENGTH_LONG).show();

        geocodeAddressInViewport(streetName);
    }

    private void geocodeAddressInViewport(String queryString) {
        clearMap();

        GeoBox geoBox = mapView.getCamera().getBoundingRect();
        long maxResultCount = 30;
        GeocodingOptions geocodingOptions = new GeocodingOptions(
                LanguageCode.EN_US, maxResultCount);


        //Geocode an address to a location
        //This let's you search raw coordinates and other location details by passing an
        // address in detail such as a street name or city
        geocodingEngine.searchLocations(geoBox, queryString, geocodingOptions, new GeocodingCallback() {
            @Override
            public void onSearchCompleted(@Nullable SearchError searchError,
                                          @Nullable List<GeocodingResult> list) {
                if (searchError != null) {
                    showDialog("Geocoding", "Error: " + searchError.toString());
                    return;
                }

                if (list.isEmpty()) {
                    showDialog("Geocoding", "No geocoding results found.");
                    return;
                }

                for (GeocodingResult geocodingResult : list) {
                    GeoCoordinates geoCoordinates = geocodingResult.coordinates;    //coordinates of 750 s halsted
                    Address address = geocodingResult.address;                      //Address: 750 S Halsted
                    if (address != null) {
                        String locationDetails = address.addressText
                                + ". GeoCoordinates: " + geoCoordinates.latitude
                                + ", " + geoCoordinates.longitude;

                        Log.d(TAG, "" +
                                ": " + locationDetails);
                        addPoiMapMarker(geoCoordinates);
                    }
                }

                showDialog("Geocoding result","Size: " + list.size());
            }
        });
    }

    private void setTapGestureHandler() {
        mapView.getGestures().setTapListener(touchPoint -> pickMapMarker(touchPoint));
    }


    private void pickMapMarker(final Point2D point2D) {
        float radiusInPixel = 2;
        mapView.pickMapItems(point2D, radiusInPixel, new PickMapItemsCallback() {
            @Override
            public void onMapItemsPicked(@Nullable PickMapItemsResult pickMapItemsResult) {
                if (pickMapItemsResult == null) {
                    return;
                }

                MapMarker topmostMapMarker = pickMapItemsResult.getTopmostMarker();
                if (topmostMapMarker == null) {
                    return;
                }

                Metadata metadata = topmostMapMarker.getMetadata();
                if (metadata != null) {
                    CustomMetadataValue customMetadataValue = metadata.getCustomValue("key_search_result");
                    if (customMetadataValue != null) {
                        SearchResultMetadata searchResultMetadata = (SearchResultMetadata) customMetadataValue;
                        String title = searchResultMetadata.searchResult.title;
                        String vicinity = searchResultMetadata.searchResult.vicinity;
                        SearchCategory category = searchResultMetadata.searchResult.category;
                        showDialog("Picked Search Result",
                                title + ", " + vicinity + ". Category: " + category.localizedName);
                        return;
                    }
                }

                showDialog("Picked Map Marker",
                        "Geographic coordinates: " +
                                topmostMapMarker.getCoordinates().latitude + ", " +
                                topmostMapMarker.getCoordinates().longitude);
            }
        });
    }


    private static class SearchResultMetadata implements CustomMetadataValue {

        public SearchResult searchResult;

        public SearchResultMetadata(SearchResult searchResult) {
            this.searchResult = searchResult;
        }

        @NonNull
        @Override
        public String getTag() {
            return "SearchResult Metadata";
        }
    }




    private void addPoiMapMarker(GeoCoordinates geoCoordinates) {
        MapMarker mapMarker = createPoiMapMarker(geoCoordinates);
        mapView.getMapScene().addMapMarker(mapMarker);
        mapMarkerList.add(mapMarker);
    }

    private void addPoiMapMarker(GeoCoordinates geoCoordinates, Metadata metadata) {
        MapMarker mapMarker = createPoiMapMarker(geoCoordinates);
        mapMarker.setMetadata(metadata);
        mapView.getMapScene().addMapMarker(mapMarker);
        mapMarkerList.add(mapMarker);
    }

    private MapMarker createPoiMapMarker(GeoCoordinates geoCoordinates) {
        MapImage mapImage = MapImageFactory.fromResource(context.getResources(), R.drawable.poi);
        MapMarker mapMarker = new MapMarker(geoCoordinates);
        MapMarkerImageStyle mapMarkerImageStyle = new MapMarkerImageStyle();
        mapMarkerImageStyle.setAnchorPoint(new Anchor2D(0.5F, 1));
        mapMarker.addImage(mapImage, mapMarkerImageStyle);
        return mapMarker;
    }





    public void addRoute() {
        clearMap();

        startGeoCoordinates = createRandomGeoCoordinatesInViewport();                   //TODO2
        destinationGeoCoordinates = createRandomGeoCoordinatesInViewport();             //TODO2
        Waypoint startWaypoint = new Waypoint(startGeoCoordinates);
        //Waypoint destinationWaypoint = new Waypoint(destinationGeoCoordinates);

        //List<GeocodingResult> list = new ArrayList<GeocodingResult>();

        GeoBox geoBox = mapView.getCamera().getBoundingRect();
        long maxResultCount = 30;
        GeocodingOptions geocodingOptions = new GeocodingOptions(
                LanguageCode.EN_US, maxResultCount);

        String queryString = "750 S Halsted St";


        geocodingEngine.searchLocations(geoBox, queryString, geocodingOptions, new GeocodingCallback()
        {
            @Override
            public void onSearchCompleted(@Nullable SearchError searchError, @Nullable List<GeocodingResult> list)
            {
                if (searchError != null) {
                    showDialog("Geocoding", "Error: " + searchError.toString());
                    return;
                }
                if (list.isEmpty()) {
                    showDialog("Geocoding", "No geocoding results found.");
                    return;
                }

                for (GeocodingResult geocodingResult : list) {
                    GeoCoordinates geoCoordinates = geocodingResult.coordinates;
                    Address address = geocodingResult.address;
                    destinationGeoCoordinates = geoCoordinates;

                    if (address != null) {
                        String locationDetails = address.addressText
                                + ". GeoCoordinates: " + geoCoordinates.latitude
                                + ", " + geoCoordinates.longitude;

                        Log.d(TAG, "GeocodingResult: " + locationDetails);
                        addPoiMapMarker(geoCoordinates);
                    }
                }
                    showDialog("Geocoding result","Size: " + list.size());
            }
        });

/*
        System.out.println(list.get(0));
//        GeocodingResult geocodingResult = list.;
//        GeoCoordinates geoCoordinates = geocodingResult.coordinates;    //coordinates of 750 s halsted
*/

        Waypoint destinationWaypoint = new Waypoint(destinationGeoCoordinates);

        List<Waypoint> waypoints =
                new ArrayList<>(Arrays.asList(startWaypoint, destinationWaypoint));

        routingEngine.calculateRoute(
                waypoints, new CarOptions(),
                new CalculateRouteCallback()
                {
                    @Override
                    public void onRouteCalculated(@Nullable RoutingError routingError, @Nullable List<Route> routes)
                    {
                        if (routingError == null)       //if routing is empty calculate the route
                        {
                            Route route = routes.get(0);
                            showRouteDetails(route);
                            showRouteOnMap(route);
                        } else {
                            showDialog("Error while calculating a route:", routingError.toString());
                        }
                    }
                });
    }

    private void showRouteDetails(Route route) {
        int estimatedTravelTimeInSeconds = route.getTravelTimeInSeconds();
        int lengthInMeters = route.getLengthInMeters();

        String routeDetails =
                "Travel Time: " + formatTime(estimatedTravelTimeInSeconds)
                + ", Length: " + formatLength(lengthInMeters);

        showDialog("Route Details", routeDetails);
    }

    private String formatTime(int sec) {
        int hours = sec / 3600;
        int minutes = (sec % 3600) / 60;

        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    private String formatLength(int meters) {
        int kilometers = meters / 1000;
        int remainingMeters = meters % 1000;

        return String.format(Locale.getDefault(), "%02d.%02d km", kilometers, remainingMeters);
    }

    private void showRouteOnMap(Route route) {
        // Show route as polyline.
        GeoPolyline routeGeoPolyline;
        try {
            routeGeoPolyline = new GeoPolyline(route.getShape());
        } catch (InstantiationErrorException e) {
            // It should never happen that the route shape contains less than two vertices.
            return;
        }

//Visualization of what the route looks like => Thickness of route, color of route
        MapPolylineStyle mapPolylineStyle = new MapPolylineStyle();
        mapPolylineStyle.setColor(0x00908AA0, PixelFormat.RGBA_8888);
        mapPolylineStyle.setWidth(5);
        MapPolyline routeMapPolyline = new MapPolyline(routeGeoPolyline, mapPolylineStyle);
        mapView.getMapScene().addMapPolyline(routeMapPolyline);
        mapPolylines.add(routeMapPolyline);

        // Draw a circle to indicate starting point and destination.
        addCircleMapMarker(startGeoCoordinates, R.drawable.green_dot);
        addCircleMapMarker(destinationGeoCoordinates, R.drawable.green_dot);

        // Log maneuver instructions per route leg.
        List<RouteLeg> routeLegs = route.getLegs();
        for (RouteLeg routeLeg : routeLegs) {
            logManeuverInstructions(routeLeg);
        }
    }

    private void logManeuverInstructions(RouteLeg routeLeg) {
        Log.d(TAG, "Log maneuver instructions per route leg:");
        List<Maneuver> maneuverInstructions = routeLeg.getManeuvers();
        for (Maneuver maneuverInstruction : maneuverInstructions) {
            ManeuverAction maneuverAction = maneuverInstruction.getAction();
            GeoCoordinates maneuverLocation = maneuverInstruction.getCoordinates();
            String maneuverInfo = maneuverInstruction.getText()
                    + ", Action: " + maneuverAction.name()
                    + ", Location: " + maneuverLocation.toString();
            Log.d(TAG, maneuverInfo);
        }
    }

    public void addWaypoints() {
        if (startGeoCoordinates == null || destinationGeoCoordinates == null) {
            showDialog("Error", "Please add a route first.");
            return;
        }

        clearWaypointMapMarker();
        clearRoute();

        Waypoint waypoint1 = new Waypoint(createRandomGeoCoordinatesInViewport());
        Waypoint waypoint2 = new Waypoint(createRandomGeoCoordinatesInViewport());
        List<Waypoint> waypoints = new ArrayList<>(Arrays.asList(new Waypoint(startGeoCoordinates),
                waypoint1, waypoint2, new Waypoint(destinationGeoCoordinates)));

        routingEngine.calculateRoute(
                waypoints,
                new CarOptions(),
                new CalculateRouteCallback() {
                    @Override
                    public void onRouteCalculated(@Nullable RoutingError routingError, @Nullable List<Route> routes) {
                        if (routingError == null) {
                            Route route = routes.get(0);
                            showRouteDetails(route);
                            showRouteOnMap(route);

                            // Draw a circle to indicate the location of the waypoints.
                            addCircleMapMarker(waypoint1.coordinates, R.drawable.red_dot);
                            addCircleMapMarker(waypoint2.coordinates, R.drawable.red_dot);
                        } else {
                            showDialog("Error while calculating a route:", routingError.toString());
                        }
                    }
                });
    }

    public void clearMap() {
        clearWaypointMapMarker();
        clearRoute();
    }

    private void clearWaypointMapMarker() {
        for (MapMarker mapMarker : mapMarkerList) {
            mapView.getMapScene().removeMapMarker(mapMarker);
        }
        mapMarkerList.clear();
    }

    private void clearRoute() {
        for (MapPolyline mapPolyline : mapPolylines) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }
        mapPolylines.clear();
    }

/*      TODO
    private GeoCoordinates lastKnownLocation() {


        return new GeoCoordinates();
    }
*/


    //This function makes a RANDOMLY generates coordinate
    //This function is used for addRoute and addWaypoint
    private GeoCoordinates createRandomGeoCoordinatesInViewport() {
        GeoBox geoBox = mapView.getCamera().getBoundingRect();
        GeoCoordinates northEast = geoBox.northEastCorner;
        GeoCoordinates southWest = geoBox.southWestCorner;

        double minLat = southWest.latitude;
        double maxLat = northEast.latitude;
        double lat = getRandom(minLat, maxLat);

        double minLon = southWest.longitude;
        double maxLon = northEast.longitude;
        double lon = getRandom(minLon, maxLon);

        return new GeoCoordinates(lat, lon);
    }

    private double getRandom(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private void addCircleMapMarker(GeoCoordinates geoCoordinates, int resourceId) {
        MapImage mapImage = MapImageFactory.fromResource(context.getResources(), resourceId);
        MapMarker mapMarker = new MapMarker(geoCoordinates);
        mapMarker.addImage(mapImage, new MapMarkerImageStyle());
        mapView.getMapScene().addMapMarker(mapMarker);
        mapMarkerList.add(mapMarker);
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.show();
    }
}
