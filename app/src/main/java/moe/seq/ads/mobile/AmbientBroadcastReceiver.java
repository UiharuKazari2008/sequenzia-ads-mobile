package moe.seq.ads.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;

public class AmbientBroadcastReceiver extends BroadcastReceiver {

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive (Context context , Intent intent) {
        AmbientDataManager ambientDataManager = new AmbientDataManager(context);
        final String[] calledActions = intent.getAction().split(":",2);
        final String action = calledActions[0];
        int index = -1;
        if (calledActions.length == 2) { index = Integer.parseInt(calledActions[1]); }
        Log.w("Broadcast", String.format("Action: %s - Index: %s", action, index));

        switch (action) {
            case "NEXT_IMAGE":
                Toast.makeText(context, "Getting Next Iamge...", Toast.LENGTH_SHORT).show();
                ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, "AmbientManager Failure", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResponse(Boolean completed) {

                    }
                });
                break;
            case "FAV_IMAGE":
                if (index != -1) {
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
                            e.printStackTrace();
                        }
                        try {
                            Log.w("FindElement", responseData);
                            assert imageObject != null;
                            String messageId = imageObject.get("fileId").getAsString();
                            Uri fileURL = Uri.parse(String.format("%s/gallery?search=id:st:%s", AmbientDataManager.SEQUENZIA_HOST, messageId.substring(0,6)));
                            context.startActivity(new Intent(Intent.ACTION_VIEW, fileURL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (JsonIOException e) {
                            e.printStackTrace();
                        }
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