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
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    // button to set the home screen wallpaper when clicked
    private Button bLoginButton;
    private Button bLogoutButton;
    private static Button bEnableTimer;
    private Button bSettingsButton;
    private TextView tAuthText, tErrorText;
    public static final String NOTIFICATION_CHANNEL_ID = "moe.seq.ads.mobile";
    public static final String channelName = "My Background Service";

    static SharedPreferences prefs;
    static int interval;
    static String serverName;
    static String displayName;
    static String folderName;
    static Boolean aspectRatio;
    static Boolean pinsOnly;
    static Boolean centerImage;
    static Boolean nsfwResults;
    static int maxAge;

    public static AlarmManager alarmManager;
    public static Intent alarmIntent;
    public static PendingIntent pendingIntent;
    public static Boolean alarmManagerActive = false;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        notificationManager.createNotificationChannel(channel);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        refreshSettings();

        channel.setLightColor(Color.YELLOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmIntent = new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE");
        pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alarmIntent, 0);

        final AuthWare authware = new AuthWare(MainActivity.this);

        bLoginButton = findViewById(R.id.loginButton);
        bEnableTimer = findViewById(R.id.startRefreshTimer);
        bSettingsButton = findViewById(R.id.settingsButton);

        tAuthText = findViewById(R.id.codeHolder);
        tErrorText = findViewById(R.id.errorText);

        SharedPreferences sharedPref = this.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
        SharedPreferences appPref = this.getSharedPreferences("seq.application", Context.MODE_PRIVATE);
        String storedLoginToken = sharedPref.getString("StaticLogin", null);
        String tokenLogin = "";
        Boolean timerEnabled = appPref.getBoolean("timerEnabled", false);


        if (!AuthWare.authComplete && storedLoginToken != null) {
            authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                @Override
                public void onError(String message) {
                    tErrorText.setText(String.format("Failed to login automatically: %s", message));
                    bLoginButton.setEnabled(true);
                    bEnableTimer.setEnabled(false);
                }

                @Override
                public void onResponse(Boolean loginSuccess, String authCode) {
                    if (!loginSuccess) {
                        bLoginButton.setEnabled(true);
                        bEnableTimer.setEnabled(false);
                    } else {
                        if (!isMyServiceRunning(AmbientService.class)) {
                            bEnableTimer.setText("Active");
                            startService(new Intent(MainActivity.this, AmbientService.class));
                            alarmManager.setInexactRepeating(AlarmManager.RTC, 100, interval, pendingIntent);
                            alarmManagerActive = true;
                        }
                        bEnableTimer.setEnabled(true);
                        tAuthText.setText("");
                        tErrorText.setText("");
                    }
                }
            });
        } else {
            bLoginButton.setEnabled(true);
        }
        if (timerEnabled) {

        }
        bLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!AuthWare.authComplete) {
                    authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                        @Override
                        public void onError(String message) {
                            tErrorText.setText(String.format("Failed to login: %s", message));
                            bLoginButton.setEnabled(true);
                            bEnableTimer.setEnabled(false);
                        }

                        @Override
                        public void onResponse(Boolean loginSuccess, String authCode) {
                            if (authCode != null) {
                                tAuthText.setText(authCode);
                            }
                            if (!loginSuccess) {
                                bLoginButton.setEnabled(true);
                                bEnableTimer.setEnabled(false);
                            } else {
                                bEnableTimer.setEnabled(true);
                                tAuthText.setText("");
                                tErrorText.setText("");
                            }
                        }
                    });
                }
            }
        });

        bLogoutButton = findViewById(R.id.logoutButton);
        bLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authware.clearLogin();
                bLoginButton.setEnabled(true);
                bEnableTimer.setEnabled(false);
                tAuthText.setText("......");
                tErrorText.setText("");
            }
        });

        bEnableTimer.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                if (isMyServiceRunning(AmbientService.class)) {
                    bEnableTimer.setText("Stopped");
                    stopService(new Intent(MainActivity.this, AmbientService.class));
                    if (alarmManager != null && alarmIntent != null && alarmManagerActive) {
                        alarmManager.cancel(pendingIntent);
                        alarmManagerActive = false;
                        Toast.makeText(MainActivity.this, "Timer Stopped", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    startService(new Intent(MainActivity.this, AmbientService.class));
                    alarmManager.setInexactRepeating(AlarmManager.RTC, 1000, interval, pendingIntent);
                    alarmManagerActive = true;
                }
            }
        });

        bSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new  Intent(MainActivity.this, SettingsActivity.class));
            }
        });


    }

    public static void toggleTimer() {
        if (alarmManager != null && pendingIntent != null && alarmManagerActive) {
            alarmManager.cancel(pendingIntent);
            Log.w("AlarmManager", "Auto Refresh Stopped");
            alarmManagerActive = false;
            bEnableTimer.setText("Paused");
        } else {
            assert alarmManager != null;
            alarmManager.setInexactRepeating(AlarmManager.RTC, 5 * 60 * 1000, interval, pendingIntent);
            alarmManagerActive = true;
            bEnableTimer.setText("Active");
            Log.w("AlarmManager", "Auto Refresh Started");
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

    public static void refreshSettings() {
        Log.w("Settings", String.format("Interval Time: %s", prefs.getString("ltCycleTime", "Default")));
        interval = Integer.parseInt(prefs.getString("ltCycleTime", "5")) * 60 * 1000;
        serverName = prefs.getString("etServerName", "seq.moe");
        displayName = prefs.getString("etDisplayName", "Untitled");
        folderName = prefs.getString("etFolder", "");
        aspectRatio = !prefs.getBoolean("swRatio", false);
        pinsOnly = prefs.getBoolean("swPins", false);
        centerImage = !prefs.getBoolean("swCenter", false);
        maxAge = Integer.parseInt(prefs.getString("ltMaxAge", "360"));
        nsfwResults = prefs.getBoolean("swNSFW", false);
    }
}