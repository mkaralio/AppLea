package com.example.commontask.service;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Message;
import android.os.RemoteException;

import static com.example.commontask.utils.LogToFile.appendLog;

@TargetApi(Build.VERSION_CODES.M)
public class WeatherForecastResendJob extends AbstractAppJob {
    private static final String TAG = "WeatherForecastResendJob";
    public static final int JOB_ID = 463452709;

    private JobParameters params;

    @Override
    public boolean onStartJob(JobParameters params) {
        this.params = params;
        sendRetryMessageToWeatherForecastService();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        unbindAllServices();
        return true;
    }

    @Override
    protected void serviceConnected(ServiceConnection serviceConnection) {
        if (weatherForecastUnsentMessages.isEmpty()) {
            jobFinished(params, false);
        }
    }

    protected void sendRetryMessageToWeatherForecastService() {
        weatherForecastServiceLock.lock();
        try {
            Message msg = Message.obtain(
                    null,
                    ForecastWeatherService.START_WEATHER_FORECAST_RETRY
            );
            if (checkIfWeatherForecastServiceIsNotBound()) {
                weatherForecastUnsentMessages.add(msg);
                return;
            }
            weatherForecastService.send(msg);
            jobFinished(params, false);
        } catch (RemoteException e) {
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            weatherForecastServiceLock.unlock();
        }
    }
}
