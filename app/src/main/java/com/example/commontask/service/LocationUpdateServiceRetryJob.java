package com.example.commontask.service;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import static com.example.commontask.utils.LogToFile.appendLog;

@TargetApi(Build.VERSION_CODES.M)
public class LocationUpdateServiceRetryJob extends AbstractAppJob {

    private static final String TAG = "LocationUpdateServiceRetryJob";
    public static final int JOB_ID = 1355064090;

    private LocationUpdateService locationUpdateService;
    private JobParameters params;
    int connectedServicesCounter;


    @Override
    public boolean onStartJob(JobParameters params) {
        this.params = params;
        connectedServicesCounter = 0;
        appendLog(this, TAG, "starting cells only location lookup");
        if (locationUpdateService == null) {
            try {
                Intent intent = new Intent(getApplicationContext(), LocationUpdateService.class);
                getApplicationContext().bindService(intent, locationUpdateServiceConnection, Context.BIND_AUTO_CREATE);
            } catch (Exception ie) {
                appendLog(getBaseContext(), TAG, "currentWeatherServiceIsNotBound interrupted:", ie);
            }
        } else {
            locationUpdateService.updateNetworkLocation(
                params.getExtras().getBoolean("byLastLocationOnly"),
                null,
                params.getExtras().getInt("attempts"));
            jobFinished(params, false);
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        getApplicationContext().unbindService(locationUpdateServiceConnection);
        unbindAllServices();
        return true;
    }

    @Override
    protected void serviceConnected(ServiceConnection serviceConnection) {
        jobFinished(params, false);
    }

    private ServiceConnection locationUpdateServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LocationUpdateService.LocationUpdateServiceBinder binder =
                    (LocationUpdateService.LocationUpdateServiceBinder) service;
            locationUpdateService = binder.getService();
            locationUpdateService.updateNetworkLocation(
                    params.getExtras().getBoolean("byLastLocationOnly"),
                    null,
                    params.getExtras().getInt("attempts"));
            new Thread(new Runnable() {
                public void run() {
                    serviceConnected(locationUpdateServiceConnection);
                }
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locationUpdateService = null;
        }
    };
}
