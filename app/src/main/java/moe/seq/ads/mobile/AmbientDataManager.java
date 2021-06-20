package moe.seq.ads.mobile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
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
import java.util.*;

public class AmbientDataManager {
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
        String searchQuery = "";
        String aspectRatio = "";
        String pinsOnly = "";
        String nsfwResults = "nsfw=false&";
        String maxAge = "";
        String imagesToKeep = "";

        if (MainActivity.folderName.length() > 2 && MainActivity.folderName.contains(":")) { folderName = String.format("folder=%s&", MainActivity.folderName); }
        if (MainActivity.searchQuery.length() > 2) { searchQuery = String.format("search=%s&", MainActivity.searchQuery); }
        if (MainActivity.aspectRatio) { aspectRatio = "ratio=1-2.1&"; }
        if (MainActivity.pinsOnly) { pinsOnly = "pins=true&"; }
        if (MainActivity.nsfwResults) { nsfwResults = "nsfw=true&"; }
        if (MainActivity.maxAge > 0) { maxAge = String.format("numdays=%s&", MainActivity.maxAge); }
        if (MainActivity.imagesToKeep > 1) { imagesToKeep = String.format("num=%s&", MainActivity.imagesToKeep); }
        String displayName = String.format("displayname=ADSMobile-%s", MainActivity.displayName);

        final String url = String.format("https://%s/ambient-refresh?nocds=true&%s%s%s%s%s%s%s%s%s", MainActivity.serverName, imagesToKeep, minResolution, folderName, searchQuery, aspectRatio, pinsOnly, nsfwResults, maxAge, displayName);

        Log.i("ADM/Dispatch", url);

        JsonObjectRequest ambientRefreshRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.v("ADM/Response", response.toString());
                        try {
                            // Get Required Response
                            JSONArray imageResponse = response.getJSONArray("randomImagev2");

                            // Proccess Images in Response
                            for (int i=0; i < imageResponse.length(); i++) {
                                // Get Image in Response
                                JSONObject singleImage = (JSONObject) imageResponse.get(i);
                                JSONObject dataImage = new JSONObject();

                                // Generate Object for storage
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
                                        singleImage.getString("serverName").toUpperCase(),
                                        singleImage.getString("className"),
                                        singleImage.getString("channelName")));
                                dataImage.put("fileFav", singleImage.getBoolean("pinned"));

