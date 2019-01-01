package com.example.commontask;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.NestedScrollView;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.commontask.model.CurrentWeatherDbHelper;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.Weather;
import com.example.commontask.service.CurrentWeatherService;
import com.example.commontask.ui.ProfileActivity;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.Constants;
import com.example.commontask.utils.LanguageUtil;
import com.example.commontask.utils.PermissionUtil;
import com.example.commontask.utils.PreferenceUtil;
import com.example.commontask.utils.TemperatureUtil;
import com.example.commontask.utils.Utils;
import com.example.commontask.utils.WindWithUnit;
import com.example.commontask.widget.WidgetRefreshIconService;

import java.util.ArrayList;
import java.util.List;

import static com.example.commontask.utils.LogToFile.appendLog;



public class MainWeatherAppActivity extends BaseActivityWeather implements AppBarLayout.OnOffsetChangedListener {


    private static final String TAG = "MainWeatherAppActivity";

        private TextView localityView;
        private ImageView mIconWeatherView;
        private TextView mTemperatureView;
        private TextView secondTemperatureView;
        private TextView mDescriptionView;
        private TextView mHumidityView;
        private TextView mWindSpeedView;
        private TextView mPressureView;
        private TextView mCloudinessView;
        private TextView mLastUpdateView;
        private TextView mSunriseView;
        private TextView mSunsetView;
        private AppBarLayout mAppBarLayout;
        private TextView iconSecondTemperatureView;
        private TextView mIconWindView;
        private TextView mIconHumidityView;
        private TextView mIconPressureView;
        private TextView mIconCloudinessView;
        private TextView mIconSunriseView;
        private TextView mIconSunsetView;

        private ConnectionDetector connectionDetector;
        private Boolean isNetworkAvailable;
        public static ProgressDialog mProgressDialog;
        private SwipeRefreshLayout mSwipeRefresh;
        private Menu mToolbarMenu;
        private BroadcastReceiver mWeatherUpdateReceiver;

        private WindWithUnit windWithUnit;
        private String iconSecondTemperature;
        private String mIconWind;
        private String mIconHumidity;
        private String mIconPressure;
        private String mIconCloudiness;
        private String mIconSunrise;
        private String mIconSunset;
        private String mPercentSign;

        private static final int REQUEST_LOCATION = 0;
        public Context storedContext;
        private Handler refreshDialogHandler;
        private Location currentLocation;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onCreate(Bundle savedInstanceState) {

            ((YourLocalWeather)getApplication()).applyTheme(this);
            super.onCreate(savedInstanceState);


            locationsDbHelper = LocationsDbHelper.getInstance(this);
            setContentView(R.layout.activity_main_weather_final);

            weatherConditionsIcons();
            initializeTextView();
            initializeWeatherReceiver();

            connectionDetector = new ConnectionDetector(MainWeatherAppActivity.this);
            setTitle( R.string.label_activity_main);

            /**
             * Configure SwipeRefreshLayout
             */

            mSwipeRefresh = (SwipeRefreshLayout) findViewById(R.id.main_swipe_refresh);
            int top_to_padding = 150;
            mSwipeRefresh.setProgressViewOffset(false, 0, top_to_padding);
            mSwipeRefresh.setColorSchemeResources(R.color.swipe_red, R.color.swipe_green,
                    R.color.swipe_blue);
            mSwipeRefresh.setOnRefreshListener(swipeRefreshListener);

            NestedScrollView main_scroll_view = (NestedScrollView) findViewById(R.id.main_scroll_view);


            main_scroll_view.setOnTouchListener(new ActivityTransitionTouchListener(
                    null,
                    WeatherForecastActivity.class, this));

            /**
             * Share weather fab
             */

            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            this.storedContext = this;
            fab.setOnClickListener(fabListener);
            checkSettingsAndPermisions();
            Intent intentToStartUpdate = new Intent("com.example.commontask.action.START_ALARM_SERVICE");
            intentToStartUpdate.setPackage("com.example.commontask");
            startService(intentToStartUpdate);
        }

