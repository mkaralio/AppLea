package com.example.commontask.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.example.commontask.MainWeatherAppActivity;
import com.example.commontask.R;
import com.example.commontask.model.CurrentWeatherDbHelper;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.model.Weather;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.TemperatureUtil;
import com.example.commontask.utils.Utils;

public class NotificationService extends IntentService {

    private static final String TAG = "NotificationsService";

    public NotificationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final CurrentWeatherDbHelper currentWeatherDbHelper = CurrentWeatherDbHelper.getInstance(this);
        final LocationsDbHelper locationsDbHelper = LocationsDbHelper.getInstance(this);
        Location currentLocation = locationsDbHelper.getLocationByOrderId(0);
        if (!currentLocation.isEnabled()) {
            currentLocation = locationsDbHelper.getLocationByOrderId(1);
        }
        if (currentLocation == null) {
            return;
        }
        weatherNotification(currentWeatherDbHelper.getWeather(currentLocation.getId()));
    }

    public static void setNotificationServiceAlarm(Context context,
                                                   boolean isNotificationEnable) {
        Intent intentToCheckWeather = new Intent(context, CurrentWeatherService.class);
        intentToCheckWeather.putExtra("updateSource", "NOTIFICATION");
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intentToCheckWeather,
                                                               PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        String intervalPref = AppPreference.getInterval(context);
        long intervalMillis = Utils.intervalMillisForAlarm(intervalPref);
        if (isNotificationEnable) {

            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                             SystemClock.elapsedRealtime() + intervalMillis,
                                             intervalMillis,
                                             pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private void weatherNotification(CurrentWeatherDbHelper.WeatherRecord weatherRecord) {
        if (weatherRecord == null) {
            return;
        }
        Weather weather = weatherRecord.getWeather();
        Intent intent = new Intent(this, MainWeatherAppActivity.class);
        PendingIntent launchIntent = PendingIntent.getActivity(this, 0, intent, 0);

        String temperatureWithUnit = TemperatureUtil.getTemperatureWithUnit(this, weather);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentIntent(launchIntent)
                .setSmallIcon(R.drawable.small_icon)
                .setTicker(temperatureWithUnit
                                   + "  "
                                   + Utils.getCityAndCountry(this, 0))
                .setContentTitle(temperatureWithUnit +
                                 "  " +
                                 Utils.getWeatherDescription(this, weather))
                .setContentText(Utils.getCityAndCountry(this, 0))
                .setVibrate(isVibrateEnabled())
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(0, notification);
    }

    private long[] isVibrateEnabled() {
        if (!AppPreference.isVibrateEnabled(this)) {
            return null;
        }
        return new long[]{500, 500, 500};
    }
}
