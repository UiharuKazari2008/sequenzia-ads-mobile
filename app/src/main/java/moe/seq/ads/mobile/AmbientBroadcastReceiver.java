package moe.seq.ads.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    final Handler handler1 = new Handler();
    final Handler handler2 = new Handler();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive (Context context , Intent intent) {
        AmbientDataManager ambientDataManager = new AmbientDataManager(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);


        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            Log.i("Broadcast", "Screen Awake!");
            if (refreshTimer1 != null) {
                refreshTimer1.cancel();
                refreshTimer1 = null;
            }
            if (refreshTimer2 != null) {
                refreshTimer2.cancel();
                refreshTimer2 = null;
            }
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
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            boolean syncWallpapers = prefs.getBoolean("swSyncWallpaper", true);
            boolean enableLockscreen = prefs.getBoolean("swEnableLockscreen", false);
            boolean enableWallpaper = prefs.getBoolean("swEnableWallpaper", false);
            if (syncWallpapers && (enableLockscreen || enableWallpaper)) {
                if (MainActivity.pendingRefresh[0] && MainActivity.flipBoardEnabled[0]) {
                    Log.i("Broadcast", "Sleep with Pending download!");
                    ambientDataManager.ambientRefreshRequest(new AmbientDataManager.AmbientRefreshResponse() {
                        @Override
                        public void onError(String message) {
                            Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onResponse(Boolean completed) {
                            MainActivity.pendingRefresh[0] = false;
                        }
                    });
                } else if (MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000) < System.currentTimeMillis() && MainActivity.flipBoardEnabled[0]) {
                    Log.i("Broadcast", "Sleep with Pending update!");
                    ambientDataManager.nextImage(true, true);
                    if (refreshTimer1 != null) {
                        refreshTimer1.cancel();
                        refreshTimer1 = null;
                    }
                } else if (MainActivity.flipBoardEnabled[0]) {
                    final TimerTask callNextImage = new TimerTask() {
                        @Override
                        public void run() {
                            // post a runnable to the handler
                            handler1.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.i("TimerEvent", "Getting Next Image!");
                                    ambientDataManager.nextImage(true, true);
                                    refreshTimer1 = null;
                                }
                            });
                        }
                    };
                    refreshTimer1 = new Timer();
                    final long nextEventTime = ((MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000)) - System.currentTimeMillis());
                    refreshTimer1.schedule(callNextImage, nextEventTime);
                    Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                }
            } else if (enableWallpaper || enableLockscreen) {
                if (enableWallpaper) {
                    if (MainActivity.pendingRefresh[0] && MainActivity.flipBoardEnabled[0]) {
                        Log.i("Broadcast", "Sleep with Pending download!");
                        ambientDataManager.ambientRefreshRequest(new AmbientDataManager.AmbientRefreshResponse() {
                            @Override
                            public void onError(String message) {
                                Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onResponse(Boolean completed) {
                                MainActivity.pendingRefresh[0] = false;
                            }
                        });
                    } else if (MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000) < System.currentTimeMillis() && MainActivity.flipBoardEnabled[0]) {
                        Log.i("Broadcast", "Sleep with Pending update!");
                        ambientDataManager.nextImage(true, true);
                        if (refreshTimer1 != null) {
                            refreshTimer1.cancel();
                            refreshTimer1 = null;
                        }
                    } else if (MainActivity.flipBoardEnabled[0]) {
                        final TimerTask callNextImage = new TimerTask() {
                            @Override
                            public void run() {
                                // post a runnable to the handler
                                handler1.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("TimerEvent", "Getting Next Image!");
                                        ambientDataManager.nextImage(true, true);
                                        refreshTimer1 = null;
                                    }
                                });
                            }
                        };
                        refreshTimer1 = new Timer();
                        final long nextEventTime = ((MainActivity.lastChangeTime[0] + ((long) Integer.parseInt(prefs.getString("ltCycleTime1", "60")) * 60 * 1000)) - System.currentTimeMillis());
                        refreshTimer1.schedule(callNextImage, nextEventTime);
                        Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                    }
                }
                if (enableLockscreen) {
                    if (MainActivity.pendingRefresh[1] && MainActivity.flipBoardEnabled[1]) {
                        Log.i("Broadcast", "Sleep with Pending download!");
                        ambientDataManager.ambientRefreshRequest(new AmbientDataManager.AmbientRefreshResponse() {
                            @Override
                            public void onError(String message) {
                                Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onResponse(Boolean completed) {
                                MainActivity.pendingRefresh[1] = false;
                            }
                        });
                    } else if (MainActivity.lastChangeTime[1] + ((long) Integer.parseInt(prefs.getString("ltCycleTime2", "60")) * 60 * 1000) < System.currentTimeMillis() && MainActivity.flipBoardEnabled[1]) {
                        Log.i("Broadcast", "Sleep with Pending update!");
                        ambientDataManager.nextImage(true, false);
                        if (refreshTimer2 != null) {
                            refreshTimer2.cancel();
                            refreshTimer2 = null;
                        }
                    } else if (MainActivity.flipBoardEnabled[1]) {
                        final TimerTask callNextImage = new TimerTask() {
                            @Override
                            public void run() {
                                // post a runnable to the handler
                                handler2.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("TimerEvent", "Getting Next Image!");
                                        ambientDataManager.nextImage(true, false);
                                        refreshTimer2 = null;
                                    }
                                });
                            }
                        };
                        refreshTimer2 = new Timer();
                        final long nextEventTime = ((MainActivity.lastChangeTime[1] + ((long) Integer.parseInt(prefs.getString("ltCycleTime2", "60")) * 60 * 1000)) - System.currentTimeMillis());
                        refreshTimer2.schedule(callNextImage, nextEventTime);
                        Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
                    }
                }
            }
        } else {
            final String[] calledActions = intent.getAction().split(":", 4);
            final String action = calledActions[0];

            int imageSelection = -1;
            if (calledActions.length > 1) {
                imageSelection = Integer.parseInt(calledActions[1]);
            }
            int index = -1;
            if (calledActions.length == 3) {
                index = Integer.parseInt(calledActions[2]);
            }
            Log.i("Broadcast", String.format("Action: %s - Index: %s", action, index));

            switch (action) {
                case "NEXT_IMAGE":
                    if (imageSelection == -1) {
                        if (prefs.getBoolean("swEnableWallpaper", false)) {
                            ambientDataManager.nextImage(true, true);
                        }
                        if (prefs.getBoolean("swEnableLockscreen", false)) {
                            ambientDataManager.nextImage(true, false);
                        }
                    } else {
                        ambientDataManager.nextImage(true, (imageSelection == 0));
                    }
                    break;
                case "REFRESH_IMAGES":
                    Toast.makeText(context, "Refresh ADS", Toast.LENGTH_SHORT).show();
                    ambientDataManager.ambientRefreshRequest(new AmbientDataManager.AmbientRefreshResponse() {
                        @Override
                        public void onError(String message) {
                            Toast.makeText(context, String.format("Refresh Failure: %s", message), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onResponse(Boolean completed) {
                            Toast.makeText(context, "Downloaded New Images!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                case "FAV_IMAGE":
                    if (index != -1) {
                        AmbientDataManager.lastNotificationFavRemove[(imageSelection == 0) ? 0 : 1] = true;
                        ambientDataManager.ambientFavorite(index, (imageSelection == 0));
                    }
                    break;
                case "OPEN_IMAGE":
                    if (index != -1) {
                        SharedPreferences sharedPref = context.getSharedPreferences(String.format("seq.ambientData.%s", (imageSelection == 0) ? "wallpaper" : "lockscreen"), Context.MODE_PRIVATE);
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
                                Uri fileURL = Uri.parse(String.format("https://%s/gallery?search=eid:%s", prefs.getString("etServerName", "seq.moe"), messageId));
                                context.startActivity(new Intent(Intent.ACTION_VIEW, fileURL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) {
                                Toast.makeText(context, String.format("Failed to open: %s", e), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(context, "Data not found", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                case "TOGGLE_TIMER":
                    ambientDataManager.toggleTimer((imageSelection == 0));
                    break;
                default:
                    Toast.makeText(context, "Feature not implemented", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
