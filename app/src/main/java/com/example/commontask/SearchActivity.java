package com.example.commontask;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsContract;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.service.ReconciliationDbService;
import com.example.commontask.service.SensorLocationUpdateService;
import com.example.commontask.service.NominatimLocationService;
import com.example.commontask.service.SearchActivityProcessResultFromAddressResolution;
import com.example.commontask.service.SensorLocationUpdater;
import com.example.commontask.utils.PreferenceUtil;
import com.example.commontask.utils.Utils;
import com.example.commontask.utils.WidgetUtils;

import java.util.List;
import java.util.Locale;

import static com.example.commontask.utils.LogToFile.appendLog;

public class SearchActivity extends WeatherBaseActivity {

    public static final String TAG = "SearchActivity";

    public static final String ACTION_ADDRESS_RESOLUTION_RESULT = "com.example.commontask.action.ADDRESS_RESOLUTION_RESULT";

    private MapView map;
    private TextView resolvedLocationAddress;
    private Context mContext;
    private BroadcastReceiver mWeatherUpdateReceiver;
    private double longitude;
    private double latitude;
    private Address address;
    private String locale;
    private Button addLocatonButton;
    private ProgressDialog mProgressDialog;

    private void initializeWeatherReceiver() {
        mWeatherUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra("addresses")) {
                    address = (Address) intent.getExtras().getParcelable("addresses");
                    resolvedLocationAddress.setText(Utils.getCityAndCountryFromAddress(address));
                } else {
                    resolvedLocationAddress.setText(context.getString(R.string.location_not_found));
                }
                addLocatonButton.setVisibility(View.VISIBLE);
            }
        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((YourLocalWeather) getApplication()).applyTheme(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());
        Configuration.getInstance().setUserAgentValue(String.format("YourLocalWeather/%s (Linux; Android %s)",
                BuildConfig.VERSION_NAME,
                Build.VERSION.RELEASE));

        setContentView(R.layout.activity_search_weather_city_station);
        setupActionBar();

        addLocatonButton = (Button) findViewById(R.id.search_add_location_button);
        addLocatonButton.setVisibility(View.GONE);

        LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(this);
        List<Location> currentLocations = locationsDbHelper.getAllRows();
        Location lastLocation = currentLocations.get(currentLocations.size() - 1);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        IMapController mapController = map.getController();
        mapController.setZoom(11);
        GeoPoint startPoint;
        if ((lastLocation.getLongitude() == 0) && (lastLocation.getLatitude() == 0)) {
            startPoint = new GeoPoint(51.5072, -0.1267);
        } else {
            startPoint = new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());
        }
        mapController.setCenter(startPoint);

        resolvedLocationAddress = (TextView) findViewById(R.id.resolved_location_address);
        resolvedLocationAddress.setText(R.string.search_location_info);
        mContext = this;

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {

                mProgressDialog = new ProgressDialog(SearchActivity.this);
                mProgressDialog.setMessage(getString(R.string.progressDialog_gps_locate));
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            dialog.dismiss();
                        } catch (SecurityException e) {
                            appendLog(SearchActivity.this, TAG, "Cancellation error", e);
                        }
                    }
                });

                mProgressDialog.show();

                latitude = p.getLatitude();
                longitude = p.getLongitude();
                locale = PreferenceUtil.getLanguage(getApplicationContext());
                Intent resultionResult = new Intent(ACTION_ADDRESS_RESOLUTION_RESULT);
                resultionResult.setPackage("com.example.commontask");
                NominatimLocationService.getInstance().getFromLocation(
                        mContext,
                        p.getLatitude(),
                        p.getLongitude(),
                        1,
                        locale,
                        new SearchActivityProcessResultFromAddressResolution(mContext, resultionResult, mProgressDialog));
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };

        MapEventsOverlay overlayEvents = new MapEventsOverlay(mReceive);
        map.getOverlays().add(overlayEvents);

        initializeWeatherReceiver();
    }

    public void onResume(){
        super.onResume();
        registerReceiver(mWeatherUpdateReceiver,
                new IntentFilter(ACTION_ADDRESS_RESOLUTION_RESULT));
        map.onResume();
    }

    @Override
    protected void updateUI() {
    }

    public void onPause(){
        super.onPause();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        unregisterReceiver(mWeatherUpdateReceiver);
    }

    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public void addToLocations(View arg0) {
        storeLocation();
        finish();
    }

    private void storeLocation() {
        LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(this.getApplicationContext());

        int currentMaxOrderId = locationsDbHelper.getMaxOrderId();
        SQLiteDatabase db = locationsDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(LocationsContract.Locations.COLUMN_NAME_ADDRESS, locationsDbHelper.getAddressAsBytes(address));
        values.put(LocationsContract.Locations.COLUMN_NAME_LONGITUDE, longitude);
        values.put(LocationsContract.Locations.COLUMN_NAME_LATITUDE, latitude);
        values.put(LocationsContract.Locations.COLUMN_NAME_LOCALE, locale);
        values.put(LocationsContract.Locations.COLUMN_NAME_ORDER_ID, currentMaxOrderId + 1);
        values.put(LocationsContract.Locations.COLUMN_NAME_LOCATION_UPDATE_SOURCE, "");
        values.put(LocationsContract.Locations.COLUMN_NAME_ADDRESS_FOUND, 1);

        long newLocationRowId = db.insert(LocationsContract.Locations.TABLE_NAME, null, values);

        SensorLocationUpdater.autolocationForSensorEventAddressFound = true;
        appendLog(this,
                TAG,
                "autolocationForSensorEventAddressFound=",
                        SensorLocationUpdater.autolocationForSensorEventAddressFound);

        if (currentMaxOrderId == 0) {
            Intent intentToStartUpdate = new Intent("com.example.commontask.action.RESTART_ALARM_SERVICE");
            intentToStartUpdate.setPackage("com.example.commontask");
            startService(intentToStartUpdate);
        }
        Intent reconciliationService = new Intent(this, ReconciliationDbService.class);
        reconciliationService.putExtra("force", true);
        WidgetUtils.startBackgroundService(
                this,
                reconciliationService);
    }
}
