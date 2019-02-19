package com.example.commontask.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.commontask.WidgetSettingsDialogue;
import com.example.commontask.R;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.WidgetSettingsDbHelper;
import com.example.commontask.service.CurrentWeatherService;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.Constants;
import com.example.commontask.utils.GraphUtils;
import com.example.commontask.utils.PermissionUtil;

import java.util.ArrayList;

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
        if (PermissionUtil.noPermissionGranted(context)) {
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
        } else {
            currentLocation = locationsDbHelper.getLocationById(locationId);
        }
        if (!currentLocation.isEnabled()) {
            currentLocation = locationsDbHelper.getLocationByOrderId(1);
        }
        appendLog(context, TAG, "onEnabled:end");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        appendLog(context, TAG, "intent:", intent, ", widget:", getWidgetClass());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
            appendLog(context, TAG, "EXTRA_APPWIDGET_ID:" + appWidgetId);
        }

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
            case "ocom.example.commontask.action.WEATHER_UPDATE_RESULT":
            case "android.appwidget.action.APPWIDGET_UPDATE":
                if (!servicesStarted) {
                    onEnabled(context);
                    servicesStarted = true;
                }
                onUpdate(context, widgetManager, new int[] {widgetId});
                break;
            case Constants.ACTION_FORCED_APPWIDGET_UPDATE:
                if (!WidgetRefreshIconService.isRotationActive) {
                    sendWeatherUpdate(context);
                }
                onUpdate(context, widgetManager, new int[]{ widgetId});
                break;
            case Intent.ACTION_LOCALE_CHANGED:
            case Constants.ACTION_APPWIDGET_THEME_CHANGED:
            case Constants.ACTION_APPWIDGET_SETTINGS_SHOW_CONTROLS:
                refreshWidgetValues(context);
                break;
            case Constants.ACTION_APPWIDGET_UPDATE_PERIOD_CHANGED:
                onEnabled(context);
                break;
            case Constants.ACTION_APPWIDGET_CHANGE_SETTINGS:
                onUpdate(context, widgetManager, new int[]{ widgetId});
                break;
        }

        if (intent.getAction().startsWith(Constants.ACTION_APPWIDGET_SETTINGS_OPENED)) {
            String[] params = intent.getAction().split("__");
            String widgetIdTxt = params[1];
            widgetId = Integer.parseInt(widgetIdTxt);
            openWidgetSettings(context, widgetId, params[2]);
        } else if (intent.getAction().startsWith(Constants.ACTION_APPWIDGET_START_ACTIVITY)) {
            AppPreference.setCurrentLocationId(context, currentLocation.getId());
            Long widgetActionId = intent.getLongExtra("widgetAction", 1);
            Class activityClass = WidgetActions.getById(widgetActionId, "action_current_weather_icon").getActivityClass();
            Intent activityIntent = new Intent(context, activityClass);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(activityIntent);
        } else if (intent.getAction().startsWith(Constants.ACTION_APPWIDGET_CHANGE_LOCATION)) {
            changeLocation(widgetId, locationsDbHelper, widgetSettingsDbHelper);
            GraphUtils.invalidateGraph();
            onUpdate(context, widgetManager, new int[]{widgetId});
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        appendLog(context, TAG, "onUpdate:start");
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        ComponentName componentName = new ComponentName(context, getWidgetClass());
        int[] appWidgetIdsForWidget = appWidgetManager.getAppWidgetIds(componentName);

        for (int appWidgetId : appWidgetIds) {

            boolean found = false;
            for (int widgetIdToSearch: appWidgetIdsForWidget) {
                if (widgetIdToSearch == appWidgetId) {
                    found = true;
                }
            }
            if (!found) {
                continue;
            }

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    getWidgetLayout());

            if (ExtLocationWidgetProvider.class.equals(getWidgetClass())) {
                ExtLocationWidgetProvider.setWidgetTheme(context, remoteViews);
            } else if (MoreWidgetProvider.class.equals(getWidgetClass())) {
                MoreWidgetProvider.setWidgetTheme(context, remoteViews);
            } else if (LessWidgetProvider.class.equals(getWidgetClass())) {
                LessWidgetProvider.setWidgetTheme(context, remoteViews);
            } else if (ExtLocationWithForecastWidgetProvider.class.equals(getWidgetClass())) {
                ExtLocationWithForecastWidgetProvider.setWidgetTheme(context, remoteViews, appWidgetId);
            } else if (WeatherForecastWidgetProvider.class.equals(getWidgetClass())) {
                WeatherForecastWidgetProvider.setWidgetTheme(context, remoteViews, appWidgetId);
            } else if (ExtLocationWithGraphWidgetProvider.class.equals(getWidgetClass())) {
                ExtLocationWithGraphWidgetProvider.setWidgetTheme(context, remoteViews, appWidgetId);
            } else if (WeatherGraphWidgetProvider.class.equals(getWidgetClass())) {
                WeatherGraphWidgetProvider.setWidgetTheme(context, remoteViews, appWidgetId);
            }
            setWidgetIntents(context, remoteViews, getWidgetClass(), appWidgetId);
            preLoadWeather(context, remoteViews, appWidgetId);

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
            //appWidgetManager.updateAppWidget(new ComponentName(context, getWidgetClass()), remoteViews);
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

    protected void refreshWidgetValues(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, getWidgetClass());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    protected void sendWeatherUpdate(Context context) {
        if (currentLocation == null) {
            appendLog(context,
                    TAG,
                    "currentLocation is null");
            return;
        }
        if ((currentLocation.getOrderId() == 0) && currentLocation.isEnabled()) {
            Intent startLocationUpdateIntent = new Intent("android.intent.action.START_LOCATION_AND_WEATHER_UPDATE");
            startLocationUpdateIntent.setPackage("ocom.example.commontask");
            startLocationUpdateIntent.putExtra("locationId", currentLocation.getId());
            startLocationUpdateIntent.putExtra("forceUpdate", true);
            startServiceWithCheck(context, startLocationUpdateIntent);
            appendLog(context, TAG, "send intent START_LOCATION_UPDATE:", startLocationUpdateIntent);
        } else if (currentLocation.getOrderId() != 0) {
            Intent intentToCheckWeather = new Intent(context, CurrentWeatherService.class);
            intentToCheckWeather.putExtra("locationId", currentLocation.getId());
            intentToCheckWeather.putExtra("forceUpdate", true);
            intentToCheckWeather.putExtra("updateWeatherOnly", true);
            startServiceWithCheck(context, intentToCheckWeather);
        }
    }

    public static void setWidgetIntents(Context context, RemoteViews remoteViews, Class<?>  widgetClass, int widgetId) {
        appendLog(context, TAG, "setWidgetIntents:widgetid:", widgetId);
        if (AppPreference.isShowControls(context)) {
            remoteViews.setViewVisibility(R.id.widget_weather_graph_1x3_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_graph_3x3_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_forecast_3x3_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_weather_forecast_1x3_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_3x3_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_less_3x1_settings_layout, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_more_3x3_settings_layout, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.widget_weather_graph_1x3_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_graph_3x3_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_forecast_3x3_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_weather_forecast_1x3_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_ext_loc_3x3_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_less_3x1_settings_layout, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_more_3x3_settings_layout, View.GONE);
        }

        Intent intentRefreshService = new Intent(context, widgetClass);
        intentRefreshService.setAction(Constants.ACTION_FORCED_APPWIDGET_UPDATE);
        intentRefreshService.setPackage("ocom.example.commontask");
        intentRefreshService.putExtra("widgetId", widgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intentRefreshService, 0);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_3x3_widget_button_refresh, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_forecast_3x3_widget_button_refresh, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_graph_3x3_widget_button_refresh, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.widget_less_3x1_widget_button_refresh, pendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.widget_more_3x3_widget_button_refresh, pendingIntent);

        final WidgetSettingsDbHelper widgetSettingsDbHelper = WidgetSettingsDbHelper.getInstance(context);

        WidgetActions mainIconAction = WidgetActions.getById(widgetSettingsDbHelper.getParamLong(widgetId, "action_current_weather_icon"), "action_current_weather_icon");
        PendingIntent pendingIntentMainIconAction = getActionIntent(context, mainIconAction, widgetClass, widgetId);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_3x3_widget_icon, pendingIntentMainIconAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_forecast_3x3_widget_icon, pendingIntentMainIconAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_graph_3x3_widget_icon, pendingIntentMainIconAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_less_3x1_widget_icon, pendingIntentMainIconAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_more_3x3_widget_icon, pendingIntentMainIconAction);

        WidgetActions graphAction = WidgetActions.getById(widgetSettingsDbHelper.getParamLong(widgetId, "action_graph"), "action_graph");
        PendingIntent pendingIntentGraphAction = getActionIntent(context, graphAction, widgetClass, widgetId);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_graph_3x3_forecast_graph, pendingIntentGraphAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_weather_graph_1x3_forecast_graph, pendingIntentGraphAction);

        WidgetActions forecastAction = WidgetActions.getById(widgetSettingsDbHelper.getParamLong(widgetId, "action_forecast"), "action_forecast");
        PendingIntent pendingIntentForecastAction = getActionIntent(context, forecastAction, widgetClass, widgetId);
        remoteViews.setOnClickPendingIntent(R.id.widget_ext_loc_forecast_3x3_forecast_layout, pendingIntentForecastAction);
        remoteViews.setOnClickPendingIntent(R.id.widget_weather_forecast_1x3_forecast_layout, pendingIntentForecastAction);

        Integer cityViewId = getCityViewId(widgetClass);
        if (cityViewId != null) {
            WidgetActions cityAction = WidgetActions.getById(widgetSettingsDbHelper.getParamLong(widgetId, "action_city"), "action_city");
            PendingIntent pendingIntentCityAction = getActionIntent(context, cityAction, widgetClass, widgetId);
            remoteViews.setOnClickPendingIntent(getCityViewId(widgetClass), pendingIntentCityAction);
        }

        setSettingButtonAction(context, widgetId, "forecastSettings", R.id.widget_ext_loc_forecast_3x3_button_days_setting, remoteViews, ExtLocationWithForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "graphSetting", R.id.widget_ext_loc_graph_3x3_button_graph_setting, remoteViews, ExtLocationWithGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "graphSetting", R.id.widget_weather_graph_1x3_button_graph_setting, remoteViews, WeatherGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "forecastSettings", R.id.widget_weather_forecast_1x3_button_days_setting, remoteViews, WeatherForecastWidgetProvider.class);

        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_ext_loc_forecast_3x3_button_location_setting, remoteViews, ExtLocationWithForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_weather_forecast_1x3_button_location_setting, remoteViews, WeatherForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_ext_loc_graph_3x3_button_location_setting, remoteViews, ExtLocationWithGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_weather_graph_1x3_button_location_setting, remoteViews, WeatherGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_weather_forecast_1x3_button_location_setting, remoteViews, WeatherForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_ext_loc_3x3_button_location_setting, remoteViews, ExtLocationWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_less_3x1_button_location_setting, remoteViews, LessWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "locationSettings", R.id.widget_more_3x3_button_location_setting, remoteViews, MoreWidgetProvider.class);

        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_ext_loc_forecast_3x3_button_action_setting, remoteViews, ExtLocationWithForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_weather_forecast_1x3_button_action_setting, remoteViews, WeatherForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_ext_loc_graph_3x3_button_action_setting, remoteViews, ExtLocationWithGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_weather_graph_1x3_button_action_setting, remoteViews, WeatherGraphWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_weather_forecast_1x3_button_action_setting, remoteViews, WeatherForecastWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_ext_loc_3x3_button_action_setting, remoteViews, ExtLocationWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_less_3x1_button_action_setting, remoteViews, LessWidgetProvider.class);
        setSettingButtonAction(context, widgetId, "widgetActionSettings", R.id.widget_more_3x3_button_action_setting, remoteViews, MoreWidgetProvider.class);
    }

    private static Integer getCityViewId(Class widgetClass) {
        if (ExtLocationWidgetProvider.class.equals(widgetClass)) {
            return R.id.widget_ext_loc_3x3_widget_city;
        } else if (MoreWidgetProvider.class.equals(widgetClass)) {
            return R.id.widget_more_3x3_widget_city;
        } else if (LessWidgetProvider.class.equals(widgetClass)) {
            return R.id.widget_less_3x1_widget_city;
        } else if (ExtLocationWithForecastWidgetProvider.class.equals(widgetClass)) {
            return R.id.widget_ext_loc_forecast_3x3_widget_city;
        } else if (ExtLocationWithGraphWidgetProvider.class.equals(widgetClass)) {
            return R.id.widget_ext_loc_graph_3x3_widget_city;
        } else {
            return null;
        }
    }

    private static PendingIntent getActionIntent(Context context, WidgetActions widgetAction, Class widgetClass, int widgetId) {
        switch (widgetAction) {
            case LOCATION_SWITCH: return getSwitchLocationIntent(context, widgetClass, widgetId);
            case MAIN_SCREEN:
            case FORECAST_SCREEN:
            case GRAPHS_SCREEN:
            default:
                return getActivityIntent(context, widgetClass, widgetId, widgetAction);
        }
    }

    private static PendingIntent getSwitchLocationIntent(Context context, Class widgetClass, int widgetId) {
        Intent intentSwitchLocality = new Intent(context, widgetClass);
        intentSwitchLocality.setAction(Constants.ACTION_APPWIDGET_CHANGE_LOCATION + "_" + widgetId);
        intentSwitchLocality.putExtra("widgetId", widgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intentSwitchLocality, 0);
        return pendingIntent;
    }

    private static PendingIntent getActivityIntent(
            Context context,
            Class widgetClass,
            int widgetId,
            WidgetActions widgetAction) {
        Intent activityIntent = new Intent(context, widgetClass);
        Long widgetActionId = widgetAction.getId();
        activityIntent.setAction(Constants.ACTION_APPWIDGET_START_ACTIVITY + widgetActionId);
        activityIntent.putExtra("widgetId", widgetId);
        activityIntent.putExtra("widgetAction", widgetActionId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                activityIntent, 0);
        return pendingIntent;
    }

    private static void setSettingButtonAction(Context context, int widgetId, String settingName, int buttonId, RemoteViews remoteViews, Class widgetClass) {
        Intent intentWeatherForecastWidgetProvider = new Intent(context, widgetClass);
        intentWeatherForecastWidgetProvider.setAction(Constants.ACTION_APPWIDGET_SETTINGS_OPENED + "__" + widgetId + "__" + settingName);
        intentWeatherForecastWidgetProvider.setPackage("ocom.example.commontask");
        PendingIntent pendingWeatherForecastWidgetProvider = PendingIntent.getBroadcast(context, 0,
                intentWeatherForecastWidgetProvider, 0);
        remoteViews.setOnClickPendingIntent(buttonId, pendingWeatherForecastWidgetProvider);
    }

    protected abstract void preLoadWeather(Context context, RemoteViews remoteViews, int widgetId);

    protected abstract Class<?> getWidgetClass();

    protected abstract String getWidgetName();

    protected abstract int getWidgetLayout();

    private void startBackgroundService(Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getService(context,
                0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 10,
                pendingIntent);
    }

    private void changeLocation(int widgetId,
                                LocationsDbHelper locationsDbHelper,
                                WidgetSettingsDbHelper widgetSettingsDbHelper) {
        if (currentLocation == null) {
            currentLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!currentLocation.isEnabled()) {
                currentLocation = locationsDbHelper.getLocationByOrderId(1);
            }
            if (currentLocation == null) {
                return;
            }
        }
        int newOrderId = 1 + currentLocation.getOrderId();
        currentLocation = locationsDbHelper.getLocationByOrderId(newOrderId);
        if (currentLocation == null) {
            currentLocation = locationsDbHelper.getLocationByOrderId(0);
            if (!currentLocation.isEnabled()) {
                currentLocation = locationsDbHelper.getLocationByOrderId(1);
            }
        }
        if (currentLocation != null) {
            widgetSettingsDbHelper.saveParamLong(widgetId, "locationId", currentLocation.getId());
        }
    }

    protected void startServiceWithCheck(Context context, Intent intent) {
        try {
            context.startService(intent);
        } catch (IllegalStateException ise) {
            intent.putExtra("isInteractive", false);
            PendingIntent pendingIntent = PendingIntent.getService(context,
                    0,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 10,
                    pendingIntent);
        }
    }

    private void openWidgetSettings(Context context, int widgetId, String settingsName) {
        Intent popUpIntent = new Intent(context, WidgetSettingsDialogue.class);
        popUpIntent.putExtra("widgetId", widgetId);
        popUpIntent.putExtra("settings_option", settingsName);
        popUpIntent.putStringArrayListExtra("widget_action_places", getEnabledActionPlaces());
        popUpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(popUpIntent);
    }

    abstract ArrayList<String> getEnabledActionPlaces();
}
