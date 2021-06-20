package moe.seq.ads.mobile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AmbientDataManager {
    public static final String SEQUENZIA_HOST = MainActivity.serverName;
    public static final String SEQUENZIA_SESSION = String.format("%s/discord/session", SEQUENZIA_HOST);
    public static final String SEQUENZIA_REFRESH = String.format("%s/ambient-refresh", SEQUENZIA_HOST);
    public static final String SEQUENZIA_API_V1 = String.format("%s/actions/v1", SEQUENZIA_HOST);
    public static final String SEQUENZIA_API_V2 = String.format("%s/actions/v2", SEQUENZIA_HOST);

    public static int lastNotificationIndex = -1;

    Context context;

    public AmbientDataManager(Context context) {
        this.context = context;
    }

    public interface AmbientRefreshResponse {
        void onError(String message);
        void onResponse(Boolean completed);
    }

    public void ambientRefresh (AmbientRefreshResponse completed) {
        MainActivity.refreshSettings();
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);

        String minResolution = "minres=720&";
        String folderName = "";
        if (MainActivity.folderName.length() > 2 && MainActivity.folderName.contains(":")) {
            folderName = String.format("folder=%s&", MainActivity.folderName);
        }
        String aspectRatio = "";
        if (MainActivity.aspectRatio) {
            aspectRatio = "ratio=1-2.1&";
        }
        String pinsOnly = "";
        if (MainActivity.pinsOnly) {
            pinsOnly = "pins=true&";
        }
        String nsfwResults = "nsfw=false&";
        if (MainActivity.nsfwResults) {
            nsfwResults = "nsfw=true&";
        }
        String maxAge = "";
        if (MainActivity.maxAge > 0) {
            maxAge = String.format("numofdays=%s&", MainActivity.maxAge);
        }
        String displayName = String.format("displayname=ADSMobile-%s", MainActivity.displayName);


        final String url = String.format("https://%s?nocds=true&%s%s%s%s%s%s%s", SEQUENZIA_REFRESH, minResolution, folderName, aspectRatio, pinsOnly, nsfwResults, maxAge, displayName);

        Log.w("RefreshDispatch", url);

        JsonObjectRequest ambientRefreshRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.w("Ambient", response.toString());

                        List<ResponseImageModel> images = new ArrayList<>();

                        try {
                            JSONArray imageResponse = response.getJSONArray("randomImagev2");
                            JSONArray dataStorage = new JSONArray();

                            for (int i=0; i < imageResponse.length(); i++) {
                                Gson gson = new Gson();
                                SharedPreferences.Editor prefsEditor = sharedPref.edit();

                                JSONObject singleImage = (JSONObject) imageResponse.get(i);
                                JSONObject dataImage = new JSONObject();

                                final String fullImageURL = singleImage.getString("fullImage");
                                final String previewImageURL = singleImage.getString("previewImage");
                                final String fileName = String.format("adi-%s", i);
                                final String previewName = String.format("adp-%s", i);

                                dataImage.put("fileName", fileName);
                                dataImage.put("fileUrl", fullImageURL);
                                dataImage.put("filePreviewUrl", previewImageURL);
                                dataImage.put("filePreviewName", previewName);
                                dataImage.put("fileDate", singleImage.getString("date"));
                                dataImage.put("fileContents", singleImage.getString("contentClean"));
                                dataImage.put("fileEid", singleImage.getInt("eid"));
                                dataImage.put("fileId", singleImage.getInt("id"));
                                dataImage.put("fileChannelId", singleImage.getString("channelId"));
                                dataImage.put("location", String.format("%s:/%s/%s",
                                        singleImage.getString("serverName"),
                                        singleImage.getString("className"),
                                        singleImage.getString("channelName")));
                                dataImage.put("fileFav", singleImage.getBoolean("pinned"));

                                dataStorage.put(dataImage);

                                String json = gson.toJson(dataImage);
                                prefsEditor.putString(String.format("ambientResponse-%s", i), json);
                                prefsEditor.apply();
                            }

                            downloadImages(dataStorage);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        completed.onResponse(true);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                        error.printStackTrace();
                    }
                });

        NetworkManager.getInstance(context).addToRequestQueue(ambientRefreshRequest);
    }

    public void ambientFavorite (int index) {
        String channelId = "";
        String messageEid = "";

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
                channelId = imageObject.get("fileChannelId").getAsString();
                messageEid = imageObject.get("fileEid").getAsString();
            } catch (JsonIOException e) {
                e.printStackTrace();
            }
        }

        String finalChannelId = channelId;
        String finalMessageEid = messageEid;
        StringRequest apiRequest = new StringRequest(Request.Method.POST, SEQUENZIA_API_V1, new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Toast.makeText(context, "Image was favoured!", Toast.LENGTH_SHORT).show();
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, "Failed to favorite image", Toast.LENGTH_SHORT).show();
                Log.e("FavImage", String.valueOf(error));
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("channelid", finalChannelId);
                params.put("messageid", finalMessageEid);
                params.put("action", "Pin");
                params.put("bypass", "appIntent");
                return params;
            }
        };

        NetworkManager.getInstance(context).addToRequestQueue(apiRequest);
    }

    private interface GetImageRequest {
        void onError(String message);
        void onResponse(Boolean ok);
    }

    private void sendGetRequest(final String filename, String url, GetImageRequest cb) {
        EncodedByteArrayFileRequest downloadRequest = new EncodedByteArrayFileRequest(
                Request.Method.GET, url,
                new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        try {

                            FileOutputStream outputStream;
                            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
                            outputStream.write(response);
                            outputStream.close();

                            cb.onResponse(true);
                        } catch (IOException e) {
                            // TODO: remove all logs before finalizing app
                            Log.d("failed to save file", "File not saved", e);

                            String msg = "Failed to save file";
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                            cb.onResponse(false);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        handleDownloadError(error);
                        cb.onError("Failed to download!");
                    }
                },
                (HashMap<String, String>) requestParams(filename)
        );

        NetworkManager.getInstance(context).addToRequestQueue(downloadRequest);
    }

    private void handleDownloadError(VolleyError error) {
        Log.d("VolleyError", "Got error in get request", error);
        Toast.makeText(context, "Error. Check Logcat", Toast.LENGTH_SHORT).show();
    }

    private Map<String, String> requestParams(String filename) {
        Map<String, String> params = new HashMap<>();
        params.put("filename", filename);    // I setup my lambda function to check for this header
        return params;
    }

    public void downloadImages (JSONArray images) {
        for (int i=0; i < images.length(); i++) {
            try {
                ImageManager imageManager = new ImageManager(context);

                JSONObject imageObject = images.getJSONObject(i);
                String filename = imageObject.getString("fileName");
                int finalI = i;
                sendGetRequest(filename, imageObject.getString("fileUrl"), new GetImageRequest() {
                    @Override
                    public void onError(String message) {

                    }

                    @Override
                    public void onResponse(Boolean ok) {
                        imageManager.setWallpaperImage(filename, new ImageManager.ImageManagerResponse() {
                            @Override
                            public void onError(String message) {

                            }

                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            public void onResponse(Boolean completed) {
                                updateNotification(finalI);
                            }
                        });
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void toggleTimer () {
        MainActivity.toggleTimer();
        updateNotification(lastNotificationIndex);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateNotification(int index) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        JsonObject imageObject = null;
        final String responseData = sharedPref.getString(String.format("ambientResponse-%s", index), null);
        if (responseData != null) {
            try {
                imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
            } catch (JsonIOException e) {
                e.printStackTrace();
            }
        }
        assert imageObject != null;


        PendingIntent nextImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE"), 0);
        PendingIntent openImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("OPEN_IMAGE:%s", index)), 0);
        PendingIntent favImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("FAV_IMAGE:%s", index)), 0);
        PendingIntent pauseImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction("TOGGLE_TIMER"), 0);

        final String notificationText = String.format("%s - %s", imageObject.get("location").getAsString(), imageObject.get("fileDate").getAsString());

        Notification.Builder notification = new Notification.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setTicker(notificationText)
                .setContentTitle(notificationText)
                .setContentIntent(openImageIntent);
        final String notificationContents = imageObject.get("fileContents").getAsString();
        if (notificationContents.length() >= 5) {
            notification.setContentText(notificationContents);
        }
        notification.addAction(R.drawable.ic_launcher_foreground, "Next", nextImageIntent);
        String alarmAction = "Resume";
        if (MainActivity.alarmManagerActive) { alarmAction = "Pause"; }
        notification.addAction(R.drawable.ic_launcher_foreground, alarmAction, pauseImageIntent);
        if (!imageObject.get("fileFav").getAsBoolean()) {
            notification.addAction(R.drawable.ic_launcher_foreground, "Favorite", favImageIntent);
        }
        manager.notify(9854, notification.build());

        lastNotificationIndex = index;
    }
}
