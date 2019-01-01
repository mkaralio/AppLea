package com.example.commontask.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.commontask.MainActivity;
import com.example.commontask.MainWeatherAppActivity;
import com.example.commontask.R;
import com.example.commontask.model.CurrentWeatherDbHelper;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.WidgetSettingsDbHelper;
import com.example.commontask.service.CurrentWeatherService;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.Constants;
import com.example.commontask.utils.PermissionUtil;

import java.util.Calendar;

import static com.example.commontask.utils.LogToFile.appendLog;

public abstract class AbstractWidgetProvider extends AppWidgetProvider {

    private static String TAG = "AbstractWidgetProvider";

    protected Location currentLocation;
    volatile boolean servicesStarted = false;

    @Override
    public void onEnabled(Context context) {
        appendLog(context, TAG, "onEnabled:start");
        super.onEnabled(context);
        LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(context);
        WidgetSettingsDbHelper widgetSettingsDbHelper = WidgetSettingsDbHelper.getInstance(context);
        if (PermissionUtil.areAllPermissionsGranted(context)) {
            Toast.makeText(context,
                    R.string.permissions_not_granted,
                    Toast.LENGTH_LONG).show();
        }
        ComponentName widgetComponent = new ComponentName(context, getWidgetClass());

        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
        if (widgetIds.length == 0) {
            return;
        }
        int currentWidget = widgetIds[0];
        Long locationId = widgetSettingsDbHelper.getParamLong(currentWidget, "locationId");
        if (locationId == null) {
            currentLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!currentLocation.isEnabled()) {
                currentLocation = locationsDbHelper.getLocationByOrderId(1);
            }
        } else {
            currentLocation = locationsDbHelper.getLocationById(locationId);
        }
        appendLog(context, TAG, "onEnabled:end");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        appendLog(context, TAG, "intent:" + intent + ", widget:" + getWidgetClass());
        super.onReceive(context, intent);
        LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(context);
        WidgetSettingsDbHelper widgetSettingsDbHelper = WidgetSettingsDbHelper.getInstance(context);
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);

        Integer widgetId = null;
        ComponentName widgetComponent = new ComponentName(context, getWidgetClass());

        if (intent.hasExtra("widgetId")) {
            widgetId = intent.getIntExtra("widgetId", 0);
            if (widgetId == 0) {
                widgetId = null;
            }
        }
        if (widgetId == null) {
            int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
            if (widgetIds.length == 0) {
                return;
            }
            widgetId = widgetIds[0];
        }
        Long locationId = widgetSettingsDbHelper.getParamLong(widgetId, "locationId");
        if (locationId == null) {
            currentLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!currentLocation.isEnabled()) {
                currentLocation = locationsDbHelper.getLocationByOrderId(1);
            }
        } else {
            currentLocation = locationsDbHelper.getLocationById(locationId);
        }
        switch (intent.getAction()) {
            case "com.example.commontask.action.WEATHER_UPDATE_RESULT":
            case "android.appwidget.action.APPWIDGET_UPDATE":
                if (!servicesStarted) {
                    onEnabled(context);
                    servicesStarted = true;
                }
                onUpdate(context, widgetManager, new int[] {widgetId});
                break;
            case Constants.ACTION_FORCED_APPWIDGET_UPDATE:
                //if (!WidgetRefreshIconService.isRotationActive) {
                    sendWeatherUpdate(context);
                //}
                onUpdate(context, widgetManager, new int[]{ widgetId});
                break;
            case Intent.ACTION_SCREEN_ON:
                updateWather(context);
            case Intent.ACTION_LOCALE_CHANGED:
            case Constants.ACTION_APPWIDGET_THEME_CHANGED:
                refreshWidgetValues(context);
                break;
            case Constants.ACTION_APPWIDGET_UPDATE_PERIOD_CHANGED:
                onEnabled(context);
                break;
            case Constants.ACTION_APPWIDGET_CHANGE_LOCATION:
                changeLocation(widgetId, locationsDbHelper, widgetSettingsDbHelper);
                onUpdate(context, widgetManager, new int[]{ widgetId});
                break;
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        appendLog(context, TAG, "onUpdate:start");
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        for (int appWidgetId : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    getWidgetLayout());

            if (ExtLocationWidgetProvider.class.equals(getWidgetClass())) {
                ExtLocationWidgetProvider.setWidgetTheme(context, remoteViews);
            } else if (MoreWidgetProvider.class.equals(getWidgetClass())) {
                MoreWidgetProvider.setWidgetTheme(context, remoteViews);
            } else if (LessWidgetProvider.class.equals(getWidgetClass())) {
                LessWidgetProvider.setWidgetTheme(context, remoteViews);
            }
            setWidgetIntents(context, remoteViews, getWidgetClass(), appWidgetId);
            preLoadWeather(context, remoteViews, appWidgetId);

            appWidgetManager.updateAppWidget(new ComponentName(context, getWidgetClass()), remoteViews);
        }
        appendLog(context, TAG, "onUpdate:end");
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        WidgetSettingsDbHelper widgetSettingsDbHelper = WidgetSettingsDbHelper.getInstance(context);
        for (int widgetId: appWidgetIds) {
            widgetSettingsDbHelper.deleteRecordFromTable(widgetId);
        }
    }

    private void refreshWidgetValues(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, getWidgetClass());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    private void updateWather(Context context) {
        long now = Calendar.getInstance().getTimeInMillis();
        CurrentWeatherDbHelper currentWeatherDbHelper = CurrentWeatherDbHelper.getInstance(context);
        CurrentWeatherDbHelper.WeatherRecord weatherRecord = currentWeatherDbHelper.getWeather(currentLocation.getId());
        long lastUpdatedWeather = weatherRecord.getLastUpdatedTime();
        appendLog(context, TAG, "SCREEN_ON called, lastUpdate=" + lastUpdatedWeather + ", now=" + now);
        if (now < (lastUpdatedWeather + 900000)) {
            return;
        }
        currentWeatherDbHelper.updateLastUpdatedTime(currentLocation.getId(), now);
        sendWeatherUpdate(context);
    }

    private void sendWeatherUpdate(Context context) {
        if ((currentLocation.getOrderId() == 0) && currentLocation.isEnabled()) {
            Intent startLocationUpdateIntent = new Intent("android.intent.action.START_LOCATION_AND_WEATHER_UPDATE");
            startLocationUpdateIntent.setPackage("com.example.commontask");
            startLocationUpdateIntent.putExtra("location", currentLocation);
            context.startService(startLocationUpdateIntent);
            appendLog(context, TAG, "send intent START_LOCATION_UPDATE:" + startLocationUpdateIntent);
        } else if (currentLocation.getOrderId() != 0) {
            Intent intentToCheckWeather = new Intent(context, CurrentWeatherService.class);
            intentToCheckWeather.putExtra("location", currentLocation);
            context.startService(intentToCheckWeather);
        }
    }

    public static void setWidgetIntents(Context context, RemoteViews remoteViews, Class<?>  widgetClass, int widgetId) {
        appendLog(context, TAG, "setWidgetIntents:widgetid:" + widgetId);
        Intent intentRefreshService = new Intent(context, widgetClass);
        intentRefreshService.setAction(Constants.ACTION_FORCED_APPWIDGET_UPDATE);
        intentRefreshService.putExtra("widgetId", widgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intentRefreshService, 0);
        remoteViews.setOnClickPendingIntent(R.id.widget_button_refresh, pendingIntent);

        Intent intentStartActivity = new Intent(context, MainWeatherAppActivity.class);
        PendingIntent pendingIntent2 = PendingIntent.getActivity(context, 0,
                intentStartActivity, 0);
        remoteViews.setOnClickPendingIntent(R.id.widget_icon, pendingIntent2);

        Intent intentSwitchLocality = new Intent(context, widgetClass);
        intentSwitchLocality.setAction(Constants.ACTION_APPWIDGET_CHANGE_LOCATION);
        intentSwitchLocality.putExtra("widgetId", widgetId);
        PendingIntent pendingSwitchLocalityIntent = PendingIntent.getBroadcast(context, 0,
                intentSwitchLocality, 0);
        remoteViews.setOnClickPendingIntent(R.id.widget_city, pendingSwitchLocalityIntent);
    }

    protected abstract void preLoadWeather(Context context, RemoteViews remoteViews, int widgetId);

    protected abstract Class<?> getWidgetClass();

    protected abstract String getWidgetName();

    protected abstract int getWidgetLayout();

    private void changeLocation(int widgetId,
                                LocationsDbHelper locationsDbHelper,
                                WidgetSettingsDbHelper widgetSettingsDbHelper) {
        int newOrderId = 1 + currentLocation.getOrderId();
        currentLocation = locationsDbHelper.getLocationByOrderId(newOrderId);
        if (currentLocation == null) {
            currentLocation = locationsDbHelper.getLocationByOrderId(0);
        }
        widgetSettingsDbHelper.saveParamLong(widgetId, "locationId", currentLocation.getId());
    }
}
