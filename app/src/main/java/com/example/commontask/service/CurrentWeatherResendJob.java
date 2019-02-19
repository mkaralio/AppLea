package com.example.commontask.service;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Message;
import android.os.RemoteException;

import static com.example.commontask.utils.LogToFile.appendLog;

@TargetApi(Build.VERSION_CODES.M)
public class CurrentWeatherResendJob extends AbstractAppJob {
    private static final String TAG = "CurrentWeatherResendJob";
    public static final int JOB_ID = 1537091709;

    private JobParameters params;
    @Override
    public boolean onStartJob(JobParameters params) {
        this.params = params;
        appendLog(getBaseContext(), TAG, "onStartJob");
        sendRetryMessageToCurrentWeatherService();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        appendLog(getBaseContext(), TAG, "onStopJob");
        unbindAllServices();
        return true;
    }

    @Override
    protected void serviceConnected(ServiceConnection serviceConnection) {
        if (currentWeatherUnsentMessages.isEmpty()) {
            jobFinished(params, false);
        }
    }

    protected void sendRetryMessageToCurrentWeatherService() {
        appendLog(getBaseContext(), TAG, "sendRetryMessageToCurrentWeatherService:1");
        currentWeatherServiceLock.lock();
        appendLog(getBaseContext(), TAG, "sendRetryMessageToCurrentWeatherService:after lock");
        try {
            Message msg = Message.obtain(
                    null,
                    CurrentWeatherService.START_CURRENT_WEATHER_RETRY
            );
            if (checkIfCurrentWeatherServiceIsNotBound()) {
                appendLog(getBaseContext(), TAG, "sendRetryMessageToCurrentWeatherService:pushing into messages");
                currentWeatherUnsentMessages.add(msg);
                return;
            }
            appendLog(getBaseContext(), TAG, "sendMessageToService:");
            currentWeatherService.send(msg);
            jobFinished(params, false);
        } catch (RemoteException e) {
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            currentWeatherServiceLock.unlock();
        }
    }
}
