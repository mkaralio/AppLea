package com.example.commontask.service;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.example.commontask.model.Location;
import com.example.commontask.utils.ForecastUtil;
import com.example.commontask.utils.Utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.commontask.utils.LogToFile.appendLog;

@TargetApi(Build.VERSION_CODES.M)
public abstract class AbstractAppJob extends JobService {

    private static final String TAG = "AbstractAppJob";

    protected Messenger currentWeatherService;
    protected Lock currentWeatherServiceLock = new ReentrantLock();
    protected Queue<Message> currentWeatherUnsentMessages = new LinkedList<>();
    protected Messenger weatherForecastService;
    protected Lock weatherForecastServiceLock = new ReentrantLock();
    protected Queue<Message> weatherForecastUnsentMessages = new LinkedList<>();
    private Messenger wakeUpService;
    private Lock wakeUpServiceLock = new ReentrantLock();
    private Queue<Message> wakeUpUnsentMessages = new LinkedList<>();

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    protected void reScheduleNextAlarm(int jobId, String updatePeriodStr, Class serviceClass) {
        long updateAutoPeriodMills = Utils.intervalMillisForAlarm(updatePeriodStr);
        reScheduleNextAlarm(jobId, updateAutoPeriodMills, serviceClass);
    }

    protected void reScheduleNextAlarm(int jobId, long updatePeriod, Class serviceClass) {
        appendLog(getBaseContext(), TAG, "next alarm:", updatePeriod,
                ", serviceClass=", serviceClass);
        ComponentName serviceComponent = new ComponentName(this, serviceClass);
        JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
        builder.setMinimumLatency(updatePeriod); // wait at least
        builder.setOverrideDeadline(updatePeriod + (3 * 1000)); // maximum delay
        JobScheduler jobScheduler = getSystemService(JobScheduler.class);
        jobScheduler.schedule(builder.build());
    }

    protected void sendMessageToWeatherForecastService(long locationId) {
        sendMessageToWeatherForecastService(locationId, null);
    }

    protected void sendMessageToWeatherForecastService(long locationId, String updateSource) {
        if (!ForecastUtil.shouldUpdateForecast(this, locationId)) {
            return;
        }
        weatherForecastServiceLock.lock();
        try {
            Message msg = Message.obtain(
                    null,
                    ForecastWeatherService.START_WEATHER_FORECAST_UPDATE,
                    new WeatherRequestDataHolder(locationId, updateSource)
            );
            if (checkIfWeatherForecastServiceIsNotBound()) {
                //appendLog(getBaseContext(), TAG, "WidgetIconService is still not bound");
                weatherForecastUnsentMessages.add(msg);
                return;
            }
            //appendLog(getBaseContext(), TAG, "sendMessageToService:");
            weatherForecastService.send(msg);
        } catch (RemoteException e) {
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            weatherForecastServiceLock.unlock();
        }
    }

    protected void unbindAllServices() {
        unbindCurrentWeatherService();
        unbindWeatherForecastService();
        unbindWakeUpService();
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        try {
            super.unbindService(conn);
        } catch (Exception e) {
            appendLog(this, "TAG", e.getMessage(), e);
        }
    }

    protected boolean checkIfWeatherForecastServiceIsNotBound() {
        if (weatherForecastService != null) {
            return false;
        }
        try {
            bindWeatherForecastService();
        } catch (Exception ie) {
            appendLog(getBaseContext(), TAG, "weatherForecastServiceIsNotBound interrupted:", ie);
        }
        return (weatherForecastService == null);
    }

    private void bindWeatherForecastService() {
        getApplicationContext().bindService(
                new Intent(getApplicationContext(), ForecastWeatherService.class),
                weatherForecastServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void unbindWeatherForecastService() {
        if (weatherForecastService == null) {
            return;
        }
        getApplicationContext().unbindService(weatherForecastServiceConnection);
    }

    private ServiceConnection weatherForecastServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binderService) {
            weatherForecastService = new Messenger(binderService);
            weatherForecastServiceLock.lock();
            try {
                while (!weatherForecastUnsentMessages.isEmpty()) {
                    weatherForecastService.send(weatherForecastUnsentMessages.poll());
                }
            } catch (RemoteException e) {
                appendLog(getBaseContext(), TAG, e.getMessage(), e);
            } finally {
                weatherForecastServiceLock.unlock();
            }
            serviceConnected(currentWeatherServiceConnection);
        }
        public void onServiceDisconnected(ComponentName className) {
            weatherForecastService = null;
        }
    };