        @Override
        public void onResume() {
            super.onResume();

            currentLocation = locationsDbHelper.getLocationById(AppPreference.getCurrentLocationId(this));
            if (currentLocation == null) {
                currentLocation = locationsDbHelper.getLocationByOrderId(0);
            }
            switchToNextLocationWhenCurrentIsAutoAndIsDisabled();
            if (mToolbarMenu != null) {
                if ((currentLocation.getOrderId() == 0) && !currentLocation.isEnabled()) {
                    mToolbarMenu.findItem(R.id.main_menu_refresh).setVisible(false);
                } else {
                    mToolbarMenu.findItem(R.id.main_menu_refresh).setVisible(true);
                }
                Location autoLocation = locationsDbHelper.getLocationByOrderId(0);
                if (!autoLocation.isEnabled()) {
                    mToolbarMenu.findItem(R.id.main_menu_detect_location).setVisible(false);
                } else {
                    mToolbarMenu.findItem(R.id.main_menu_detect_location).setVisible(true);
                }
            }
            AppPreference.setCurrentLocationId(this, currentLocation.getId());
            checkSettingsAndPermisions();
            preLoadWeather();
            mAppBarLayout.addOnOffsetChangedListener(this);
            LocalBroadcastManager.getInstance(this).registerReceiver(mWeatherUpdateReceiver,
                    new IntentFilter(
                            CurrentWeatherService.ACTION_WEATHER_UPDATE_RESULT));

        }

