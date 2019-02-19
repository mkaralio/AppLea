package com.example.commontask;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.commontask.help.HelpActivity;
import com.example.commontask.model.Location;
import com.example.commontask.model.LocationsDbHelper;
import com.example.commontask.service.CurrentWeatherService;
import com.example.commontask.service.ForecastWeatherService;
import com.example.commontask.service.WeatherRequestDataHolder;
import com.example.commontask.utils.AppPreference;
import com.example.commontask.utils.ForecastUtil;
import com.example.commontask.utils.LanguageUtil;
import com.example.commontask.utils.PreferenceUtil;
import com.example.commontask.utils.Utils;
import com.example.commontask.view.activities.MainActivity;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.commontask.utils.LogToFile.appendLog;

public abstract class WeatherBaseActivity extends AppCompatActivity {

    private final String TAG = "WeatherBaseActivity";
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Toolbar mToolbar;
    private TextView mHeaderCity;
    protected LocationsDbHelper locationsDbHelper;
    private Messenger weatherForecastService;
    private Lock weatherForecastServiceLock = new ReentrantLock();
    private Queue<Message> weatherForecastUnsentMessages = new LinkedList<>();
    protected Location currentLocation;
    protected TextView localityView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindWeatherForecastService();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        getToolbar();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupNavDrawer();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageUtil.setLanguage(base, PreferenceUtil.getLanguage(base)));
    }

    public void switchLocation(View arg0) {
        int newLocationOrderId = 1 + currentLocation.getOrderId();
        currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);

        if (currentLocation == null) {
            newLocationOrderId = 0;
            currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);
            if ((currentLocation.getOrderId() == 0) && !currentLocation.isEnabled() && (locationsDbHelper.getAllRows().size() > 1)) {
                newLocationOrderId++;
                currentLocation = locationsDbHelper.getLocationByOrderId(newLocationOrderId);
            }
        }

        AppPreference.setCurrentLocationId(this, currentLocation.getId());
        localityView.setText(Utils.getCityAndCountry(this, newLocationOrderId));
        updateUI();
    }

    protected abstract void updateUI();

    private void setupNavDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (mDrawerLayout == null) {
            return;
        }
        mDrawerToggle = new ActionBarDrawerToggle(this,
                mDrawerLayout,
                mToolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        if (mToolbar != null) {
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mDrawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        configureNavView();
    }

    private void configureNavView() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(navigationViewListener);

        View headerLayout = navigationView.getHeaderView(0);
        mHeaderCity = (TextView) headerLayout.findViewById(R.id.nav_header_city);
        //mHeaderCity.setText(Utils.getCityAndCountry(this));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    private NavigationView.OnNavigationItemSelectedListener navigationViewListener =
            new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    switch (item.getItemId()) {

                        case R.id.nav_menu_current_weather:
                            startActivity(new Intent(getApplicationContext() ,MainWeatherAppActivity.class));
                            break;
                        case R.id.nav_menu_graphs:
                            createBackStack(new Intent(getApplicationContext(),
                                    GraphsActivity.class));
                            break;

                        case R.id.nav_menu_hours:
                            createBackStack(new Intent(getApplicationContext(),
                                    MainActivity.class));
                            break;

                        case R.id.nav_menu_weather_forecast:
                            createBackStack(new Intent(getApplicationContext(),
                                    WeatherForecastActivity.class));
                            break;

                        case R.id.nav_settings:
                            createBackStack(new Intent(getApplicationContext(),
                                    WeatherSettingsActivity.class));
                            break;
                        case R.id.nav_menu_help:
                            createBackStack(new Intent(getApplicationContext(),
                                    HelpActivity.class));
                            break;

                       /* case R.id.nav_feedback:
                            Intent sendMessage = new Intent(Intent.ACTION_SEND);
                            sendMessage.setType("message/rfc822");
                            sendMessage.putExtra(Intent.EXTRA_EMAIL, new String[]{
                                    getResources().getString(R.string.feedback_email)});
                            try {
                                startActivity(Intent.createChooser(sendMessage, "Send feedback"));
                            } catch (android.content.ActivityNotFoundException e) {
                                Toast.makeText(BaseActivityWeather.this, "Communication app not found",
                                        Toast.LENGTH_SHORT).show();
                            }
                            break;*/
                    }

                    mDrawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                }
            };

    private void createBackStack(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            TaskStackBuilder builder = TaskStackBuilder.create(this);
            builder.addNextIntentWithParentStack(intent);
            builder.startActivities();
        } else {
            startActivity(intent);
            finish();
        }
    }

    protected Toolbar getToolbar() {
        if (mToolbar == null) {
            mToolbar = (Toolbar) findViewById(R.id.toolbar);
            if (mToolbar != null) {
                setSupportActionBar(mToolbar);
            }
        }

        return mToolbar;
    }

    @Override
    public void onBackPressed() {
        if (isNavDrawerOpen()) {
            closeNavDraw();
        } else {
            startActivity(new Intent(getApplicationContext(), MainActivityCalendar.class));
        }
    }


    protected boolean isNavDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    protected void closeNavDraw() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @NonNull
    protected ProgressDialog getProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.isIndeterminate();
        dialog.setMessage(getString(R.string.load_progress));
        dialog.setCancelable(false);
        return dialog;
    }

    protected void sendMessageToWeatherForecastService(Long locationId) {
        if (!ForecastUtil.shouldUpdateForecast(this, locationId)) {
            return;
        }
        sendMessageToWeatherForecastService(locationId, null);
    }

    protected void sendMessageToWeatherForecastService(Long locationId, String updateSource) {
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

    private boolean checkIfWeatherForecastServiceIsNotBound() {
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
        }
        public void onServiceDisconnected(ComponentName className) {
            weatherForecastService = null;
        }
    };
}

