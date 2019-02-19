package com.example.commontask;


import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.obsez.android.lib.filechooser.ChooserDialog;

import com.example.commontask.WeatherSettingsActivity;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.ReverseGeocodingCacheContract;
import com.example.commontask.model.ReverseGeocodingCacheDbHelper;
import com.example.commontask.service.CurrentWeatherService;
import com.example.commontask.service.NotificationService;
import com.example.commontask.service.ReconciliationDbService;
import com.example.commontask.utils.ApiKeys;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.Constants;
import com.example.commontask.utils.GraphUtils;
import com.example.commontask.utils.LanguageUtil;
import com.example.commontask.utils.LogToFile;
import com.example.commontask.utils.NotificationUtils;
import com.example.commontask.utils.PreferenceUtil;
import com.example.commontask.utils.WidgetUtils;
import org.yaml.snakeyaml.scanner.Constant;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class WeatherSettingsActivity extends AppCompatPreferenceActivity {

    private static final String TAG = "SettingsActivity";

    public static final String KEY_DEBUG_FILE = "debug.log.file";
    public static final String KEY_DEBUG_TO_FILE = "debug.to.file";
    public static final String KEY_DEBUG_FILE_LASTING_HOURS = "debug.file.lasting.hours";

    private static SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", new Locale("en"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((YourLocalWeather) getApplication()).applyTheme(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }
        super.onCreate(savedInstanceState);
        setupActionBar();

        int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());
        getListView().setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        super.onBuildHeaders(target);
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageUtil.setLanguage(base, PreferenceUtil.getLanguage(base)));
    }

    private void setupActionBar() {
        getLayoutInflater().inflate(R.layout.activity_settings, (ViewGroup)findViewById(android.R.id.content));
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || UnitsPreferenceFragment.class.getName().equals(fragmentName)
                || UpdatesPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName)
                || PowerSavePreferenceFragment.class.getName().equals(fragmentName)
                || DebugOptionsPreferenceFragment.class.getName().equals(fragmentName)
                || WidgetPreferenceFragment.class.getName().equals(fragmentName)
                || AboutPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private final String[] SUMMARIES_TO_UPDATE = {
                Constants.KEY_PREF_HIDE_DESCRIPTION,
                Constants.PREF_LANGUAGE,
                Constants.PREF_THEME,
                Constants.KEY_PREF_WEATHER_ICON_SET,
                Constants.KEY_PREF_OPEN_WEATHER_MAP_API_KEY
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general_weather);

            EditTextPreference openWeatherMapApiKey =
                    (EditTextPreference) findPreference(Constants.KEY_PREF_OPEN_WEATHER_MAP_API_KEY);
            openWeatherMapApiKey.setSummary(ApiKeys.getOpenweathermapApiKeyForPreferences(getActivity()));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        private void entrySummary(String key) {
            ListPreference preference = (ListPreference) findPreference(key);
            if (preference == null) {
                return;
            }
            preference.setSummary(preference.getEntry());
        }

        private void updateSummary(String key, boolean changing) {
            switch (key) {
                case Constants.KEY_PREF_HIDE_DESCRIPTION:
                    if (changing) {
                        Intent intent = new Intent(Constants.ACTION_FORCED_APPWIDGET_UPDATE);
                        intent.setPackage("com.example.commontask");
                        getActivity().sendBroadcast(intent);
                    }
                    break;
                case Constants.PREF_LANGUAGE:
                    entrySummary(key);
                    if (changing) {
                        String newLocale = PreferenceUtil.getLanguage(getActivity().getApplicationContext());
                        LanguageUtil.setLanguage(getActivity().getApplication(), newLocale);
                        updateLocationsLocale(newLocale);
                        WidgetUtils.updateWidgets(getActivity());
                        DialogFragment dialog = new SettingsAlertDialog().newInstance(R.string.update_locale_dialog_message);
                        dialog.show(getActivity().getFragmentManager(), "restartApp");
                    }
                    break;
                case Constants.PREF_THEME:
                    entrySummary(key);
                    if (changing) {
                        YourLocalWeather app = (YourLocalWeather) getActivity().getApplication();
                        app.reloadTheme();
                        app.applyTheme(getActivity());
                        restartApp(getActivity());
                    }
                    break;
                case Constants.KEY_PREF_WEATHER_ICON_SET:
                    entrySummary(key);
                    break;
                case Constants.KEY_PREF_OPEN_WEATHER_MAP_API_KEY:
                    findPreference(key).setSummary(ApiKeys.getOpenweathermapApiKeyForPreferences(getActivity()));
                    checkAndDeleteLocations();
                    break;
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(key, true);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void updateLocationsLocale(String newLocale) {
            LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(getActivity());
            for (Location location: locationsDbHelper.getAllRows()) {
                locationsDbHelper.updateLocale(location.getId(), newLocale);
            }
        }

        private void setDefaultValues() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (!preferences.contains(Constants.PREF_LANGUAGE)) {
                preferences.edit().putString(Constants.PREF_LANGUAGE, Resources.getSystem().getConfiguration().locale.getLanguage()).apply();
                entrySummary(Constants.PREF_LANGUAGE);
            }
        }

        private void updateSummaries() {
            for (String key : SUMMARIES_TO_UPDATE) {
                updateSummary(key, false);
            }
        }

        private void checkAndDeleteLocations() {
            LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(getActivity());
            List<Location> allLocations = locationsDbHelper.getAllRows();
            if (allLocations.size() <= ApiKeys.getAvailableLocations(getActivity())) {
                return;
            }
            for (Location location: allLocations) {
                if (location.getOrderId() >= ApiKeys.getAvailableLocations(getActivity())) {
                    locationsDbHelper.deleteRecordFromTable(location);
                }
            }
            Intent reconciliationService = new Intent(getActivity(), ReconciliationDbService.class);
            reconciliationService.putExtra("force", true);
            WidgetUtils.startBackgroundService(
                    getActivity(),
                    reconciliationService);
        }
    }

    public static class UnitsPreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private final String[] SUMMARIES_TO_UPDATE = {
                Constants.KEY_PREF_DATE_STYLE,
                Constants.KEY_PREF_TIME_STYLE,
                Constants.KEY_PREF_TEMPERATURE_TYPE,
                Constants.KEY_PREF_TEMPERATURE_UNITS,
                Constants.KEY_PREF_WIND_UNITS,
                Constants.KEY_PREF_WIND_DIRECTION,
                Constants.KEY_PREF_RAIN_SNOW_UNITS,
                Constants.KEY_PREF_PRESSURE_UNITS
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_units);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        private void entrySummary(String key) {
            ListPreference preference = (ListPreference) findPreference(key);
            if (preference == null) {
                return;
            }
            preference.setSummary(preference.getEntry());
        }

        private void updateSummary(String key, boolean changing) {
            entrySummary(key);
            if (changing) {
                Intent intent = new Intent(Constants.ACTION_FORCED_APPWIDGET_UPDATE);
                intent.setPackage("com.example.commontask");
                getActivity().sendBroadcast(intent);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(key, true);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void updateSummaries() {
            for (String key : SUMMARIES_TO_UPDATE) {
                updateSummary(key, false);
            }
        }
    }

    public static class UpdatesPreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private final String[] SUMMARIES_TO_UPDATE = {
                Constants.KEY_PREF_LOCATION_AUTO_UPDATE_PERIOD,
                Constants.KEY_PREF_LOCATION_UPDATE_PERIOD,
                Constants.KEY_PREF_LOCATION_GEOCODER_SOURCE
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_updates);

            SensorManager senSensorManager  = (SensorManager) getActivity()
                    .getSystemService(Context.SENSOR_SERVICE);
            Sensor senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            boolean deviceHasAccelerometer = senSensorManager.registerListener(
                    sensorListener,
                    senAccelerometer,
                    SensorManager.SENSOR_DELAY_FASTEST);
            senSensorManager.unregisterListener(sensorListener);

            Preference updateWidgetUpdatePref = findPreference(Constants.KEY_PREF_LOCATION_AUTO_UPDATE_PERIOD);
            ListPreference updateListPref = (ListPreference) updateWidgetUpdatePref;
            int accIndex = updateListPref.findIndexOfValue("0");

            if (!deviceHasAccelerometer) {
                CharSequence[] entries = updateListPref.getEntries();
                CharSequence[] newEntries = new CharSequence[entries.length - 1];
                int i = 0;
                int j = 0;
                for (CharSequence entry : entries) {
                    if (i != accIndex) {
                        newEntries[j] = entries[i];
                        j++;
                    }
                    i++;
                }
                updateListPref.setEntries(newEntries);
                if (updateListPref.getValue() == null) {
                    updateListPref.setValueIndex(updateListPref.findIndexOfValue("60") - 1);
                }
            } else if (updateListPref.getValue() == null) {
                updateListPref.setValueIndex(accIndex);
            }
            LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(getActivity());
            List<Location> availableLocations = locationsDbHelper.getAllRows();
            boolean oneNoautoLocationAvailable = false;
            for (Location location: availableLocations) {
                if (location.getOrderId() != 0) {
                    oneNoautoLocationAvailable = true;
                    break;
                }
            }
            if (!oneNoautoLocationAvailable) {
                ListPreference locationPreference = (ListPreference) findPreference("location_update_period_pref_key");
                locationPreference.setEnabled(false);
            }

            ListPreference locationAutoPreference = (ListPreference) findPreference("location_auto_update_period_pref_key");
            locationAutoPreference.setEnabled(locationsDbHelper.getLocationByOrderId(0).isEnabled());
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        private void entrySummary(String key) {
            ListPreference preference = (ListPreference) findPreference(key);
            if (preference == null) {
                return;
            }
            preference.setSummary(preference.getEntry());
        }

        private void updateSummary(String key, boolean changing) {
            entrySummary(key);
            switch (key) {
                case Constants.KEY_PREF_LOCATION_AUTO_UPDATE_PERIOD:
                case Constants.KEY_PREF_LOCATION_UPDATE_PERIOD:
                    if (changing) {
                        Intent intentToStartUpdate = new Intent("com.example.commontask.action.RESTART_ALARM_SERVICE");
                        intentToStartUpdate.setPackage("com.example.commontask");
                        getActivity().startService(intentToStartUpdate);
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(key, true);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void updateSummaries() {
            for (String key : SUMMARIES_TO_UPDATE) {
                updateSummary(key, false);
            }
        }

        private SensorEventListener sensorListener = new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
    }

    public static class NotificationPreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private final String[] SUMMARIES_TO_UPDATE = {
                Constants.KEY_PREF_INTERVAL_NOTIFICATION,
                Constants.KEY_PREF_NOTIFICATION_PRESENCE,
                Constants.KEY_PREF_NOTIFICATION_STATUS_ICON,
                Constants.KEY_PREF_NOTIFICATION_VISUAL_STYLE
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification_weather);

            final SwitchPreference notificationSwitch = (SwitchPreference) findPreference(
                    Constants.KEY_PREF_IS_NOTIFICATION_ENABLED);
            notificationSwitch.setOnPreferenceChangeListener(notificationListener);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        Preference.OnPreferenceChangeListener notificationListener =
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        boolean isEnabled = (boolean) o;
                        AppPreference.setNotificationEnabled(getActivity(), isEnabled);
                        Intent intentToStartUpdate = new Intent("com.example.commontask.action.RESTART_NOTIFICATION_ALARM_SERVICE");
                        intentToStartUpdate.setPackage("com.example.commontask");
                        getActivity().startService(intentToStartUpdate);
                        return true;
                    }
                };

        private void entrySummary(String key, boolean changing) {
            ListPreference preference = (ListPreference) findPreference(key);
            if (preference == null) {
                return;
            }
            preference.setSummary(preference.getEntry());

            if (Constants.KEY_PREF_NOTIFICATION_PRESENCE.equals(key)) {
                if ("permanent".equals(preference.getValue()) || "on_lock_screen".equals(preference.getValue())) {
                    SwitchPreference vibrate = (SwitchPreference) findPreference(Constants.KEY_PREF_VIBRATE);
                    vibrate.setEnabled(false);
                    vibrate.setChecked(false);
                } else {
                    SwitchPreference vibrate = (SwitchPreference) findPreference(Constants.KEY_PREF_VIBRATE);
                    vibrate.setEnabled(true);
                }
            }
            if ("permanent".equals(AppPreference.getNotificationPresence(getActivity()))) {
                NotificationUtils.weatherNotification(getActivity(),
                        NotificationUtils.getLocationForNotification(getActivity()).getId());
            }
            if ((Constants.KEY_PREF_NOTIFICATION_PRESENCE.equals(key)) && changing) {
                if (!"permanent".equals(preference.getValue())) {
                    NotificationManager notificationManager =
                            (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancelAll();
                }

            }
        }

        private void updateSummary(String key, boolean changing) {
            switch (key) {
                case Constants.KEY_PREF_INTERVAL_NOTIFICATION:
                    entrySummary(key, changing);
                    if (changing) {
                        Intent intentToStartUpdate = new Intent("com.example.commontask.action.RESTART_ALARM_SERVICE");
                        intentToStartUpdate.setPackage("com.example.commontask");
                        getActivity().startService(intentToStartUpdate);
                    }
                    break;
                case Constants.KEY_PREF_NOTIFICATION_PRESENCE:
                case Constants.KEY_PREF_NOTIFICATION_STATUS_ICON:
                case Constants.KEY_PREF_NOTIFICATION_VISUAL_STYLE:
                    entrySummary(key, changing);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(key, true);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void updateSummaries() {
            for (String key : SUMMARIES_TO_UPDATE) {
                updateSummary(key, false);
            }
        }
    }

    public static class PowerSavePreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private final String[] SUMMARIES_TO_UPDATE = {
                Constants.KEY_WAKE_UP_STRATEGY,
                Constants.KEY_PREF_LOCATION_GPS_ENABLED,
                Constants.APP_SETTINGS_LOCATION_CACHE_ENABLED
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_powersave);
            initLocationCache();
            initWakeUpStrategy();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        private void entrySummary(String key) {
            if (!Constants.KEY_PREF_LOCATION_GPS_ENABLED.equals(key)) {
                ListPreference preference = (ListPreference) findPreference(key);
                if (preference == null) {
                    return;
                }
                preference.setSummary(preference.getEntry());
            }
        }

        private void updateSummary(String key, boolean changing) {
            switch (key) {
                case Constants.KEY_PREF_LOCATION_GPS_ENABLED:
                    entrySummary(key);
                    break;
            }
        }

        private void updateSummaries() {
            for (String key : SUMMARIES_TO_UPDATE) {
                updateSummary(key, false);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary(key, true);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void initLocationCache() {

            Preference locationCacheEnabled = findPreference(Constants.APP_SETTINGS_LOCATION_CACHE_ENABLED);
            locationCacheEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    boolean enabled = (Boolean) value;
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    preferences.edit().putBoolean(Constants.APP_SETTINGS_LOCATION_CACHE_ENABLED, enabled).apply();
                    return true;
                }
            });

            Preference locationLasting = findPreference(Constants.APP_SETTINGS_LOCATION_CACHE_LASTING_HOURS);
            locationLasting.setSummary(
                    getLocationLastingLabel(Integer.parseInt(
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Constants.APP_SETTINGS_LOCATION_CACHE_LASTING_HOURS, "720"))
                    )
            );
            locationLasting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference locationLasting, Object value) {
                    String locationRowLastingHoursTxt = (String) value;
                    Integer locationRowLastingHours = Integer.valueOf(locationRowLastingHoursTxt);
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    preferences.edit().putString(Constants.APP_SETTINGS_LOCATION_CACHE_LASTING_HOURS, locationRowLastingHoursTxt).apply();
                    locationLasting.setSummary(getString(getLocationLastingLabel(locationRowLastingHours)));
                    return true;
                }
            });


            Preference button = findPreference("clear_cache_button");
            button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ReverseGeocodingCacheDbHelper mDbHelper = ReverseGeocodingCacheDbHelper.getInstance(preference.getContext());
                    SQLiteDatabase db = mDbHelper.getWritableDatabase();
                    mDbHelper.onUpgrade(db, 0, 0);
                    return true;
                }
            });

            Preference dbInfo = findPreference("db_info");
            dbInfo.setSummary(getDataFromCacheDB());
        }

        private int getLocationLastingLabel(int locationLastingValue) {
            int locationLastingLastingId;
            switch (locationLastingValue) {
                case 12:
                    locationLastingLastingId = R.string.location_cache_12_label;
                    break;
                case 24:
                    locationLastingLastingId = R.string.location_cache_24_label;
                    break;
                case 168:
                    locationLastingLastingId = R.string.location_cache_168_label;
                    break;
                case 2190:
                    locationLastingLastingId = R.string.location_cache_2190_label;
                    break;
                case 4380:
                    locationLastingLastingId = R.string.location_cache_4380_label;
                    break;
                case 8760:
                    locationLastingLastingId = R.string.location_cache_8760_label;
                    break;
                case 88888:
                    locationLastingLastingId = R.string.location_cache_88888_label;
                    break;
                case 720:
                default:
                    locationLastingLastingId = R.string.location_cache_720_label;
                    break;
            }
            return locationLastingLastingId;
        }

        private void initWakeUpStrategy() {
            Preference wakeUpStrategy = findPreference(Constants.KEY_WAKE_UP_STRATEGY);
            wakeUpStrategy.setSummary(
                    getWakeUpStrategyLabel(
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Constants.KEY_WAKE_UP_STRATEGY, "nowakeup")
                    )
            );
            wakeUpStrategy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference wakeUpStrategy, Object value) {
                    String wakeUpStrategyValue = (String) value;
                    wakeUpStrategy.setSummary(getString(getWakeUpStrategyLabel(wakeUpStrategyValue)));
                    return true;
                }
            });
        }

        private int getWakeUpStrategyLabel(String wakeUpStrategyValue) {
            int wakeUpStrategyId;
            switch (wakeUpStrategyValue) {
                case "wakeuppartial":
                    wakeUpStrategyId = R.string.wakeuppartial_label;
                    break;
                case "wakeupfull":
                    wakeUpStrategyId = R.string.wakeupfull_label;
                    break;
                case "nowakeup":
                default:
                    wakeUpStrategyId = R.string.nowakeup_label;
                    break;
            }
            return wakeUpStrategyId;
        }

        private String getDataFromCacheDB() {

            ReverseGeocodingCacheDbHelper mDbHelper = ReverseGeocodingCacheDbHelper.getInstance(getActivity());
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            long numberOfRowsInAddress = DatabaseUtils.queryNumEntries(db, ReverseGeocodingCacheContract.LocationAddressCache.TABLE_NAME);

            StringBuilder lastRowsFromDB = new StringBuilder();

            lastRowsFromDB.append("There are ");
            lastRowsFromDB.append(numberOfRowsInAddress);
            lastRowsFromDB.append(" of rows in cache.\n\n");

            String[] projection = {
                    ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_ADDRESS,
                    ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED,
                    ReverseGeocodingCacheContract.LocationAddressCache._ID
            };

            String sortOrder = ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED + " DESC";

            Cursor cursor = db.query(
                    ReverseGeocodingCacheContract.LocationAddressCache.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    sortOrder
            );

            int rowsCounter = 0;

            while(cursor.moveToNext()) {

                if (!cursor.isFirst()) {
                    lastRowsFromDB.append("\n");
                }

                byte[] cachedAddressBytes = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_ADDRESS));
                Address address = ReverseGeocodingCacheDbHelper.getAddressFromBytes(cachedAddressBytes);

                long recordCreatedinMilis = cursor.getLong(cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED));
                String recordCreatedTxt = iso8601Format.format(new Date(recordCreatedinMilis));

                int itemId = cursor.getInt(cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache._ID));

                lastRowsFromDB.append(itemId);
                lastRowsFromDB.append(" : ");
                lastRowsFromDB.append(recordCreatedTxt);
                lastRowsFromDB.append(" : ");
                if (address.getLocality() != null) {
                    lastRowsFromDB.append(address.getLocality());
                    if (!address.getLocality().equals(address.getSubLocality())) {
                        lastRowsFromDB.append(" - ");
                        lastRowsFromDB.append(address.getSubLocality());
                    }
                }

                rowsCounter++;
                if (rowsCounter > 7) {
                    break;
                }
            }
            cursor.close();

            return lastRowsFromDB.toString();
        }
    }

    public static class WidgetPreferenceFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_widget);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case Constants.KEY_PREF_WIDGET_THEME:
                    Intent intent = new Intent(Constants.ACTION_APPWIDGET_THEME_CHANGED);
                    intent.setPackage("com.example.commontask");
                    getActivity().sendBroadcast(intent);
                    setSummary(Constants.KEY_PREF_WIDGET_THEME);
                    break;
                case Constants.KEY_PREF_LOCATION_GPS_ENABLED:
                    break;
                case Constants.KEY_PREF_WIDGET_SHOW_LABELS:
                    intent = new Intent(Constants.ACTION_APPWIDGET_THEME_CHANGED);
                    intent.setPackage("com.example.commontask");
                    getActivity().sendBroadcast(intent);
                    break;
                case Constants.KEY_PREF_WIDGET_GRAPH_NATIVE_SCALE:
                    GraphUtils.invalidateGraph();
                    intent = new Intent(Constants.ACTION_APPWIDGET_CHANGE_GRAPH_SCALE);
                    intent.setPackage("com.example.commontask");
                    getActivity().sendBroadcast(intent);
                    break;
                case Constants.KEY_PREF_WIDGET_SHOW_CONTROLS:
                    intent = new Intent(Constants.ACTION_APPWIDGET_SETTINGS_SHOW_CONTROLS);
                    intent.setPackage("com.example.commontask");
                    getActivity().sendBroadcast(intent);
                case Constants.KEY_PREF_UPDATE_DETAIL:
                    intent = new Intent(Constants.ACTION_FORCED_APPWIDGET_UPDATE);
                    intent.setPackage("com.example.commontask");
                    getActivity().sendBroadcast(intent);
                    setDetailedSummary(Constants.KEY_PREF_UPDATE_DETAIL);
                    break;
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            setSummary(Constants.KEY_PREF_WIDGET_THEME);
            setDetailedSummary(Constants.KEY_PREF_UPDATE_DETAIL);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private void setDetailedSummary(CharSequence prefKey) {
            Preference updatePref = findPreference(prefKey);
            ListPreference updateListPref = (ListPreference) updatePref;
            switch (updateListPref.getValue()) {
                case "preference_display_update_value":
                    updatePref.setSummary(R.string.preference_display_update_value_info);
                    break;
                case "preference_display_update_location_source":
                    updatePref.setSummary(R.string.preference_display_update_location_source_info);
                    break;
                case "preference_display_update_nothing":
                default:
                    updatePref.setSummary(updateListPref.getEntry());
                    break;
            }
        }

        private void setSummary(CharSequence prefKey) {
            Preference updatePref = findPreference(prefKey);
            ListPreference updateListPref = (ListPreference) updatePref;
            updatePref.setSummary(updateListPref.getEntry());
        }
    }

    public static class DebugOptionsPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_debug);
            initLogFileChooser();
            initLogFileLasting();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            Preference preference = findPreference(KEY_DEBUG_FILE);
            preference.setSummary(preferences.getString(KEY_DEBUG_FILE,""));
        }

        private void initLogFileChooser() {

            Preference logToFileCheckbox = findPreference(KEY_DEBUG_TO_FILE);
            logToFileCheckbox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, Object value) {
                    if (!checkWriteToSdcardPermission()) {
                        return false;
                    }
                    boolean logToFile = (Boolean) value;
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    preferences.edit().putBoolean(KEY_DEBUG_TO_FILE, logToFile).apply();
                    LogToFile.logToFileEnabled = logToFile;
                    return true;
                }
            });

            Preference buttonFileLog = findPreference(KEY_DEBUG_FILE);
            buttonFileLog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    new ChooserDialog().with(getActivity())
                            .withFilter(true, false)
                            .withStartFile("/mnt")
                            .withChosenListener(new ChooserDialog.Result() {
                                @Override
                                public void onChoosePath(String path, File pathFile) {
                                    String logFileName = path + "/log-yourlocalweather.txt";
                                    LogToFile.logFilePathname = logFileName;
                                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                    preferences.edit().putString(KEY_DEBUG_FILE, logFileName).apply();
                                    preference.setSummary(preferences.getString(KEY_DEBUG_FILE,""));
                                }
                            })
                            .build()
                            .show();
                    return true;
                }
            });
        }

        private boolean checkWriteToSdcardPermission() {
            if (ContextCompat.checkSelfPermission(getActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                } else {
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            123456);
                }
                return false;
            }
            return true;
        }

        private void initLogFileLasting() {
            Preference logFileLasting = findPreference(KEY_DEBUG_FILE_LASTING_HOURS);
            logFileLasting.setSummary(
                    getLogFileLastingLabel(Integer.parseInt(
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(KEY_DEBUG_FILE_LASTING_HOURS, "24"))
                    )
            );
            logFileLasting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference logFileLasting, Object value) {
                    String logFileLastingHoursTxt = (String) value;
                    Integer logFileLastingHours = Integer.valueOf(logFileLastingHoursTxt);
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    preferences.edit().putString(KEY_DEBUG_FILE_LASTING_HOURS, logFileLastingHoursTxt).apply();
                    logFileLasting.setSummary(getString(getLogFileLastingLabel(logFileLastingHours)));
                    LogToFile.logFileHoursOfLasting = logFileLastingHours;
                    return true;
                }
            });
        }

        private int getLogFileLastingLabel(int logFileLastingValue) {
            int logFileLastingId;
            switch (logFileLastingValue) {
                case 12:
                    logFileLastingId = R.string.log_file_12_label;
                    break;
                case 48:
                    logFileLastingId = R.string.log_file_48_label;
                    break;
                case 72:
                    logFileLastingId = R.string.log_file_72_label;
                    break;
                case 168:
                    logFileLastingId = R.string.log_file_168_label;
                    break;
                case 720:
                    logFileLastingId = R.string.log_file_720_label;
                    break;
                case 24:
                default:
                    logFileLastingId = R.string.log_file_24_label;
                    break;
            }
            return logFileLastingId;
        }
    }

    public static class AboutPreferenceFragment extends PreferenceFragment {

        PackageManager mPackageManager;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_about);

            mPackageManager = getActivity().getPackageManager();
            findPreference(Constants.KEY_PREF_ABOUT_VERSION).setSummary(getVersionName());
            findPreference(Constants.KEY_PREF_ABOUT_F_DROID).setIntent(fDroidIntent());
            findPreference(Constants.KEY_PREF_ABOUT_GOOGLE_PLAY).setIntent(googlePlayIntent());
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());

            if (view != null) {
                view.setPadding(horizontalMargin, topMargin, horizontalMargin, verticalMargin);
            }
            return view;
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                             Preference preference) {
            if (preference.equals(findPreference(Constants.KEY_PREF_ABOUT_OPEN_SOURCE_LICENSES))) {
                LicensesDialogFragment licensesDialog = LicensesDialogFragment.newInstance();
                licensesDialog.show(getFragmentManager(), "LicensesDialog");
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        private String getVersionName() {
            String versionName;
            try {
                versionName = mPackageManager.getPackageInfo(getActivity().getPackageName(),
                        0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Get version name error", e);
                versionName = "666";
            }
            return versionName;
        }

        private Intent fDroidIntent() {
            String ACTION_VIEW = Intent.ACTION_VIEW;
            String fDroidWebUri = String.format(Constants.F_DROID_WEB_URI,
                    getActivity().getPackageName());

            return new Intent(ACTION_VIEW, Uri.parse(fDroidWebUri));
        }

        private Intent googlePlayIntent() {
            String ACTION_VIEW = Intent.ACTION_VIEW;
            String googlePlayAppUri = String.format(Constants.GOOGLE_PLAY_APP_URI,
                    getActivity().getPackageName());
            String googlePlayWebUri = String.format(Constants.GOOGLE_PLAY_WEB_URI,
                    getActivity().getPackageName());

            Intent intent = new Intent(ACTION_VIEW, Uri.parse(googlePlayAppUri));
            if (mPackageManager.resolveActivity(intent, 0) == null) {
                intent = new Intent(ACTION_VIEW, Uri.parse(googlePlayWebUri));
            }

            return intent;
        }

        public static class LicensesDialogFragment extends DialogFragment {

            static LicensesDialogFragment newInstance() {
                return new LicensesDialogFragment();
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final TextView textView = new TextView(getActivity());
                int padding = (int) getResources().getDimension(R.dimen.activity_horizontal_margin);
                textView.setPadding(padding, padding, padding, padding);
                textView.setLineSpacing(0, 1.2f);
                textView.setLinkTextColor(ContextCompat.getColor(getActivity(), R.color.link_color));
                textView.setText(R.string.licenses);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                return new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.title_open_source_licenses))
                        .setView(textView)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
            }
        }
    }

    public static class SettingsAlertDialog extends DialogFragment {

        private static final String ARG_MESSAGE_RES_ID = "com.example.commontask.message_res_id";

        public SettingsAlertDialog newInstance(int messageResId) {
            SettingsAlertDialog fragment = new SettingsAlertDialog();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int messageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(messageResId);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent returnToMainActivity = new Intent(getActivity().getApplicationContext(), MainWeatherAppActivity.class);
                    startActivity(returnToMainActivity);
                }
            });
            return builder.create();
        }
    }

    public static void restartApp(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

}