        @Override
        protected void onPause() {
            super.onPause();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            mAppBarLayout.removeOnOffsetChangedListener(this);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mWeatherUpdateReceiver);
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            this.mToolbarMenu = menu;
            MenuInflater menuInflater = getMenuInflater();
            menuInflater.inflate(R.menu.activity_main_menu, menu);
            Location autoLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!autoLocation.isEnabled()) {
                menu.findItem(R.id.main_menu_detect_location).setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.main_menu_refresh:
                    if (connectionDetector.isNetworkAvailableAndConnected()) {
                        locationsDbHelper.updateLastUpdatedAndLocationSource(
                                currentLocation.getId(),
                                System.currentTimeMillis(),
                                "-");
                        currentLocation = locationsDbHelper.getLocationById(currentLocation.getId());
                        Intent intent = new Intent(this, CurrentWeatherService.class);
                        intent.putExtra("updateSource", "MAIN");
                        intent.putExtra("location", currentLocation);
                        startService(intent);
                        setUpdateButtonState(true);
                    } else {
                        Toast.makeText(MainWeatherAppActivity.this,
                                R.string.connection_not_found,
                                Toast.LENGTH_SHORT).show();
                        setUpdateButtonState(false);
                    }
                    return true;
                case R.id.main_menu_detect_location:
                    requestLocation();
                    return true;
                case R.id.main_menu_search_city:
                    Intent intent = new Intent(MainWeatherAppActivity.this, LocationsActivity.class);
                    startActivity(intent);
                    return true;
            }

            return super.onOptionsItemSelected(item);
        }

        public void switchLocation(View arg0) {
            int newLocationOrderId = 1 + currentLocation.getOrderId();
            currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);

            if (currentLocation == null) {
                newLocationOrderId = 0;
                currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);
                if ((currentLocation.getOrderId() == 0) && !currentLocation.isEnabled() && (locationsDbHelper.getAllRows().size() > 1)) {
                    newLocationOrderId++;
                    currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);
                }
            }

            AppPreference.setCurrentLocationId(this, currentLocation.getId());
            localityView.setText(Utils.getCityAndCountry(this, newLocationOrderId));
            preLoadWeather();
        }

        private SwipeRefreshLayout.OnRefreshListener swipeRefreshListener =
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        isNetworkAvailable = connectionDetector.isNetworkAvailableAndConnected();
                        if (isNetworkAvailable) {
                            locationsDbHelper.updateLastUpdatedAndLocationSource(
                                    currentLocation.getId(),
                                    System.currentTimeMillis(),
                                    "-");
                            currentLocation = locationsDbHelper.getLocationById(currentLocation.getId());
                            Intent intent = new Intent(MainWeatherAppActivity.this, CurrentWeatherService.class);
                            intent.putExtra("updateSource", "MAIN");
                            intent.putExtra("location", currentLocation);
                            startService(intent);
                        } else {
                            Toast.makeText(MainWeatherAppActivity.this,
                                    R.string.connection_not_found,
                                    Toast.LENGTH_SHORT).show();
                            mSwipeRefresh.setRefreshing(false);
                        }
                    }
                };

        private void switchToNextLocationWhenCurrentIsAutoAndIsDisabled() {
            if ((currentLocation.getOrderId() == 0) && !currentLocation.isEnabled() && (locationsDbHelper.getAllRows().size() > 1)) {
                currentLocation = locationsDbHelper.getLocationByOrderId(1);
            }
        }

        private void preLoadWeather() {
            final CurrentWeatherDbHelper currentWeatherDbHelper = CurrentWeatherDbHelper.getInstance(this);

            if (currentLocation == null) {
                return;
            }

            CurrentWeatherDbHelper.WeatherRecord weatherRecord = currentWeatherDbHelper.getWeather(currentLocation.getId());

            if (weatherRecord == null) {
                mTemperatureView.setText(getString(R.string.temperature_with_degree,""));
                String temperatureTypeFromPreferences = PreferenceManager.getDefaultSharedPreferences(this).getString(
                        Constants.KEY_PREF_TEMPERATURE_TYPE, "measured_only");
                if ("measured_only".equals(temperatureTypeFromPreferences) ||
                        "appearance_only".equals(temperatureTypeFromPreferences)) {
                    secondTemperatureView.setVisibility(View.GONE);
                    iconSecondTemperatureView.setVisibility(View.GONE);
                } else {
                    secondTemperatureView.setVisibility(View.VISIBLE);
                    iconSecondTemperatureView.setVisibility(View.VISIBLE);
                    secondTemperatureView.setText(getString(R.string.label_apparent_temperature,""));
                }
                mDescriptionView.setText(R.string.location_not_found);
                mLastUpdateView.setText(getString(R.string.last_update_label, ""));
                mHumidityView.setText(getString(R.string.humidity_label,
                        "",
                        ""));
                mPressureView.setText(getString(R.string.pressure_label,
                        "",
                        ""));
                mWindSpeedView.setText(getString(R.string.wind_label,
                        "",
                        ""));
                mCloudinessView.setText(getString(R.string.cloudiness_label,
                        "",
                        ""));
                mSunriseView.setText(getString(R.string.sunrise_label, ""));
                mSunsetView.setText(getString(R.string.sunset_label, ""));
                return;
            }

            Weather weather = weatherRecord.getWeather();

            String lastUpdate = Utils.setLastUpdateTime(this, weatherRecord.getLastUpdatedTime(), currentLocation.getLocationSource());
            windWithUnit = AppPreference.getWindWithUnit(this, weather.getWindSpeed());
            WindWithUnit pressure = AppPreference.getPressureWithUnit(this, weather.getPressure());
            String sunrise = Utils.unixTimeToFormatTime(this, weather.getSunrise());
            String sunset = Utils.unixTimeToFormatTime(this, weather.getSunset());

            Utils.setWeatherIcon(mIconWeatherView, this, weatherRecord);
            mTemperatureView.setText(getString(R.string.temperature_with_degree,
                    TemperatureUtil.getTemperatureWithUnit(this, weather)));
            String secondTemperature = TemperatureUtil.getSecondTemperatureWithLabel(this, weather);
            if (secondTemperature != null) {
                secondTemperatureView.setText(secondTemperature);
                secondTemperatureView.setVisibility(View.VISIBLE);
                iconSecondTemperatureView.setVisibility(View.VISIBLE);
            } else {
                secondTemperatureView.setVisibility(View.GONE);
                iconSecondTemperatureView.setVisibility(View.GONE);
            }
            mDescriptionView.setText(Utils.getWeatherDescription(this, weather));
            mLastUpdateView.setText(getString(R.string.last_update_label, lastUpdate));
            mHumidityView.setText(getString(R.string.humidity_label,
                    String.valueOf(weather.getHumidity()),
                    mPercentSign));
            mPressureView.setText(getString(R.string.pressure_label,
                    pressure.getWindSpeed(0),
                    pressure.getWindUnit()));
            mWindSpeedView.setText(getString(R.string.wind_label,
                    windWithUnit.getWindSpeed(1),
                    windWithUnit.getWindUnit()));
            mCloudinessView.setText(getString(R.string.cloudiness_label,
                    String.valueOf(weather.getClouds()),
                    mPercentSign));
            mSunriseView.setText(getString(R.string.sunrise_label, sunrise));
            mSunsetView.setText(getString(R.string.sunset_label, sunset));
            localityView.setText(Utils.getCityAndCountry(this, currentLocation.getOrderId()));
        }

        private void initializeTextView() {
            /**
             * Create typefaces from Asset
             */
            Typeface weatherFontIcon = Typeface.createFromAsset(this.getAssets(),
                    "fonts/weathericons-regular-webfont.ttf");
            Typeface robotoThin = Typeface.createFromAsset(this.getAssets(),
                    "fonts/Roboto-Thin.ttf");
            Typeface robotoLight = Typeface.createFromAsset(this.getAssets(),
                    "fonts/Roboto-Light.ttf");

            mIconWeatherView = (ImageView) findViewById(R.id.main_weather_icon);
            mTemperatureView = (TextView) findViewById(R.id.main_temperature);
            secondTemperatureView = (TextView) findViewById(R.id.main_second_temperature);
            mDescriptionView = (TextView) findViewById(R.id.main_description);
            mPressureView = (TextView) findViewById(R.id.main_pressure);
            mHumidityView = (TextView) findViewById(R.id.main_humidity);
            mWindSpeedView = (TextView) findViewById(R.id.main_wind_speed);
            mCloudinessView = (TextView) findViewById(R.id.main_cloudiness);
            mLastUpdateView = (TextView) findViewById(R.id.main_last_update);
            mSunriseView = (TextView) findViewById(R.id.main_sunrise);
            mSunsetView = (TextView) findViewById(R.id.main_sunset);
            mAppBarLayout = (AppBarLayout) findViewById(R.id.main_app_bar);
            localityView = (TextView) findViewById(R.id.main_locality);

            mTemperatureView.setTypeface(robotoThin);
            secondTemperatureView.setTypeface(robotoLight);
            mWindSpeedView.setTypeface(robotoLight);
            mHumidityView.setTypeface(robotoLight);
            mPressureView.setTypeface(robotoLight);
            mCloudinessView.setTypeface(robotoLight);
            mSunriseView.setTypeface(robotoLight);
            mSunsetView.setTypeface(robotoLight);

            /**
             * Initialize and configure weather icons
             */
            iconSecondTemperatureView = (TextView) findViewById(R.id.main_second_temperature_icon);
            iconSecondTemperatureView.setTypeface(weatherFontIcon);
            iconSecondTemperatureView.setText(iconSecondTemperature);
            mIconWindView = (TextView) findViewById(R.id.main_wind_icon);
            mIconWindView.setTypeface(weatherFontIcon);
            mIconWindView.setText(mIconWind);
            mIconHumidityView = (TextView) findViewById(R.id.main_humidity_icon);
            mIconHumidityView.setTypeface(weatherFontIcon);
            mIconHumidityView.setText(mIconHumidity);
            mIconPressureView = (TextView) findViewById(R.id.main_pressure_icon);
            mIconPressureView.setTypeface(weatherFontIcon);
            mIconPressureView.setText(mIconPressure);
            mIconCloudinessView = (TextView) findViewById(R.id.main_cloudiness_icon);
            mIconCloudinessView.setTypeface(weatherFontIcon);
            mIconCloudinessView.setText(mIconCloudiness);
            mIconSunriseView = (TextView) findViewById(R.id.main_sunrise_icon);
            mIconSunriseView.setTypeface(weatherFontIcon);
            mIconSunriseView.setText(mIconSunrise);
            mIconSunsetView = (TextView) findViewById(R.id.main_sunset_icon);
            mIconSunsetView.setTypeface(weatherFontIcon);
            mIconSunsetView.setText(mIconSunset);
        }

        private void weatherConditionsIcons() {
            mIconWind = getString(R.string.icon_wind);
            mIconHumidity = getString(R.string.icon_humidity);
            mIconPressure = getString(R.string.icon_barometer);
            mIconCloudiness = getString(R.string.icon_cloudiness);
            mPercentSign = getString(R.string.percent_sign);
            mIconSunrise = getString(R.string.icon_sunrise);
            mIconSunset = getString(R.string.icon_sunset);
            iconSecondTemperature = getString(R.string.icon_thermometer);
        }

        private void setUpdateButtonState(boolean isUpdate) {
            if (mToolbarMenu != null) {
                MenuItem updateItem = mToolbarMenu.findItem(R.id.main_menu_refresh);
                ProgressBar progressUpdate = (ProgressBar) findViewById(R.id.toolbar_progress_bar);
                if (isUpdate) {
                    updateItem.setVisible(false);
                    progressUpdate.setVisibility(View.VISIBLE);
                } else {
                    progressUpdate.setVisibility(View.GONE);
                    updateItem.setVisible(true);
                }
            }
        }

        private void initializeWeatherReceiver() {
            mWeatherUpdateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ((mProgressDialog != null) && (refreshDialogHandler != null)) {
                        refreshDialogHandler.post(new Runnable() {
                            public void run() {
                                if (mProgressDialog != null) {
                                    mProgressDialog.dismiss();
                                }
                            }
                        });
                    }
                    switch (intent.getStringExtra(CurrentWeatherService.ACTION_WEATHER_UPDATE_RESULT)) {
                        case CurrentWeatherService.ACTION_WEATHER_UPDATE_OK:
                            mSwipeRefresh.setRefreshing(false);
                            setUpdateButtonState(false);
                            preLoadWeather();
                            break;
                        case CurrentWeatherService.ACTION_WEATHER_UPDATE_FAIL:
                            mSwipeRefresh.setRefreshing(false);
                            setUpdateButtonState(false);
                            Toast.makeText(MainWeatherAppActivity.this,
                                    getString(R.string.toast_parse_error),
                                    Toast.LENGTH_SHORT).show();
                    }
                }
            };
        }

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            mSwipeRefresh.setEnabled(verticalOffset == 0);
        }

        FloatingActionButton.OnClickListener fabListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CurrentWeatherDbHelper currentWeatherDbHelper = CurrentWeatherDbHelper.getInstance(MainWeatherAppActivity.this);
                CurrentWeatherDbHelper.WeatherRecord currentWeatherRecord = currentWeatherDbHelper.getWeather(currentLocation.getId());

                if (currentWeatherRecord == null) {
                    Toast.makeText(MainWeatherAppActivity.this,
                            getString(R.string.current_weather_has_not_been_fetched),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Weather weather = currentWeatherRecord.getWeather();

                String temperatureWithUnit = TemperatureUtil.getTemperatureWithUnit(
                        MainWeatherAppActivity.this,
                        weather);
                windWithUnit = AppPreference.getWindWithUnit(
                        MainWeatherAppActivity.this,
                        weather.getWindSpeed());
                String description;
                String sunrise;
                String sunset;
                description = Utils.getWeatherDescription(MainWeatherAppActivity.this, weather);
                sunrise = Utils.unixTimeToFormatTime(MainWeatherAppActivity.this, weather.getSunrise());
                sunset = Utils.unixTimeToFormatTime(MainWeatherAppActivity.this, weather.getSunset());
                String weatherDescription = "City: " + getCityNameFromAddress() +
                        "\nTemperature: " + temperatureWithUnit +
                        "\nDescription: " + description +
                        "\nWind: " + windWithUnit.getWindSpeed(1) + " " + windWithUnit.getWindUnit() +
                        "\nSunrise: " + sunrise +
                        "\nSunset: " + sunset;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, weatherDescription);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(Intent.createChooser(shareIntent, "Share Weather"));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainWeatherAppActivity.this,
                            "Communication app not found",
                            Toast.LENGTH_LONG).show();
                }
            }
        };

        private String getCityNameFromAddress() {
            return (currentLocation.getAddress() != null)?currentLocation.getAddress().getLocality():getString(R.string.location_not_found);
        }

        private void detectLocation() {
            if (WidgetRefreshIconService.isRotationActive) {
                return;
            }
            mProgressDialog = new ProgressDialog(MainWeatherAppActivity.this);
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
                        appendLog(MainWeatherAppActivity.this, TAG, "Cancellation error", e);
                    }
                }
            });

            updateNetworkLocation();
            mProgressDialog.show();
            refreshDialogHandler = new Handler(Looper.getMainLooper());
        }

        private volatile boolean permissionsAndSettingsRequested = false;

        public boolean checkPermissionsSettingsAndShowAlert() {
            if (permissionsAndSettingsRequested) {
                return true;
            }
            permissionsAndSettingsRequested = true;
            Location autoUpdateLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!autoUpdateLocation.isEnabled()) {
                return true;
            }
            AlertDialog.Builder settingsAlert = new AlertDialog.Builder(MainWeatherAppActivity.this);
            settingsAlert.setTitle(R.string.alertDialog_location_permission_title);

            LocationManager locationManager = (LocationManager) getBaseContext().getSystemService(Context.LOCATION_SERVICE);
            boolean isGPSEnabled = locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)
                    && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            String geocoder = AppPreference.getLocationGeocoderSource(getBaseContext());

            boolean gpsNotEnabled = !isGPSEnabled && AppPreference.isGpsEnabledByPreferences(getBaseContext());
            boolean networkNotEnabled = !isNetworkEnabled && "location_geocoder_system".equals(geocoder);

            if (gpsNotEnabled || networkNotEnabled) {
                settingsAlert.setMessage(R.string.alertDialog_location_permission_message_location_phone_settings);
                settingsAlert.setPositiveButton(R.string.alertDialog_location_permission_positiveButton_settings,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                permissionsAndSettingsRequested = false;
                                Intent goToSettings = new Intent(
                                        Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(goToSettings);
                            }
                        });
            } else {
                List<String> permissions = new ArrayList<>();
                StringBuilder notificationMessage = new StringBuilder();
                if (AppPreference.isGpsEnabledByPreferences(getBaseContext()) &&
                        isGPSEnabled &&
                        ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    notificationMessage.append(getString(R.string.alertDialog_location_permission_message_location_phone_settings) + "\n\n");
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if ("location_geocoder_local".equals(geocoder) && ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    notificationMessage.append(getString(R.string.alertDialog_location_permission_message_location_phone_permission));
                    permissions.add(Manifest.permission.READ_PHONE_STATE);
                } else if (isNetworkEnabled && "location_geocoder_system".equals(geocoder) && ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    notificationMessage.append(getString(R.string.alertDialog_location_permission_message_location_network_permission));
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                }
                if (permissions.isEmpty()) {
                    return true;
                }
                settingsAlert.setMessage(notificationMessage.toString());
                final String[] permissionsArray = permissions.toArray(new String[permissions.size()]);
                final Activity MainWeatherAppActivity = this;
                settingsAlert.setPositiveButton(R.string.alertDialog_location_permission_positiveButton_permissions,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainWeatherAppActivity,
                                        permissionsArray,
                                        123);
                            }
                        });
            }

            settingsAlert.setNegativeButton(R.string.alertDialog_location_permission_negativeButton,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            permissionsAndSettingsRequested = false;
                            dialog.cancel();
                        }
                    });

            settingsAlert.show();
            return false;
        }

        private volatile boolean initialGuideCompleted;
        private volatile int initialGuidePage;
        private int selectedUpdateLocationStrategy;
        private int selectedLocationAndAddressSourceStrategy;
        private int selectedWakeupStrategyStrategy;
        private int selectedCacheLocationStrategy;

        private void checkSettingsAndPermisions() {
            if (initialGuideCompleted) {
                return;
            }
            checkAndShowInitialGuide();
        }

        private void checkAndShowInitialGuide() {
            int initialGuideVersion = PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                    .getInt(Constants.APP_INITIAL_GUIDE_VERSION, 0);
            if (initialGuideVersion > 0) {
                initialGuideCompleted = true;
                return;
            }
            if (initialGuidePage > 0) {
                return;
            }
            initialGuidePage = 1;
            showInitialGuidePage(initialGuidePage);
        }

    @Override
    public void onBackPressed() {
        if (isNavDrawerOpen()) {
            closeNavDraw();
        } else {
            startActivity(new Intent(MainWeatherAppActivity.this, MainActivityCalendar.class));
        }
    }

        private void showInitialGuidePage(int pageNumber) {
            final AlertDialog.Builder settingsAlert = new AlertDialog.Builder(MainWeatherAppActivity.this);
            switch (pageNumber) {
                case 1:
                    settingsAlert.setTitle(R.string.initial_guide_title_1);
                    settingsAlert.setMessage(R.string.initial_guide_paragraph_1);
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_close);
                    break;
                case 2:
                    settingsAlert.setTitle(R.string.initial_guide_title_2);
                    selectedUpdateLocationStrategy = 1;
                    settingsAlert.setSingleChoiceItems(R.array.location_update_strategy, selectedUpdateLocationStrategy,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int selectedOption) {
                                    selectedUpdateLocationStrategy = selectedOption;
                                    if (selectedOption == 0) {
                                        initialGuidePage = 8; //skip to the last page
                                    }
                                }
                            });
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 3:
                    settingsAlert.setTitle(R.string.initial_guide_title_3);
                    settingsAlert.setMessage(R.string.initial_guide_paragraph_3);
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 4:
                    settingsAlert.setTitle(R.string.initial_guide_title_4);
                    selectedLocationAndAddressSourceStrategy = 0;
                    settingsAlert.setSingleChoiceItems(R.array.location_geocoder_source_entries, selectedLocationAndAddressSourceStrategy,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int selectedOption) {
                                    selectedLocationAndAddressSourceStrategy = selectedOption;
                                }
                            });
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 5:
                    settingsAlert.setTitle(R.string.initial_guide_title_5);
                    settingsAlert.setMessage(R.string.initial_guide_paragraph_5);
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 6:
                    settingsAlert.setTitle(R.string.initial_guide_title_6);
                    selectedWakeupStrategyStrategy = 2;
                    settingsAlert.setSingleChoiceItems(R.array.wake_up_strategy_entries, selectedWakeupStrategyStrategy,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int selectedOption) {
                                    selectedWakeupStrategyStrategy = selectedOption;
                                }
                            });
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 7:
                    settingsAlert.setTitle(R.string.initial_guide_title_7);
                    settingsAlert.setMessage(R.string.initial_guide_paragraph_7);
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 8:
                    settingsAlert.setTitle(R.string.initial_guide_title_8);
                    selectedCacheLocationStrategy = 1;
                    settingsAlert.setSingleChoiceItems(R.array.location_cache_entries, selectedCacheLocationStrategy,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int selectedOption) {
                                    selectedCacheLocationStrategy = selectedOption;
                                }
                            });
                    setNextButton(settingsAlert, R.string.initial_guide_next);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
                case 9:
                    settingsAlert.setTitle(R.string.initial_guide_title_9);
                    settingsAlert.setMessage(R.string.initial_guide_paragraph_9);
                    setNextButton(settingsAlert, R.string.initial_guide_finish);
                    setPreviousButton(settingsAlert, R.string.initial_guide_previous);
                    break;
            }
            settingsAlert.show();
        }

        private void setNextButton(AlertDialog.Builder settingsAlert, int labelId) {
            settingsAlert.setPositiveButton(labelId,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            initialGuidePage++;
                            if (initialGuidePage > 9) {
                                initialGuideCompleted = true;
                                permissionsAndSettingsRequested = false;
                                saveInitialPreferences();
                                checkPermissionsSettingsAndShowAlert();
                            } else {
                                showInitialGuidePage(initialGuidePage);
                            }
                        }
                    });
        }

        private void setPreviousButton(AlertDialog.Builder settingsAlert, final int labelId) {
            settingsAlert.setNegativeButton(labelId,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            if (labelId == R.string.initial_guide_close) {
                                closeInitialGuideAndCheckPermission();
                            } else {
                                initialGuidePage--;
                                showInitialGuidePage(initialGuidePage);
                            }
                        }
                    });
        }

        private void closeInitialGuideAndCheckPermission() {
            permissionsAndSettingsRequested = false;
            SharedPreferences.Editor preferences = PreferenceManager.getDefaultSharedPreferences(this).edit();
            preferences.putInt(Constants.APP_INITIAL_GUIDE_VERSION, 1);
            preferences.apply();
            initialGuideCompleted = true;
            checkPermissionsSettingsAndShowAlert();
        }

        private void saveInitialPreferences() {
            SharedPreferences.Editor preferences = PreferenceManager.getDefaultSharedPreferences(this).edit();

            boolean gpsEnabled = true;
            boolean locationUpdateEnabled = true;
            switch (selectedUpdateLocationStrategy) {
                case 0: locationUpdateEnabled = false; gpsEnabled = false; break;
                case 1: locationUpdateEnabled = true; gpsEnabled = true; break;
                case 2: locationUpdateEnabled = true; gpsEnabled = false; break;
            }
            LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(this);
            Location autoLocation = locationsDbHelper.getLocationByOrderId(0);
            locationsDbHelper.updateEnabled(autoLocation.getId(), locationUpdateEnabled);
            preferences.putBoolean(Constants.KEY_PREF_LOCATION_GPS_ENABLED, gpsEnabled);

            String selectedWakeupStrategyStrategyString = "nowakeup";
            switch (selectedWakeupStrategyStrategy) {
                case 0: selectedWakeupStrategyStrategyString = "nowakeup"; break;
                case 1: selectedWakeupStrategyStrategyString = "wakeuppartial"; break;
                case 2: selectedWakeupStrategyStrategyString = "wakeupfull"; break;
            }
            preferences.putString(Constants.KEY_WAKE_UP_STRATEGY, selectedWakeupStrategyStrategyString);

            String selectedLocationAndAddressSourceStrategyString = "location_geocoder_local";
            switch (selectedLocationAndAddressSourceStrategy) {
                case 0: selectedLocationAndAddressSourceStrategyString = "location_geocoder_local"; break;
                case 1: selectedLocationAndAddressSourceStrategyString = "location_geocoder_system"; break;
            }
            preferences.putString(Constants.KEY_PREF_LOCATION_GEOCODER_SOURCE, selectedLocationAndAddressSourceStrategyString);

            boolean selectedCacheLocationStrategyBoolean = false;
            switch (selectedCacheLocationStrategy) {
                case 0: selectedCacheLocationStrategyBoolean = false; break;
                case 1: selectedCacheLocationStrategyBoolean = true; break;
            }
            preferences.putBoolean(Constants.APP_SETTINGS_LOCATION_CACHE_ENABLED, selectedCacheLocationStrategyBoolean);

            preferences.putInt(Constants.APP_INITIAL_GUIDE_VERSION, 1);
            preferences.apply();
        }

        private void updateNetworkLocation() {
            Intent startLocationUpdateIntent = new Intent("android.intent.action.START_LOCATION_AND_WEATHER_UPDATE");
            startLocationUpdateIntent.setPackage("com.example.commontask");
            startLocationUpdateIntent.putExtra("updateSource", "MAIN");
            startLocationUpdateIntent.putExtra("location", currentLocation);
            storedContext.startService(startLocationUpdateIntent);
        }

        private void requestLocation() {
            if (checkPermissionsSettingsAndShowAlert()) {
                detectLocation();
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            switch (requestCode) {
                case REQUEST_LOCATION:
                    if (PermissionUtil.verifyPermissions(grantResults)) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.permission_available_location, Snackbar.LENGTH_SHORT).show();
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), R.string.permission_not_granted, Snackbar.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                    break;
            }
        }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LanguageUtil.setLanguage(this, PreferenceUtil.getLanguage(this));
    }


}

