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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.*;

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
        Button bEditor = findViewById(R.id.editConfigFile);
        Button bLogoutButton = findViewById(R.id.logoutButton);
        Button bRefreshButton = findViewById(R.id.refreshButton);
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
                String settingsName = null;
                String typeOfImport = "";
                switch (_sType) {
                    case "0":
                        settingsName = "wallpaper";
                        typeOfImport=  "Synced Wallpaper & Lockscreen";
                        break;
                    case "1":
                        settingsName = "wallpaper";
                        typeOfImport=  "Wallpaper";
                        break;
                    case "2":
                        settingsName = "lockscreen";
                        typeOfImport=  "Lockscreen";
                        break;
                    default:
                        Toast.makeText(MainActivity.this, "Invalid Setting Type from Sequenzia Web", Toast.LENGTH_SHORT).show();
                        break;
                }
                if (settingsName != null) {

                    Log.i("WebLoader", String.format("Got URI: %s", intent.getData()));

                    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                    dialog.setMessage(String.format("Import %s from Sequenzia Web? This will replace existing settings!", typeOfImport));
                    String finalSettingsName = settingsName;
                    dialog.setPositiveButton("Import", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (prefs.getBoolean("swCycleConfig", false)){
                                        final Boolean[] nightMode = {false};
                                        AlertDialog.Builder nightDialog = new AlertDialog.Builder(MainActivity.this);
                                        nightDialog.setTitle("What Time?");
                                        nightDialog.setSingleChoiceItems(R.array.configTime, 0,
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        nightMode[0] = (which == 1);
                                                    }
                                        });
                                        nightDialog.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                importUri(uri, finalSettingsName, nightMode[0], _sType);
                                            }
                                        });
                                        nightDialog.setNeutralButton("Disable", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                SharedPreferences.Editor prefsEditor = prefs.edit();
                                                prefsEditor.putBoolean("swCycleConfig", false).apply();
                                                importUri(uri, finalSettingsName, false, _sType);
                                            }
                                        });
                                        nightDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                Toast.makeText(MainActivity.this, "Aborted, No Changes Made", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        AlertDialog nightAlertDialog = nightDialog.create();
                                        nightAlertDialog.show();
                                    } else {
                                        importUri(uri, finalSettingsName, false, _sType);
                                    }
                                }
                            });
                    dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(MainActivity.this, "Aborted, No Changes Made", Toast.LENGTH_SHORT).show();
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

        bRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authware.refreshSession(new AuthWare.AuthWareRefreshAccount() {
                    @Override
                    public void onError(String message) {
                        Toast.makeText(MainActivity.this, String.format("Failed to refresh account: %s", message), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResponse(Boolean ok) {
                        if (ok) {
                            Toast.makeText(MainActivity.this, "Refreshed User Account", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
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

        // Open File Editor
        bEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] configOptions;
                List<String> menuOptions = new ArrayList<String>();

                if (prefs.getBoolean("swSyncWallpaper", true)) {
                    menuOptions.add("wallpaper");
                    if (prefs.getBoolean("swCycleConfig", false)) {
                        menuOptions.add("wallpaper.night");
                    }
                } else {
                    menuOptions.add("wallpaper");
                    menuOptions.add("lockscreen");
                    if (prefs.getBoolean("swCycleConfig", false)) {
                        menuOptions.add("wallpaper.night");
                        menuOptions.add("lockscreen.night");
                    }
                }
                configOptions = menuOptions.toArray(new String[menuOptions.size()]);

                String[] selectedConfig = new String[]{ "seq.settings.wallpaper" };
                AlertDialog.Builder selectFileDialog = new AlertDialog.Builder(v.getContext());
                selectFileDialog.setTitle("Select Configuration");
                selectFileDialog.setSingleChoiceItems(configOptions, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        selectedConfig[0] = String.format("seq.settings.%s", configOptions[which]);
                    }
                });
                selectFileDialog.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.v("FileEditor", String.format("Edit File: %s", selectedConfig[0]));
                        SharedPreferences settingsPrefs = v.getContext().getSharedPreferences(selectedConfig[0], Context.MODE_PRIVATE);

                        LayoutInflater inflater = getLayoutInflater();
                        View alertLayout = inflater.inflate(R.layout.layout_file_editor_dialog, null);
                        final String[] folderName = {settingsPrefs.getString("folder", "")};
                        final String[] albumName = {settingsPrefs.getString("album", "")};
                        final String[] searchQuery = {settingsPrefs.getString("search", "")};
                        final String[] aspectRatio = {settingsPrefs.getString("ratio", "")};
                        final String[] pinsOnly = {settingsPrefs.getString("pins", "")};
                        final String[] maxAge = {settingsPrefs.getString("maxAge", "")};
                        final String[] darkSelection = {settingsPrefs.getString("bright", "")};
                        final String[] colorSelection = {settingsPrefs.getString("color", "")};
                        final String[] minFResolution = {settingsPrefs.getString("minRes", "")};
                        final String[] minHResolution = {settingsPrefs.getString("minHRes", "")};
                        final String[] minWResolution = {settingsPrefs.getString("minWRes", "")};
                        final String[] nsfwResults = {settingsPrefs.getString("nsfwEnabled", "false")};

                        TextInputEditText etFolderName = alertLayout.findViewById(R.id.etFolderName);
                        etFolderName.setText(folderName[0]);
                        etFolderName.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { folderName[0] = s.toString(); }
                        });
                        TextInputEditText etAlbumName = alertLayout.findViewById(R.id.etAlbumName);
                        etAlbumName.setText(albumName[0]);
                        etAlbumName.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { albumName[0] = s.toString(); }
                        });
                        TextInputEditText etSearchQuery = alertLayout.findViewById(R.id.etSearchQuery);
                        etSearchQuery.setText(searchQuery[0]);
                        etSearchQuery.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { searchQuery[0] = s.toString(); }
                        });
                        TextInputEditText etAspectRatio = alertLayout.findViewById(R.id.etAspectRatio);
                        etAspectRatio.setText(aspectRatio[0]);
                        etAspectRatio.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { aspectRatio[0] = s.toString(); }
                        });
                        TextInputEditText etPinsOnly = alertLayout.findViewById(R.id.etPinsOnly);
                        etPinsOnly.setText(pinsOnly[0]);
                        etPinsOnly.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { pinsOnly[0] = s.toString(); }
                        });
                        TextInputEditText etMaxAge = alertLayout.findViewById(R.id.etMaxAge);
                        etMaxAge.setText(maxAge[0]);
                        etMaxAge.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { maxAge[0] = s.toString(); }
                        });
                        TextInputEditText etDarkSelection = alertLayout.findViewById(R.id.etDarkSelection);
                        etDarkSelection.setText(darkSelection[0]);
                        etDarkSelection.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { darkSelection[0] = s.toString(); }
                        });
                        TextInputEditText etColorSelection = alertLayout.findViewById(R.id.etColorSelection);
                        etColorSelection.setText(colorSelection[0]);
                        etColorSelection.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { colorSelection[0] = s.toString(); }
                        });
                        TextInputEditText etMinFResolution = alertLayout.findViewById(R.id.etMinFResolution);
                        etMinFResolution.setText(minFResolution[0]);
                        etMinFResolution.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { minFResolution[0] = s.toString(); }
                        });
                        TextInputEditText etMinHResolution = alertLayout.findViewById(R.id.etMinHResolution);
                        etMinHResolution.setText(minHResolution[0]);
                        etMinHResolution.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { minHResolution[0] = s.toString(); }
                        });
                        TextInputEditText etMinWResolution = alertLayout.findViewById(R.id.etMinWResolution);
                        etMinWResolution.setText(minWResolution[0]);
                        etMinWResolution.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { minWResolution[0] = s.toString(); }
                        });
                        TextInputEditText etNsfwResults = alertLayout.findViewById(R.id.etNsfwResults);
                        etNsfwResults.setText(nsfwResults[0]);
                        etNsfwResults.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) { }

                            @Override
                            public void afterTextChanged(Editable s) { nsfwResults[0] = s.toString(); }
                        });

                        AlertDialog.Builder fileEditorBuilder = new AlertDialog.Builder(v.getContext());
                        fileEditorBuilder.setTitle(String.format("Editor: %s", selectedConfig[0]));
                        // this is set the view from XML inside AlertDialog
                        fileEditorBuilder.setView(alertLayout);
                        // disallow cancel of AlertDialog on click of back button and outside touch
                        fileEditorBuilder.setCancelable(false);
                        fileEditorBuilder.setNegativeButton("Cancel", (fileDialog, fileW) -> Toast.makeText(getBaseContext(), "Edit Aborted", Toast.LENGTH_SHORT).show());

                        fileEditorBuilder.setPositiveButton("Save", (fileDialog, fileW) -> {
                            SharedPreferences.Editor editor = settingsPrefs.edit();
                            if(folderName[0].length() > 0) {
                                editor.putString("folder", folderName[0]).apply();
                            } else {
                                editor.remove("folder").apply();
                            }
                            if(albumName[0].length() > 0) {
                                editor.putString("album", albumName[0]).apply();
                            } else {
                                editor.remove("album").apply();
                            }
                            if(searchQuery[0].length() > 0) {
                                editor.putString("search", searchQuery[0]).apply();
                            } else {
                                editor.remove("search").apply();
                            }
                            if(aspectRatio[0].length() > 0){
                                editor.putString("ratio", aspectRatio[0]).apply();
                            } else {
                                editor.remove("ratio").apply();
                            }
                            if(pinsOnly[0].length() > 0){
                                editor.putString("pins", pinsOnly[0]).apply();
                            } else {
                                editor.remove("pins").apply();
                            }
                            if(darkSelection[0].length() > 0){
                                editor.putString("bright", darkSelection[0]).apply();
                            } else {
                                editor.remove("bright").apply();
                            }
                            if(maxAge[0].length() > 0){
                                editor.putString("maxAge", maxAge[0]).apply();
                            } else {
                                editor.remove("maxAge").apply();
                            }
                            if(colorSelection[0].length() > 0){
                                editor.putString("color", colorSelection[0]).apply();
                            } else {
                                editor.remove("color").apply();
                            }
                            if(minFResolution[0].length() > 0){
                                editor.putString("minRes", minFResolution[0]).apply();
                            } else {
                                editor.remove("minRes").apply();
                            }
                            if(minHResolution[0].length() > 0){
                                editor.putString("minHRes", minHResolution[0]).apply();
                            } else {
                                editor.remove("minHRes").apply();
                            }
                            if(minWResolution[0].length() > 0){
                                editor.putString("minWRes", minWResolution[0]).apply();
                            } else {
                                editor.remove("minWRes").apply();
                            }
                            if(nsfwResults[0].length() > 0){
                                editor.putString("nsfwEnabled", nsfwResults[0]).apply();
                            } else {
                                editor.remove("nsfwEnabled").apply();
                            }
                            Toast.makeText(getBaseContext(), String.format("Configuration %s Updated", selectedConfig[0]), Toast.LENGTH_SHORT).show();
                            sendBroadcast(new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction(String.format("REFRESH_IMAGES:%s:%s", (selectedConfig[0].contains("wallpaper") ? 1 : 2), (selectedConfig[0].contains(".night")) ? 1 : 0)));
                        });
                        AlertDialog fileEditorDialog = fileEditorBuilder.create();
                        fileEditorDialog.show();
                    }
                });
                selectFileDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog selectAlertFileDialog = selectFileDialog.create();
                selectAlertFileDialog.show();
            }
        });
    }

    private void importUri(Uri uri, String settingsName, Boolean nightMode, String importType) {
        SharedPreferences.Editor settings = this.getSharedPreferences(String.format("seq.settings.%s%s", settingsName, (nightMode) ? ".night" : ""), Context.MODE_PRIVATE).edit();
        SharedPreferences.Editor prefsEditor = prefs.edit();

        if (importType.equals("1") && prefs.getBoolean("swSyncWallpaper", false)) {
            SharedPreferences wallPrefs = this.getSharedPreferences(String.format("seq.settings.wallpaper%s", (nightMode) ? ".night" : ""), Context.MODE_PRIVATE);
            SharedPreferences lockPrefs = this.getSharedPreferences(String.format("seq.settings.lockscreen%s", (nightMode) ? ".night" : ""), Context.MODE_PRIVATE);
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

        prefsEditor.putBoolean("swSyncWallpaper", importType.equals("0")).apply();

        if (isMyServiceRunning(AmbientService.class, this) && ((AmbientDataManager.lastTimeSelect && nightMode) || (!AmbientDataManager.lastTimeSelect && !nightMode))) {
            stopService(new Intent(MainActivity.this, AmbientService.class));
            bEnableTimer.setEnabled(false);
        }

        String _minRes = uri.getQueryParameter("minres");
        String _minHRes = uri.getQueryParameter("minhres");
        String _minWRes = uri.getQueryParameter("minwres");
        String _imageRatio = uri.getQueryParameter("ratio");
        String _folderName = uri.getQueryParameter("folder");
        String _albumId = uri.getQueryParameter("album");
        String _searchQuery = uri.getQueryParameter("search");
        String _maxAge = uri.getQueryParameter("numdays");
        String _pinsOnly = uri.getQueryParameter("pins");
        String _nsfwEnabled = uri.getQueryParameter("nsfw");
        String _darkSelect = uri.getQueryParameter("brightness");
        String _colorSelect = uri.getQueryParameter("color");
        String _serverName = uri.getQueryParameter("server_hostname");

        if (_serverName != null && _serverName.length() > 0) {
            prefsEditor.putString("etServerName", _serverName).apply();
        }
        if (_folderName != null && _folderName.length() > 0) {
            settings.putString("folder", _folderName).apply();
        } else {
            settings.remove("folder").apply();
        }
        if (_albumId != null && _albumId.length() > 0) {
            settings.putString("album", _albumId).apply();
        } else {
            settings.remove("album").apply();
        }
        if (_searchQuery != null && _searchQuery.length() > 0) {
            settings.putString("search", _searchQuery).apply();
        } else {
            settings.remove("search").apply();
        }
        if (_imageRatio != null && _imageRatio.length() > 0) {
            settings.putString("ratio", _imageRatio).apply();
        } else {
            settings.putString("ratio", "0.9-2.1").apply();
        }
        if (_minRes != null && _minRes.length() > 0) {
            settings.putString("minRes", _minRes).apply();
            settings.remove("minHRes").apply();
            settings.remove("minWRes").apply();
        } else if (_minHRes != null && _minHRes.length() > 0) {
            settings.remove("minRes").apply();
            settings.putString("minHRes", _minHRes).apply();
            settings.remove("minWRes").apply();
        } else if (_minWRes != null && _minWRes.length() > 0) {
            settings.remove("minRes").apply();
            settings.remove("minHRes").apply();
            settings.putString("minWRes", _minWRes).apply();
        } else {
            settings.remove("minRes").apply();
            settings.remove("minHRes").apply();
            settings.remove("minWRes").apply();
        }
        if (_darkSelect != null && _darkSelect.length() > 0) {
            settings.putString("bright", _darkSelect).apply();
        } else {
            settings.remove("bright").apply();
        }
        if (_colorSelect != null && _colorSelect.length() > 0) {
            settings.putString("color", _colorSelect).apply();
        } else {
            settings.remove("color").apply();
        }
        if (_pinsOnly != null && _pinsOnly.length() > 0) {
            settings.putString("pins", _pinsOnly).apply();
        } else {
            settings.remove("pins").apply();
        }
        if (_nsfwEnabled != null && _nsfwEnabled.length() > 0) {
            settings.putString("nsfwEnabled", _nsfwEnabled).apply();
        } else {
            settings.putString("nsfwEnabled", "false").apply();
        }
        if (_maxAge != null && _maxAge.length() > 0) {
            settings.putString("maxAge", _maxAge).apply();
        } else {
            settings.remove("maxAge").apply();
        }

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
                        sendBroadcast(new Intent(MainActivity.this, AmbientBroadcastReceiver.class).setAction(String.format("REFRESH_IMAGES:%s:%s", importType, (nightMode) ? 1 : 0)));
                        if ((AmbientDataManager.lastTimeSelect && nightMode) || (!AmbientDataManager.lastTimeSelect && !nightMode)) {
                            Log.i("TimerEvent", "Restarting Service!");
                            bEnableTimer.setEnabled(true);
                        }
                    }
                });
            }
        };
        new Timer().schedule(callRefresh, 1000);
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