    protected void sendMessageToCurrentWeatherService(Location location,
                                                      int wakeUpSource,
                                                      boolean updateWeatherOnly) {
        sendMessageToCurrentWeatherService(location, null, wakeUpSource, updateWeatherOnly);
    }

    protected void sendMessageToCurrentWeatherService(Location location,
                                                      String updateSource,
                                                      int wakeUpSource,
                                                      boolean updateWeatherOnly) {
        sendMessageToWakeUpService(
                AppWakeUpManager.WAKE_UP,
                wakeUpSource
        );
        currentWeatherServiceLock.lock();
        try {
            Message msg = Message.obtain(
                    null,
                    CurrentWeatherService.START_CURRENT_WEATHER_UPDATE,
                    new WeatherRequestDataHolder(location.getId(), updateSource, updateWeatherOnly)
            );
            if (checkIfCurrentWeatherServiceIsNotBound()) {
                currentWeatherUnsentMessages.add(msg);
                return;
            }
            currentWeatherService.send(msg);
            serviceConnected(currentWeatherServiceConnection);
        } catch (RemoteException e) {
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            currentWeatherServiceLock.unlock();
        }
    }

    protected boolean checkIfCurrentWeatherServiceIsNotBound() {
        if (currentWeatherService != null) {
            return false;
        }
        try {
            bindCurrentWeatherService();
        } catch (Exception ie) {
            appendLog(getBaseContext(), TAG, "currentWeatherServiceIsNotBound interrupted:", ie);
        }
        return (currentWeatherService == null);
    }

    private void bindCurrentWeatherService() {
        appendLog(getBaseContext(), getClass().getSimpleName(), "bind current weather service:", this.toString());
        getApplicationContext().bindService(
                new Intent(getApplicationContext(), CurrentWeatherService.class),
                currentWeatherServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void unbindCurrentWeatherService() {
        if (currentWeatherService == null) {
            return;
        }
        getApplicationContext().unbindService(currentWeatherServiceConnection);
    }

    private ServiceConnection currentWeatherServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binderService) {
            currentWeatherService = new Messenger(binderService);
            currentWeatherServiceLock.lock();
            try {
                while (!currentWeatherUnsentMessages.isEmpty()) {
                    currentWeatherService.send(currentWeatherUnsentMessages.poll());
                }
            } catch (RemoteException e) {
                appendLog(getBaseContext(), TAG, e.getMessage(), e);
            } finally {
                currentWeatherServiceLock.unlock();
            }
            serviceConnected(currentWeatherServiceConnection);
        }
        public void onServiceDisconnected(ComponentName className) {
            currentWeatherService = null;
        }
    };

    protected abstract void serviceConnected(ServiceConnection serviceConnection);

    protected void sendMessageToWakeUpService(int wakeAction, int wakeupSource) {
        wakeUpServiceLock.lock();
        try {
            Message msg = Message.obtain(
                    null,
                    wakeAction,
                    wakeupSource,
                    0
            );
            if (checkIfWakeUpServiceIsNotBound()) {
                wakeUpUnsentMessages.add(msg);
                return;
            }
            wakeUpService.send(msg);
        } catch (RemoteException e) {
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            wakeUpServiceLock.unlock();
        }
    }

    private boolean checkIfWakeUpServiceIsNotBound() {
        if (wakeUpService != null) {
            return false;
        }
        try {
            bindWakeUpService();
        } catch (Exception ie) {
            appendLog(getBaseContext(), TAG, "currentWeatherServiceIsNotBound interrupted:", ie);
        }
        return (wakeUpService == null);
    }

    private void bindWakeUpService() {
        appendLog(getBaseContext(), getClass().getSimpleName(), "bind wakeup service:", this.toString());
        getApplicationContext().bindService(
                new Intent(getApplicationContext(), AppWakeUpManager.class),
                wakeUpServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    private void unbindWakeUpService() {
        if (wakeUpService == null) {
            return;
        }
        getApplicationContext().unbindService(wakeUpServiceConnection);
    }

    private ServiceConnection wakeUpServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binderService) {
            wakeUpService = new Messenger(binderService);
            wakeUpServiceLock.lock();
            try {
                while (!wakeUpUnsentMessages.isEmpty()) {
                    wakeUpService.send(wakeUpUnsentMessages.poll());
                }
            } catch (RemoteException e) {
                appendLog(getBaseContext(), TAG, e.getMessage(), e);
            } finally {
                wakeUpServiceLock.unlock();
            }
            serviceConnected(currentWeatherServiceConnection);
        }
        public void onServiceDisconnected(ComponentName className) {
            wakeUpService = null;
        }
    };
}
