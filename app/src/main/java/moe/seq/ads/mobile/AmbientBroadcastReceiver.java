package moe.seq.ads.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import java.util.Timer;
import java.util.TimerTask;

public class AmbientBroadcastReceiver extends BroadcastReceiver {

    Timer refreshTimer1 = new Timer();
    Timer refreshTimer2 = new Timer();
    Timer broadcastDelay = new Timer();
    final Handler handler1 = new Handler();
    final Handler handler2 = new Handler();
    Boolean broadcastBusy = false;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive (Context context , Intent intent) {
        broadcastBusy = true;
        if (broadcastDelay != null) {
            broadcastDelay.cancel();
            broadcastDelay = null;
        }
        AmbientDataManager ambientDataManager = new AmbientDataManager(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean syncWallpapers = prefs.getBoolean("swSyncWallpaper", true);
        final boolean enableLockscreen = prefs.getBoolean("swEnableLockscreen", false);
        final boolean enableWallpaper = prefs.getBoolean("swEnableWallpaper", false);
        final boolean enableTimeCycle = prefs.getBoolean("swCycleConfig", false);
        final int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        final int dayTimeStart = Integer.parseInt(prefs.getString("ltCycleDay", "5"));
        final int dayTimeEnd = Integer.parseInt(prefs.getString("ltCycleNight", "20"));
        boolean timeSelect = (enableTimeCycle && (dayTimeEnd <= currentHour || currentHour < dayTimeStart));

        switch (intent.getAction()) {
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_DATE_CHANGED:
                if (enableTimeCycle && !(timeSelect == AmbientDataManager.lastTimeSelect)) {
                    Log.i("TransitionManager", "Transitioning Mode");
                    context.sendBroadcast(new Intent(context, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE"));
                }
                break;
            case Intent.ACTION_SCREEN_ON:
                Log.i("Broadcast", "Screen Awake!");
                if (refreshTimer1 != null) {
                    refreshTimer1.cancel();
                    refreshTimer1 = null;
                }
                if (refreshTimer2 != null) {
                    refreshTimer2.cancel();
                    refreshTimer2 = null;
                }
                if (enableTimeCycle && !(timeSelect == AmbientDataManager.lastTimeSelect)) {
                    Log.i("TransitionManager", "Transitioning Mode");
                    context.sendBroadcast(new Intent(context, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE"));
                    MainActivity.lastChangeTime[0] = System.currentTimeMillis();
                    MainActivity.lastChangeTime[1] = System.currentTimeMillis();
                    MainActivity.pendingTimeReset[0] = false;
                    MainActivity.pendingTimeReset[1] = false;
                } else {
                    if (MainActivity.pendingTimeReset[0]) {
                        Log.i("Broadcast", "Reset Timer!");
                        MainActivity.lastChangeTime[0] = System.currentTimeMillis();
                        MainActivity.pendingTimeReset[0] = false;
                    }
                    if (MainActivity.pendingTimeReset[1]) {
                        Log.i("Broadcast", "Reset Timer!");
                        MainActivity.lastChangeTime[1] = System.currentTimeMillis();
                        MainActivity.pendingTimeReset[1] = false;
                    }
                }
                broadcastBusy = false;
                break;
            case Intent.ACTION_SCREEN_OFF:
                if (syncWallpapers && (enableLockscreen || enableWallpaper)) {
                    if (MainActivity.pendingRefresh[0] && MainActivity.flipBoardEnabled[0]) {
                        Log.i("Broadcast", "Sleep with Pending download!");
                        ambientDataManager.ambientRefreshRequest(timeSelect, true, new AmbientDataManager.AmbientRefreshResponse() {
                            @Override
                            public void onError(String message) {
                                Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                                delayBroadcastReceiver();
                            }

                            @Override
                            public void onResponse(Boolean completed) {
                                MainActivity.pendingRefresh[0] = false;
                                delayBroadcastReceiver();
                            }
                        });
                    } else if ((AmbientDataManager.lastTimeSelect != timeSelect || (MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000) < System.currentTimeMillis())) && MainActivity.flipBoardEnabled[0]) {
                        Log.i("Broadcast", "Sleep with Pending update!");
                        ambientDataManager.nextImage(true, timeSelect, true, new AmbientDataManager.NextImageResponse() {
                            @Override
                            public void onError(String message) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                delayBroadcastReceiver();
                            }

                            @Override
                            public void onResponse(Boolean completed) {
                                delayBroadcastReceiver();
                            }
                        });
                        if (refreshTimer1 != null) {
                            refreshTimer1.cancel();
                            refreshTimer1 = null;
                        }
                    } else if (MainActivity.flipBoardEnabled[0]) {
                        boolean finalTimeSelect1 = timeSelect;
                        final TimerTask callNextImage = new TimerTask() {
                            @Override
                            public void run() {
                                // post a runnable to the handler
                                handler1.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("TimerEvent", "Getting Next Image!");
                                        ambientDataManager.nextImage(true, finalTimeSelect1, true, new AmbientDataManager.NextImageResponse() {
                                            @Override
                                            public void onError(String message) {
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                            }

                                            @Override
                                            public void onResponse(Boolean completed) {
                                            }
                                        });
                                        MainActivity.pendingTimeReset[0] = true;
                                        refreshTimer1 = null;
                                    }
                                });
                            }
                        };
                        refreshTimer1 = new Timer();
                        final long nextEventTime = ((MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000)) - System.currentTimeMillis());
                        refreshTimer1.schedule(callNextImage, nextEventTime);
                        Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                        broadcastBusy = false;
                    }
                } else if (enableWallpaper || enableLockscreen) {
                    if (enableWallpaper) {
                        if (MainActivity.pendingRefresh[0] && MainActivity.flipBoardEnabled[0]) {
                            Log.i("Broadcast", "Sleep with Pending download!");
                            ambientDataManager.ambientRefreshRequest(timeSelect, true, new AmbientDataManager.AmbientRefreshResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    MainActivity.pendingRefresh[0] = false;
                                    delayBroadcastReceiver();
                                }
                            });
                        } else if ((AmbientDataManager.lastTimeSelect != timeSelect || (MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000) < System.currentTimeMillis())) && MainActivity.flipBoardEnabled[0]) {
                            Log.i("Broadcast", "Sleep with Pending update!");
                            ambientDataManager.nextImage(true, timeSelect, true, new AmbientDataManager.NextImageResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    delayBroadcastReceiver();
                                }
                            });
                            if (refreshTimer1 != null) {
                                refreshTimer1.cancel();
                                refreshTimer1 = null;
                            }
                        } else if (MainActivity.flipBoardEnabled[0]) {
                            boolean finalTimeSelect = timeSelect;
                            final TimerTask callNextImage = new TimerTask() {
                                @Override
                                public void run() {
                                    // post a runnable to the handler
                                    handler1.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.i("TimerEvent", "Getting Next Image!");
                                            ambientDataManager.nextImage(true, finalTimeSelect, true, new AmbientDataManager.NextImageResponse() {
                                                @Override
                                                public void onError(String message) {
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                                }

                                                @Override
                                                public void onResponse(Boolean completed) {

                                                }
                                            });
                                            MainActivity.pendingTimeReset[0] = true;
                                            refreshTimer1 = null;
                                        }
                                    });
                                }
                            };
                            refreshTimer1 = new Timer();
                            final long nextEventTime = ((MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000)) - System.currentTimeMillis());
                            refreshTimer1.schedule(callNextImage, nextEventTime);
                            Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                            broadcastBusy = false;
                        }
                    }
                    if (enableLockscreen) {
                        if (MainActivity.pendingRefresh[1] && MainActivity.flipBoardEnabled[1]) {
                            Log.i("Broadcast", "Sleep with Pending download!");
                            ambientDataManager.ambientRefreshRequest(timeSelect, true, new AmbientDataManager.AmbientRefreshResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    MainActivity.pendingRefresh[1] = false;
                                    delayBroadcastReceiver();
                                }
                            });
                        } else if ((AmbientDataManager.lastTimeSelect != timeSelect || MainActivity.lastChangeTime[1] + ((long) Integer.parseInt(prefs.getString("ltCycleTime2", "60")) * 60 * 1000) < System.currentTimeMillis()) && MainActivity.flipBoardEnabled[1]) {
                            Log.i("Broadcast", "Sleep with Pending update!");
                            ambientDataManager.nextImage(true, timeSelect, false, new AmbientDataManager.NextImageResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    delayBroadcastReceiver();
                                }
                            });
                            if (refreshTimer2 != null) {
                                refreshTimer2.cancel();
                                refreshTimer2 = null;
                            }
                        } else if (MainActivity.flipBoardEnabled[1]) {
                            boolean finalTimeSelect2 = timeSelect;
                            final TimerTask callNextImage = new TimerTask() {
                                @Override
                                public void run() {
                                    // post a runnable to the handler
                                    handler2.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.i("TimerEvent", "Getting Next Image!");
                                            ambientDataManager.nextImage(true, finalTimeSelect2, false, new AmbientDataManager.NextImageResponse() {
                                                @Override
                                                public void onError(String message) {
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                                }

                                                @Override
                                                public void onResponse(Boolean completed) {

                                                }
                                            });
                                            MainActivity.pendingTimeReset[1] = true;
                                            refreshTimer2 = null;
                                        }
                                    });
                                }
                            };
                            refreshTimer2 = new Timer();
                            final long nextEventTime = ((MainActivity.lastChangeTime[1] + ((long) Integer.parseInt(prefs.getString("ltCycleTime2", "60")) * 60 * 1000)) - System.currentTimeMillis());
                            refreshTimer2.schedule(callNextImage, nextEventTime);
                            Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                            broadcastBusy = false;
                        }
                    }
                }
                break;
            default:
                final String[] calledActions = intent.getAction().split(":", 4);
                final String action = calledActions[0];

                int imageSelection = -1;
                if (calledActions.length >= 2) {
                    imageSelection = Integer.parseInt(calledActions[1]);
                }
                int index = -1;
                if (calledActions.length >= 3) {
                    index = Integer.parseInt(calledActions[2]);
                }
                Log.i("Broadcast", String.format("Action: %s - Index: %s", action, index));

                switch (action) {
                    case "NEXT_IMAGE":
                        boolean finalTimeSelect3 = (index != -1) ? (index == 1) : timeSelect;
                        if (imageSelection == -1) {
                            if (prefs.getBoolean("swEnableWallpaper", false)) {
                                ambientDataManager.nextImage(true, finalTimeSelect3, true, new AmbientDataManager.NextImageResponse() {
                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                        delayBroadcastReceiver();
                                    }

                                    @Override
                                    public void onResponse(Boolean completed) {
                                        delayBroadcastReceiver();
                                    }
                                });
                            }
                            if (prefs.getBoolean("swEnableLockscreen", false) && !prefs.getBoolean("swSyncWallpaper", true)) {
                                ambientDataManager.nextImage(true, finalTimeSelect3, false, new AmbientDataManager.NextImageResponse() {
                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                        delayBroadcastReceiver();
                                    }

                                    @Override
                                    public void onResponse(Boolean completed) {
                                        delayBroadcastReceiver();
                                    }
                                });
                            }
                        } else {
                            ambientDataManager.nextImage(true, finalTimeSelect3, (imageSelection == 0), new AmbientDataManager.NextImageResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    delayBroadcastReceiver();
                                }
                            });
                        }
                        break;
                    case "REFRESH_IMAGES":
                        final boolean selectedTime = (index != -1) ? (index == 1) : timeSelect;
                        final boolean changeImage = index == -1 || ((index == 0 && !timeSelect) || (index == 1 && timeSelect));
                        if (imageSelection == -1) {

                            Toast.makeText(context, "Refreshing ADS...", Toast.LENGTH_SHORT).show();
                            ambientDataManager.ambientRefreshRequest(selectedTime, changeImage, new AmbientDataManager.AmbientRefreshResponse() {
                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, String.format("Refresh Failure: %s", message), Toast.LENGTH_SHORT).show();
                                    delayBroadcastReceiver();
                                }

                                @Override
                                public void onResponse(Boolean completed) {
                                    delayBroadcastReceiver();
                                }
                            });
                        } else {
                            if (enableWallpaper && imageSelection < 2) {
                                ambientDataManager.ambientRefresh(true, selectedTime, new AmbientDataManager.AmbientRefreshRequest() {
                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(context, String.format("Refresh Failure: %s", message), Toast.LENGTH_SHORT).show();
                                        delayBroadcastReceiver();
                                    }

                                    @RequiresApi(api = Build.VERSION_CODES.N)
                                    @Override
                                    public void onResponse(Boolean ok) {
                                        if (!MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                                            context.startService(new Intent(context, AmbientService.class));
                                        } else {
                                            if (changeImage) {
                                                ambientDataManager.nextImage(false, selectedTime, true, new AmbientDataManager.NextImageResponse() {
                                                    @Override
                                                    public void onError(String message) {
                                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                                        delayBroadcastReceiver();
                                                    }

                                                    @Override
                                                    public void onResponse(Boolean ok) {
                                                        delayBroadcastReceiver();
                                                    }
                                                });
                                            }
                                        }
                                        if (!MainActivity.flipBoardEnabled[0] || !MainActivity.flipBoardEnabled[1]) {
                                            MainActivity.lastChangeTime[0] = 0;
                                            MainActivity.lastChangeTime[1] = 0;
                                            MainActivity.flipBoardEnabled[0] = true;
                                            MainActivity.flipBoardEnabled[1] = true;
                                        }
                                    }
                                });
                            } else if (enableLockscreen && !syncWallpapers && imageSelection == 2) {
                                ambientDataManager.ambientRefresh(false, selectedTime, new AmbientDataManager.AmbientRefreshRequest() {
                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(context, String.format("Refresh Failure: %s", message), Toast.LENGTH_SHORT).show();
                                        delayBroadcastReceiver();
                                    }

                                    @RequiresApi(api = Build.VERSION_CODES.N)
                                    @Override
                                    public void onResponse(Boolean ok) {
                                        if (changeImage) {
                                            if (!MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                                                context.startService(new Intent(context, AmbientService.class));
                                            } else {
                                                ambientDataManager.nextImage(false, selectedTime, false, new AmbientDataManager.NextImageResponse() {
                                                    @Override
                                                    public void onError(String message) {
                                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                                        delayBroadcastReceiver();
                                                    }

                                                    @Override
                                                    public void onResponse(Boolean ok) {
                                                        delayBroadcastReceiver();
                                                    }
                                                });
                                            }
                                            if (!MainActivity.flipBoardEnabled[0] || !MainActivity.flipBoardEnabled[1]) {
                                                MainActivity.lastChangeTime[0] = 0;
                                                MainActivity.lastChangeTime[1] = 0;
                                                MainActivity.flipBoardEnabled[0] = true;
                                                MainActivity.flipBoardEnabled[1] = true;
                                            }
                                        }
                                    }
                                });
                            }
                        }
                        break;
                    case "FAV_IMAGE":
                        if (index != -1) {
                            AmbientDataManager.lastNotificationFavRemove[(imageSelection == 0) ? 0 : 1] = true;
                            ambientDataManager.ambientFavorite(index, (imageSelection == 0));
                            broadcastBusy = false;
                        }
                        break;
                    case "OPEN_IMAGE":
                        if (index != -1) {
                            SharedPreferences sharedPref = context.getSharedPreferences(String.format("seq.ambientData.%s%s", (imageSelection == 0) ? "wallpaper" : "lockscreen", (timeSelect) ? ".night" : ""), Context.MODE_PRIVATE);
                            SharedPreferences screenPref = context.getSharedPreferences(String.format("seq.settings.%s%s", (imageSelection == 0) ? "wallpaper" : "lockscreen", (timeSelect) ? ".night" : ""), Context.MODE_PRIVATE);
                            final String responseData = sharedPref.getString(String.format("ambientResponse-%s", index), null);
                            if (responseData != null) {
                                JsonObject imageObject = null;
                                try {
                                    imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
                                } catch (JsonIOException e) {
                                    Toast.makeText(context, String.format("Unable to get data: %s", e), Toast.LENGTH_SHORT).show();
                                }
                                try {
                                    assert imageObject != null;
                                    String messageId = imageObject.get("fileEid").getAsString();
                                    Uri fileURL = Uri.parse(String.format("https://%s/gallery?nsfw=%s&search=eid:%s", prefs.getString("etServerName", "seq.moe"), screenPref.getString("nsfwEnabled", "false"), messageId));
                                    context.startActivity(new Intent(Intent.ACTION_VIEW, fileURL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                } catch (Exception e) {
                                    Toast.makeText(context, String.format("Failed to open: %s", e), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(context, "Data not found", Toast.LENGTH_SHORT).show();
                            }
                            broadcastBusy = false;
                        }
                        break;
                    case "TOGGLE_TIMER":
                        ambientDataManager.toggleTimer((imageSelection == 0));
                        broadcastBusy = false;
                        break;
                    default:
                        Toast.makeText(context, "Feature not implemented", Toast.LENGTH_SHORT).show();
                        broadcastBusy = false;
                }
                break;
        }
        if (broadcastBusy) {
            Log.w("Broadcast", String.format("System Busy: Ignored - %s", intent.getAction()));
        }
    }

    private void delayBroadcastReceiver() {
        if (broadcastDelay != null) {
            broadcastDelay.cancel();
            broadcastDelay = null;
        }
        final TimerTask callNextImage = new TimerTask() {
            @Override
            public void run() {
                handler1.post(() -> broadcastBusy = false);
            }
        };
        refreshTimer1 = new Timer();
        refreshTimer1.schedule(callNextImage, 20 * 1000);
    }
}
