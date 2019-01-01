package com.example.commontask.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.commontask.service.NotificationService;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.Constants;

public class StartupReceiver extends BroadcastReceiver {

    private static final String TAG = "StartupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        removeOldPreferences(context);
        boolean isNotificationEnabled = AppPreference.isNotificationEnabled(context);
        NotificationService.setNotificationServiceAlarm(context, isNotificationEnabled);
        Intent intentToStartUpdate = new Intent("com.example.commontask.action.START_ALARM_SERVICE");
        intentToStartUpdate.setPackage("com.example.commontask");
        context.startService(intentToStartUpdate);
        context.sendBroadcast(new Intent("android.appwidget.action.APPWIDGET_UPDATE"));
    }

    private void removeOldPreferences(Context context) {
        SharedPreferences mSharedPreferences = context.getSharedPreferences(Constants.APP_SETTINGS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.remove(Constants.APP_SETTINGS_ADDRESS_FOUND);
        editor.remove(Constants.APP_SETTINGS_GEO_CITY);
        editor.remove(Constants.APP_SETTINGS_GEO_COUNTRY_NAME);
        editor.remove(Constants.APP_SETTINGS_GEO_DISTRICT_OF_COUNTRY);
        editor.remove(Constants.APP_SETTINGS_GEO_DISTRICT_OF_CITY);
        editor.remove(Constants.LAST_UPDATE_TIME_IN_MS);
        editor.remove(Constants.APP_SETTINGS_CITY);
        editor.remove(Constants.APP_SETTINGS_COUNTRY_CODE);
        editor.remove(Constants.WEATHER_DATA_WEATHER_ID);
        editor.remove(Constants.WEATHER_DATA_TEMPERATURE);
        editor.remove(Constants.WEATHER_DATA_DESCRIPTION);
        editor.remove(Constants.WEATHER_DATA_PRESSURE);
        editor.remove(Constants.WEATHER_DATA_HUMIDITY);
        editor.remove(Constants.WEATHER_DATA_WIND_SPEED);
        editor.remove(Constants.WEATHER_DATA_CLOUDS);
        editor.remove(Constants.WEATHER_DATA_ICON);
        editor.remove(Constants.WEATHER_DATA_SUNRISE);
        editor.remove(Constants.WEATHER_DATA_SUNSET);
        editor.remove(Constants.APP_SETTINGS_LATITUDE);
        editor.remove(Constants.APP_SETTINGS_LONGITUDE);
        editor.remove(Constants.LAST_FORECAST_UPDATE_TIME_IN_MS);
        editor.remove(Constants.KEY_PREF_UPDATE_DETAIL);
        editor.remove(Constants.APP_SETTINGS_UPDATE_SOURCE);
        editor.remove(Constants.APP_SETTINGS_LOCATION_ACCURACY);
        editor.remove(Constants.LAST_LOCATION_UPDATE_TIME_IN_MS);
        editor.remove(Constants.LAST_WEATHER_UPDATE_TIME_IN_MS);
        editor.remove(Constants.KEY_PREF_LOCATION_UPDATE_STRATEGY);
        editor.remove("daily_forecast");
        editor.commit();
        context.getSharedPreferences(Constants.PREF_WEATHER_NAME,
                Context.MODE_PRIVATE).edit().clear().commit();


    }
}
