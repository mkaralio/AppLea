package com.example.commontask.widget;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;
import com.example.commontask.R;
import com.example.commontask.model.CurrentWeatherDbHelper;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.Weather;
import com.example.commontask.model.WidgetSettingsDbHelper;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.TemperatureUtil;
import com.example.commontask.utils.Utils;

import java.util.Locale;

import static com.example.commontask.utils.LogToFile.appendLog;

public class LessWidgetService extends IntentService {

    private static final String TAG = "UpdateLessWidgetService:";
    
    public LessWidgetService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        appendLog(this, TAG, "updateWidgetstart");
        final CurrentWeatherDbHelper currentWeatherDbHelper = CurrentWeatherDbHelper.getInstance(this);
        final LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(this);
        final WidgetSettingsDbHelper widgetSettingsDbHelper = WidgetSettingsDbHelper.getInstance(this);

        AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this,
                                                          LessWidgetProvider.class);

        int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
        for (int appWidgetId : widgetIds) {
            Long locationId = widgetSettingsDbHelper.getParamLong(appWidgetId, "locationId");

            Location currentLocation;
            if (locationId == null) {
                currentLocation = locationsDbHelper.getLocationByOrderId(0);
            } else {
                currentLocation = locationsDbHelper.getLocationById(locationId);
            }

            if (currentLocation == null) {
                continue;
            }

            CurrentWeatherDbHelper.WeatherRecord weatherRecord = currentWeatherDbHelper.getWeather(currentLocation.getId());

            if (weatherRecord == null) {
                return;
            }

            Weather weather = weatherRecord.getWeather();

            RemoteViews remoteViews = new RemoteViews(this.getPackageName(),
                                                      R.layout.widget_less_3x1);

            LessWidgetProvider.setWidgetTheme(this, remoteViews);
            LessWidgetProvider.setWidgetIntents(this, remoteViews, LessWidgetProvider.class, appWidgetId);

            String lastUpdate = Utils.setLastUpdateTime(this, weatherRecord.getLastUpdatedTime(), currentLocation.getLocationSource());
            remoteViews.setTextViewText(R.id.widget_temperature, TemperatureUtil.getTemperatureWithUnit(
                    this,
                    weather));
            String secondTemperature = TemperatureUtil.getSecondTemperatureWithUnit(
                    this,
                    weather);
            if (secondTemperature != null) {
                remoteViews.setViewVisibility(R.id.widget_second_temperature, View.VISIBLE);
                remoteViews.setTextViewText(R.id.widget_second_temperature, secondTemperature);
            } else {
                remoteViews.setViewVisibility(R.id.widget_second_temperature, View.GONE);
            }
            remoteViews.setTextViewText(R.id.widget_description, Utils.getWeatherDescription(this, weather));
            remoteViews.setTextViewText(R.id.widget_city, Utils.getCityAndCountry(this, currentLocation.getOrderId()));
            remoteViews.setTextViewText(R.id.widget_last_update, lastUpdate);
            Utils.setWeatherIcon(remoteViews, this, weatherRecord);
            widgetManager.updateAppWidget(appWidgetId, remoteViews);
        }
        appendLog(this, TAG, "updateWidgetend");
    }
}