                                // Save Object to Storage
                                Gson gson = new Gson();
                                String json = gson.toJson(dataImage);
                                SharedPreferences.Editor prefsEditor = sharedPref.edit();
                                prefsEditor.putString(String.format("ambientResponse-%s", i), json);
                                prefsEditor.apply();
                            }

                            // Request Images to be downloaded
                            downloadImages(imageResponse.length());
                            completed.onResponse(true);
                        } catch ( JSONException e) {
                            Log.e("ADM/Response", String.format("Failed to get required data from server: %s", e));
                            completed.onError(String.format("Failed to get a valid response from server: %s", e));
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        completed.onError(String.format("Failed to get response from server: %s", error));
                    }
                });

        NetworkManager.getInstance(context).addToRequestQueue(ambientRefreshRequest);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void nextImage(Boolean firstTry) {
        //if (isDisplayOn()) {
            MainActivity.refreshSettings();
            SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
            int nextImageIndex = sharedPref.getInt("lastImageIndex", -1) + 1;
            Set<String> set = sharedPref.getStringSet("downloadedImages", null);
            if (set != null) {
                List<String> list = new ArrayList<String>(set);
                String[] downloadedImages = list.toArray(new String[0]);

                Log.i("NextImage", String.format("Images in store: %s - Next Image is: %s", downloadedImages.length, nextImageIndex));

                if (downloadedImages.length > 0 && downloadedImages.length > nextImageIndex) {
                    final String responseData = sharedPref.getString(String.format("ambientResponse-%s", nextImageIndex), null);
                    JsonObject imageObject = null;
                    try {
                        imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
                    } catch (JsonIOException e) {
                        Toast.makeText(context, String.format("Unable to get data: %s", e), Toast.LENGTH_SHORT).show();
                    }
                    if (imageObject != null) {
                        setImage(imageObject, nextImageIndex);
                    }
                } else if (firstTry) {
                    ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                        @Override
                        public void onError(String message) {
                            Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onResponse(Boolean completed) {

                        }
                    });
                }
            } else {
                Log.i("NextImage", "No images in store");
                ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResponse(Boolean completed) {

                    }
                });
            }
        //} else {
        //    Log.i("NextImage", "Ignored, Display Is Off");
        //}
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setImage(@org.jetbrains.annotations.NotNull JsonObject imageObject, int nextImageIndex) {
        Log.i("SetImage", String.format("Request to set image #%s as wallpaper", nextImageIndex));

        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        final String filename = imageObject.get("fileName").getAsString();

        Log.i("SetImage", String.format("Using file: %s", filename));

        ImageManager imageManager = new ImageManager(context);
        final int finalNextImageIndex = nextImageIndex;
        imageManager.setWallpaperImage(filename, new ImageManager.ImageManagerResponse() {
            @Override
            public void onError(String message) {
                Log.e("SetImage/Img", String.format("Wallpaper Error: %s", message));
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(Boolean completed) {
                SharedPreferences.Editor prefsEditor = sharedPref.edit();
                prefsEditor.putInt("lastImageIndex", finalNextImageIndex);
                prefsEditor.apply();
                updateNotification(finalNextImageIndex);
                ambientHistorySet(finalNextImageIndex);
            }
        });
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

        final String url = String.format("https://%s/actions/v1", MainActivity.serverName);

        StringRequest apiRequest = new StringRequest(Request.Method.POST, url, new com.android.volley.Response.Listener<String>() {
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
    public void ambientHistorySet (int index) {
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
                messageEid = imageObject.get("fileEid").getAsString();
            } catch (JsonIOException e) {
                e.printStackTrace();
            }
        }

        String finalMessageEid = messageEid;

        final String url = String.format("https://%s/ambient-history?command=set&displayname=ADSMobile-%s&imageid=%s", MainActivity.serverName, MainActivity.displayName, finalMessageEid);

        StringRequest apiRequest = new StringRequest(Request.Method.GET, url, new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("ADS/History", String.format("Sent History Item: %s", finalMessageEid));
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Setup History Local Storage for Slow Internet Connections
            }
        });

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
    public void downloadImages (int imagesLength) {
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        Set<String> downloadedImages = new HashSet<String>();
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        prefsEditor.putInt("lastImageIndex", -1);
        prefsEditor.apply();

        for (int i=0; i < imagesLength; i++) {
            final String responseData = sharedPref.getString(String.format("ambientResponse-%s", i), null);
            JsonObject imageObject = null;
            try {
                imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
            } catch (JsonIOException e) {
                Toast.makeText(context, String.format("Unable to get data: %s", e), Toast.LENGTH_SHORT).show();
            }
            if (imageObject != null) {
                final String filename = imageObject.get("fileName").getAsString();
                final String url = imageObject.get("fileUrl").getAsString();

                int finalI = i;
                sendGetRequest(filename, url, new GetImageRequest() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, String.format("Failed to download image #%s", finalI), Toast.LENGTH_SHORT).show();
                        if (imagesLength - 1 == finalI) {
                            nextImage(false);
                        }
                    }

                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onResponse(Boolean ok) {
                        downloadedImages.add(String.format("ambientResponse-%s", finalI));
                        SharedPreferences.Editor prefsEditor = sharedPref.edit();
                        prefsEditor.putStringSet("downloadedImages", downloadedImages);
                        prefsEditor.apply();
                        if (imagesLength - 1 == finalI) {
                            nextImage(false);
                        }
                    }
                });
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

    private Boolean isDisplayOn() {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isInteractive()){ return true; }
        return false;
    }
}
