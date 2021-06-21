package moe.seq.ads.mobile;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    // button to set the home screen wallpaper when clicked
    private Button bLoginButton;
    private Button bEnableTimer;
    private Button bDownloadNowButton;
    private TextView tAuthText, tErrorText;
    public static final String NOTIFICATION_CHANNEL_ID = "moe.seq.ads.mobile.display";
    public static final String channelName = "Ambient Display Service";

    static SharedPreferences prefs;
    static int interval;
    static String serverName;
    static String displayName;
    static String folderName;
    static String searchQuery;
    static Boolean aspectRatio;
    static Boolean pinsOnly;
    static Boolean centerImage;
    static Boolean nsfwResults;
    static Boolean enableHistory;
    static int maxAge;
    static int imagesToKeep;
    static int wallSelection;

    public static AlarmManager alarmManager;
    public static Intent alarmIntent;
    public static PendingIntent pendingIntent;
    public static Boolean alarmManagerActive = false;
    public static Boolean pendingIntentActive = false;

    public AuthWare authware;
    private SharedPreferences authPref;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Notification Manager
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        notificationManager.createNotificationChannel(channel);
        channel.setLightColor(Color.YELLOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setDescription("Foreground Service for Ambient Display Service");

        // Application Settings Manager
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        refreshSettings();
        authPref = this.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);

        // Alarm Manager for Recurring Refresh
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmIntent = new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE");
        pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alarmIntent, 0);

        // Generate New AuthWare
        authware = new AuthWare(MainActivity.this);

        bLoginButton = findViewById(R.id.loginButton);
        bEnableTimer = findViewById(R.id.startRefreshTimer);
        bDownloadNowButton = findViewById(R.id.downloadImages);
        Button bSettingsButton = findViewById(R.id.settingsButton);
        tAuthText = findViewById(R.id.codeHolder);
        tErrorText = findViewById(R.id.errorText);

        if (isMyServiceRunning(AmbientService.class) || (alarmManager != null && alarmIntent != null && alarmManagerActive)) {
            bEnableTimer.setText("Stop Service");
            bLoginButton.setEnabled(false);
        } else {
            bEnableTimer.setText("Start Service");
        }

        // Attempt to login
        attemptLogin();
        bLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!AuthWare.authComplete) {
                    authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                        @Override
                        public void onError(String message) {
                            tErrorText.setText(String.format("Failed to login: %s", message));
                            bLoginButton.setEnabled(true);
                        }

                        @Override
                        public void onResponse(Boolean loginSuccess, String authCode, String sessionId) {
                            if (authCode != null) {
                                tAuthText.setText(authCode);
                                tAuthText.setVisibility(View.VISIBLE);
                            } else {
                                tAuthText.setText("");
                                tAuthText.setVisibility(View.GONE);
                            }
                            if (sessionId != null) {
                                // Add Manual Login Button Here
                            }
                            if (!loginSuccess) {
                                bLoginButton.setEnabled(true);
                            } else {
                                tErrorText.setText("");
                            }
                        }
                    });
                } else {
                    bLoginButton.setEnabled(false);
                }
            }
        });

        Button bLogoutButton = findViewById(R.id.logoutButton);
        bLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authware.clearLogin();
                bLoginButton.setEnabled(true);
                tAuthText.setText("......");
                tErrorText.setText("");
                AuthWare.authComplete = false;
            }
        });

        // Timer Button
        bEnableTimer.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                if (isMyServiceRunning(AmbientService.class)) {
                    stopService(new Intent(MainActivity.this, AmbientService.class));
                    if (alarmManager != null && alarmIntent != null && alarmManagerActive) {
                        alarmManager.cancel(pendingIntent);
                        alarmManagerActive = false;
                    }
                    bEnableTimer.setText("Start Service");
                } else {
                    startService(new Intent(MainActivity.this, AmbientService.class));
                    bEnableTimer.setText("Disable Service");
                }
            }
        });

        // Download Button
        bDownloadNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction("REFRESH_IMAGES"));
            }
        });

        // Open Settings Page
        bSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new  Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    public static void toggleTimer() {
        if (alarmManager != null && pendingIntent != null && alarmManagerActive) {
            stopTimer();
        } else {
            startTimer(5 * 60 * 1000);
        }
    }

    public static void stopTimer() {
        if (alarmManager != null && pendingIntent != null && alarmManagerActive) {
            alarmManager.cancel(pendingIntent);
            Log.i("AlarmManager", "Auto Refresh Stopped");
            alarmManagerActive = false;
        }
    }

    public static void startTimer(int triggerAt) {
        if (!(alarmManager != null && pendingIntent != null && alarmManagerActive)) {
            assert alarmManager != null;
            alarmManager.setInexactRepeating(AlarmManager.RTC, triggerAt, interval, pendingIntent);
            Log.i("AlarmManager", String.format("Auto Refresh Restarted with %s Delay", triggerAt));
            alarmManagerActive = true;
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void attemptLogin () {
        if (!AuthWare.authComplete && authPref.getString("StaticLogin", null) != null) {
            authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                @Override
                public void onError(String message) {
                    tErrorText.setText(String.format("Failed to login: %s", message));
                    bLoginButton.setEnabled(true);
                }

                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onResponse(Boolean loginSuccess, String authCode, String sessionId) {
                    if (!loginSuccess) {
                        bLoginButton.setEnabled(true);
                        tAuthText.setText("......");
                        tAuthText.setVisibility(View.VISIBLE);
                    } else {
                        bLoginButton.setEnabled(false);
                        if (!isMyServiceRunning(AmbientService.class)) {
                            bEnableTimer.setText("Disable Service");
                            startForegroundService(new Intent(MainActivity.this, AmbientService.class));
                            alarmManager.setInexactRepeating(AlarmManager.RTC, 5000, interval, pendingIntent);
                            alarmManagerActive = true;
                        }
                        tAuthText.setText("");
                        tAuthText.setVisibility(View.GONE);
                        tErrorText.setText("");
                    }
                }
            });
        } else {
            bLoginButton.setEnabled(true);
        }
    }

    public static void refreshSettings() {
        Log.i("Settings", String.format("Interval Time: %s", prefs.getString("ltCycleTime", "Default")));
        interval = Integer.parseInt(prefs.getString("ltCycleTime", "60")) * 60 * 1000;
        serverName = prefs.getString("etServerName", "beta.seq.moe");
        displayName = prefs.getString("etDisplayName", "Untitled");
        folderName = prefs.getString("etFolder", "");
        searchQuery = prefs.getString("etSearch", "");
        aspectRatio = !prefs.getBoolean("swRatio", false);
        pinsOnly = prefs.getBoolean("swPins", false);
        centerImage = prefs.getBoolean("swCenter", false);
        enableHistory = prefs.getBoolean("swHistory", true);
        wallSelection = Integer.parseInt(prefs.getString("ltWallSelection", "0"));
        maxAge = Integer.parseInt(prefs.getString("ltMaxAge", "0"));
        imagesToKeep = Integer.parseInt(prefs.getString("ltImageCount", "5"));
        nsfwResults = prefs.getBoolean("swNSFW", false);
    }
}