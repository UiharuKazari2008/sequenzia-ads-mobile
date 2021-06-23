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
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;

import java.util.Timer;
import java.util.TimerTask;

public class AmbientBroadcastReceiver extends BroadcastReceiver {

    Timer refreshTimer = new Timer();
    final Handler handler = new Handler();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive (Context context , Intent intent) {
        AmbientDataManager ambientDataManager = new AmbientDataManager(context);


        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            Log.i("Broadcast", "Screen Awake!");
            if (refreshTimer != null) {
                refreshTimer.cancel();
                refreshTimer = null;
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (MainActivity.pendingRefresh && MainActivity.flipBoardEnabled) {
                Log.i("Broadcast", "Sleep with Pending download!");
                ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResponse(Boolean completed) {
                        MainActivity.pendingRefresh = false;
                    }
                });
            } else if (MainActivity.lastChangeTime + MainActivity.interval < System.currentTimeMillis() && MainActivity.flipBoardEnabled) {
                Log.i("Broadcast", "Sleep with Pending update!");
                ambientDataManager.nextImage(true);
                if (refreshTimer != null) {
                    refreshTimer.cancel();
                    refreshTimer = null;
                }
            } else if (MainActivity.flipBoardEnabled) {
                MainActivity.refreshSettings();
                final TimerTask callNextImage = new TimerTask() {
                    @Override
                    public void run() {
                        // post a runnable to the handler
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("TimerEvent", "Getting Next Image!");
                                ambientDataManager.nextImage(true);
                                refreshTimer = null;
                            }
                        });
                    }
                };
                refreshTimer = new Timer();
                final long nextEventTime = ((MainActivity.lastChangeTime + MainActivity.interval) - System.currentTimeMillis());
                refreshTimer.schedule(callNextImage, nextEventTime);
                Log.i("BroadcastEvent", String.format("Next Cycle Time is : %s Sec", nextEventTime / 1000));
            }
        } else {
            final String[] calledActions = intent.getAction().split(":", 2);
            final String action = calledActions[0];
            int index = -1;
            if (calledActions.length == 2) {
                index = Integer.parseInt(calledActions[1]);
            }
            Log.i("Broadcast", String.format("Action: %s - Index: %s", action, index));

            switch (action) {
                case "NEXT_IMAGE":
                    ambientDataManager.nextImage(true);
                    break;
                case "REFRESH_IMAGES":
                    Toast.makeText(context, "Getting More Image...", Toast.LENGTH_SHORT).show();
                    ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                        @Override
                        public void onError(String message) {
                            Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onResponse(Boolean completed) {
                            Toast.makeText(context, "Downloaded New Images!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                case "FAV_IMAGE":
                    if (index != -1) {
                        AmbientDataManager.lastNotificationFavRemove = true;
                        ambientDataManager.ambientFavorite(index);
                    }
                    break;
                case "OPEN_IMAGE":
                    if (index != -1) {
                        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
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
                                Uri fileURL = Uri.parse(String.format("https://%s/gallery?search=eid:%s", MainActivity.serverName, messageId));
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
                    ambientDataManager.toggleTimer();
                    break;
                default:
                    Toast.makeText(context, "Feature not implemented", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
