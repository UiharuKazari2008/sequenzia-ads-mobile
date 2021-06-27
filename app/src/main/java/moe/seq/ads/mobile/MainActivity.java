package moe.seq.ads.mobile;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    // button to set the home screen wallpaper when clicked
    private Button bLoginButton;
    private Button bEnableTimer;
    private ImageView bDownloadNowButton;
    private TextView tAuthText, tErrorText;
    public static final String NOTIFICATION_CHANNEL_ID_1 = "moe.seq.ads.mobile.display.1";
    public static final String NOTIFICATION_CHANNEL_ID_2 = "moe.seq.ads.mobile.display.2";
    public static final String channelName1 = "Ambient Display Service - Wallpaper";
    public static final String channelName2 = "Ambient Display Service - Lockscreen";

    static SharedPreferences prefs;

    public static Boolean[] flipBoardEnabled = new Boolean[] {false, false};
    public static Boolean[] pendingRefresh = new Boolean[] {false, false};
    public static Boolean[] pendingTimeReset = new Boolean[] {false, false};
    public static long[] lastChangeTime = new long[] {0, 0};

    public AuthWare authware;
    private SharedPreferences authPref;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        androidx.appcompat.app.ActionBar actionBar = this.getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.action_bar_main);
        ColorDrawable colorDrawable
                = new ColorDrawable(Color.parseColor("#FF9800"));

        // Set BackgroundDrawable
        actionBar.setBackgroundDrawable(colorDrawable);

        //getSupportActionBar().setElevation(0);
        View view = getSupportActionBar().getCustomView();

        bDownloadNowButton = view.findViewById(R.id.download_button);
        ImageView bSettingsButton = view.findViewById(R.id.settings_button);

        // Notification Manager
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        NotificationChannel channel1 = new NotificationChannel(NOTIFICATION_CHANNEL_ID_1, channelName1, NotificationManager.IMPORTANCE_MIN);
        notificationManager.createNotificationChannel(channel1);
        channel1.setLightColor(Color.YELLOW);
        channel1.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel1.setDescription("Foreground Service for Ambient Display Service");

        NotificationChannel channel2 = new NotificationChannel(NOTIFICATION_CHANNEL_ID_2, channelName2, NotificationManager.IMPORTANCE_MIN);
        notificationManager.createNotificationChannel(channel2);
        channel2.setLightColor(Color.YELLOW);
        channel2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel2.setDescription("Foreground Service for Ambient Display Service");

        // Application Settings Manager
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        authPref = this.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);

        // Generate New AuthWare
        authware = new AuthWare(MainActivity.this);

        bLoginButton = findViewById(R.id.loginButton);
        bEnableTimer = findViewById(R.id.startRefreshTimer);
        //bDownloadNowButton = findViewById(R.id.downloadImages);
        //Button bSettingsButton = findViewById(R.id.settingsButton);
        tAuthText = findViewById(R.id.codeHolder);
        tErrorText = findViewById(R.id.errorText);

        if (flipBoardEnabled[0] || flipBoardEnabled[1]) {
            bEnableTimer.setText("Stop");
            bEnableTimer.setEnabled(true);
            bDownloadNowButton.setEnabled(true);
            bLoginButton.setEnabled(false);
        } else {
            bEnableTimer.setText("Start");
        }

        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            String _sType = uri.getQueryParameter("setType");
            if (_sType != null && _sType.length() > 0) {
                SharedPreferences settingsPrefs = null;
                SharedPreferences.Editor prefsEditor = prefs.edit();
                String typeOfImport = "";
                switch (_sType) {
                    case "0":
                        settingsPrefs = this.getSharedPreferences("seq.settings.wallpaper", Context.MODE_PRIVATE);
                        typeOfImport=  "Synced Wallpaper & Lockscreen";
                        break;
                    case "1":
                        settingsPrefs = this.getSharedPreferences("seq.settings.wallpaper", Context.MODE_PRIVATE);
                        typeOfImport=  "Wallpaper";
                        if (prefs.getBoolean("swSyncWallpaper", false)) {
                            SharedPreferences wallPrefs = this.getSharedPreferences("seq.settings.wallpaper", Context.MODE_PRIVATE);
                            SharedPreferences lockPrefs = this.getSharedPreferences("seq.settings.lockscreen", Context.MODE_PRIVATE);
                            Map<String, ?> lockSettings = lockPrefs.getAll();
                            Map<String, ?> wallSettings = wallPrefs.getAll();
                            ArrayList<String> newWallSettings = new ArrayList<>();
                            for (Map.Entry<String, ?> entry: wallSettings.entrySet()) {
                                lockPrefs.edit().putString(entry.getKey(), entry.getValue().toString()).apply();
                                newWallSettings.add(entry.getKey());
                            }
                            for (Map.Entry<String, ?> entry: lockSettings.entrySet()) {
                                if (!newWallSettings.contains(entry.getKey())){
                                    lockPrefs.edit().remove(entry.getKey()).apply();
                                }
                            }
                        }
                        break;
                    case "2":
                        settingsPrefs = this.getSharedPreferences("seq.settings.lockscreen", Context.MODE_PRIVATE);
                        typeOfImport=  "Lockscreen";
                        break;
                    default:
                        Toast.makeText(MainActivity.this, "Invalid Setting Type from Sequenzia Web", Toast.LENGTH_SHORT).show();
                        break;
                }
                if (settingsPrefs != null) {
                    SharedPreferences finalSettingsPrefs = settingsPrefs;
                    Log.i("WebLoader", String.format("Got URI: %s", intent.getData()));

                    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                    dialog.setMessage(String.format("Import %s from Sequenzia Web? This will replace existing settings!", typeOfImport));
                    dialog.setTitle("Setup ADS");
                    dialog.setPositiveButton("Import",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    prefsEditor.putBoolean("swSyncWallpaper", (_sType.equals("0"))).apply();

                                    if (isMyServiceRunning(AmbientService.class, MainActivity.this)) {
                                        stopService(new Intent(MainActivity.this, AmbientService.class));
                                    }
                                    bEnableTimer.setEnabled(false);
                                    SharedPreferences.Editor settings = finalSettingsPrefs.edit();

                                    String _minRes = uri.getQueryParameter("minres");
                                    String _imageRatio = uri.getQueryParameter("ratio");
                                    String _folderName = uri.getQueryParameter("folder");
                                    String _albumId = uri.getQueryParameter("album");
                                    String _searchQuery = uri.getQueryParameter("search");
                                    String _maxAge = uri.getQueryParameter("numdays");
                                    String _pinsOnly = uri.getQueryParameter("pins");
                                    String _nsfwEnabled = uri.getQueryParameter("nsfw");
                                    String _colorSelect = uri.getQueryParameter("brightness");
                                    String _serverName = uri.getQueryParameter("server_hostname");

                                    if (_serverName != null && _serverName.length() > 0) {
                                        prefsEditor.putString("etServerName", _serverName).apply();
                                    }
                                    if (_folderName != null && _folderName.length() > 0) {
                                        settings.putString("folder", _folderName);
                                    } else {
                                        settings.remove("folder");
                                    }
                                    if (_albumId != null && _albumId.length() > 0) {
                                        settings.putString("album", _albumId);
                                    } else {
                                        settings.remove("album");
                                    }
                                    if (_searchQuery != null && _searchQuery.length() > 0) {
                                        settings.putString("search", _searchQuery);
                                    } else {
                                        settings.remove("search");
                                    }
                                    if (_imageRatio != null && _imageRatio.length() > 0) {
                                        settings.putString("ratio", _imageRatio);
                                    } else {
                                        settings.putString("ratio", "0.9-2.1");
                                    }
                                    if (_minRes != null && _minRes.length() > 0) {
                                        settings.putString("minRes", _minRes);
                                    } else {
                                        settings.remove("minRes");
                                    }
                                    if (_colorSelect != null && _colorSelect.length() > 0) {
                                        settings.putString("bright", _colorSelect);
                                    } else {
                                        settings.remove("bright");
                                    }
                                    if (_pinsOnly != null && _pinsOnly.length() > 0) {
                                        settings.putString("pins", _pinsOnly);
                                    } else {
                                        settings.remove("pins");
                                    }
                                    if (_nsfwEnabled != null && _nsfwEnabled.length() > 0) {
                                        settings.putString("nsfwEnabled", _nsfwEnabled);
                                    } else {
                                        settings.putString("nsfwEnabled", "false");
                                    }
                                    if (_maxAge != null && _maxAge.length() > 0) {
                                        settings.putString("maxAge", _maxAge);
                                    } else {
                                        settings.remove("maxAge");
                                    }

                                    settings.apply();

                                    lastChangeTime[0] = 0;
                                    lastChangeTime[1] = 0;
                                    flipBoardEnabled[0] = true;
                                    flipBoardEnabled[1] = true;
                                    Handler hander = new Handler();
                                    final TimerTask callRefresh = new TimerTask() {
                                        @Override
                                        public void run() {
                                            // post a runnable to the handler
                                            hander.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Log.i("TimerEvent", "Restarting Service!");
                                                    sendBroadcast(new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction("REFRESH_IMAGES"));
                                                    bEnableTimer.setEnabled(true);
                                                }
                                            });
                                        }
                                    };
                                    new Timer().schedule(callRefresh, 1000);
                                }
                            });
                    dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    AlertDialog alertDialog = dialog.create();
                    alertDialog.show();
                }
            }
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
                            if (!loginSuccess) {
                                AlertDialog.Builder dialog=new AlertDialog.Builder(MainActivity.this);
                                if (authCode != null) {
                                    dialog.setMessage(String.format("Login with Express Code: %s", authCode));
                                } else {
                                    dialog.setMessage("Unable to get express code for login, use browser login");
                                }
                                dialog.setTitle("Login");
                                dialog.setPositiveButton("Login",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                attemptLogin();
                                            }
                                        });
                                if (sessionId != null) {
                                    dialog.setNeutralButton("via Browser",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Uri fileURL = Uri.parse(String.format("https://%s/transfer?type=0&deviceID=%s", prefs.getString("etServerName", "seq.moe"), sessionId));
                                                    MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, fileURL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                                }
                                            });
                                }

                                dialog.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                });
                                AlertDialog alertDialog = dialog.create();
                                alertDialog.show();
                                bLoginButton.setEnabled(true);
                                bDownloadNowButton.setEnabled(false);
                            } else {
                                bEnableTimer.setEnabled(true);
                                bDownloadNowButton.setEnabled(true);
                                bLoginButton.setEnabled(false);
                                tErrorText.setText("");
                            }
                        }
                    });
                } else {
                    bLoginButton.setEnabled(false);
                    bEnableTimer.setEnabled(true);
                    bDownloadNowButton.setEnabled(true);
                    tErrorText.setText("");
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
                if (isMyServiceRunning(AmbientService.class, MainActivity.this)) {
                    stopService(new Intent(MainActivity.this, AmbientService.class));
                    if (flipBoardEnabled[0] || flipBoardEnabled[1]) {
                        lastChangeTime[0] = 0;
                        lastChangeTime[1] = 0;
                        flipBoardEnabled[0] = false;
                        flipBoardEnabled[1] = false;
                    }
                    bEnableTimer.setText("Start");
                } else {
                    startService(new Intent(MainActivity.this, AmbientService.class));
                    bEnableTimer.setText("Disable");
                }
            }
        });

        // Download Button
        bDownloadNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction("REFRESH_IMAGES"));
                bEnableTimer.setText("Disable");
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

    public static void toggleTimer(Boolean timerSelection) {
        flipBoardEnabled[(timerSelection) ? 0 : 1] = !flipBoardEnabled[(timerSelection) ? 0 : 1];
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
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
                    } else {
                        bLoginButton.setEnabled(false);
                        if (!isMyServiceRunning(AmbientService.class, MainActivity.this)) {
                            bEnableTimer.setText("Stop");
                            startForegroundService(new Intent(MainActivity.this, AmbientService.class));
                        }
                        bEnableTimer.setEnabled(true);
                        bDownloadNowButton.setEnabled(true);
                    }
                }
            });
        } else {
            bLoginButton.setEnabled(true);
        }
    }
}