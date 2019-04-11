package nl.saxion.jeroenkoster.militarymapview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.gpkg.overlay.OsmMapShapeConverter;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.wkb.geom.Geometry;

import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity {

    final static String DATABASE_NAME = "Military";
    MapView map = null;
    private Marker marker;
    private JSONArray locations;
    private int currentLocation = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //handle permissions first, before map is created. not depicted here

        //load/initialize the osmdroid configuration, this can be done
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string
        //inflate and create the map
        setContentView(R.layout.activity_main);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.ALWAYS);
        map.setMultiTouchControls(true);

        IMapController mapController = map.getController();
        mapController.setZoom(17.5);
        GeoPoint startPoint = new GeoPoint(52.201708, 6.812436);
        mapController.setCenter(startPoint);
//        File parcoursFile = this.getAssets().open("Parcours").

        try {
            JSONObject obj = new JSONObject(loadLocationJSON());
            locations = obj.getJSONArray("locations");
            Log.d("Locations", "Locations: \n" + locations.toString());
            GeoPackageManager manager = GeoPackageFactory.getManager(this.getApplicationContext());
            InputStream is = this.getAssets().open("military.gpkg");
            manager.delete(DATABASE_NAME);
            boolean success = manager.importGeoPackage(DATABASE_NAME, is);
            Log.d("Database", "Import successful: " + success);
            GeoPackage geoPackage = manager.open(manager.databases().get(0));
            OsmMapShapeConverter converter = new OsmMapShapeConverter();
            List<String> features = geoPackage.getFeatureTables();

            String featureTable = features.get(0);
            Log.d("FeatureTable", featureTable);
            FeatureDao featureDao = geoPackage.getFeatureDao(featureTable);
            FeatureCursor featureCursor = featureDao.queryForAll();
            while (featureCursor.moveToNext()) {

                FeatureRow featureRow = featureCursor.getRow();
                GeoPackageGeometryData geometryData = featureRow.getGeometry();
                Geometry geometry = geometryData.getGeometry();
                Log.d("geometry", geometry.toString());
                converter.addToMap(map, geometry);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        marker = new Marker(map);
//        marker.setDefaultIcon();
        Drawable icon = getResources().getDrawable(R.drawable.ic_horseshoe);
        icon.setTint(Color.rgb(255,0,0));
        marker.setIcon(icon);

        marker.setPosition(new GeoPoint(52.201708, 6.812436));
//        marker.
        map.getOverlays().add(marker);
//        animateMarker(marker);
    }


    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    public void animateMarker(final Marker marker/*, final GeoPoint toPosition*/) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = map.getProjection();
        Point startPoint = proj.toPixels(marker.getPosition(), null);
        GeoPoint toPosition = new GeoPoint(marker.getPosition().getLatitude(), marker.getPosition().getLongitude());
        final IGeoPoint startGeoPoint = proj.fromPixels(startPoint.x , startPoint.y);
        final long duration = 500;
        final Interpolator interpolator = new LinearInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lng = t * locations.getJSONObject(currentLocation).getDouble("lat")+ (1 - t) * locations.getJSONObject(currentLocation).getDouble("lon");
                double lat = t * toPosition.getLatitude() + (1 - t) * startGeoPoint.getLatitude();
                marker.setPosition(new GeoPoint(lat, lng));
                if (t < 1.0) {
                    handler.postDelayed(this, 15);
                }
                map.postInvalidate();
            }
        });
    }

    public String loadLocationJSON() {
        String json = null;
        try {
            InputStream is = this.getAssets().open("TestLocations.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

}
