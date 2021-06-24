package moe.seq.ads.mobile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AmbientDataManager {
    public static String lastNotification = null;
    public static Boolean lastNotificationFavRemove = false;
    public static Boolean pendingJob = false;

    Context context;

    public AmbientDataManager(Context context) {
        this.context = context;
    }

    public interface AmbientRefreshResponse {
        void onError(String message);
        void onResponse(Boolean completed);
    }
    public void ambientRefresh (AmbientRefreshResponse completed) {
        AmbientDataManager.pendingJob = true;
        MainActivity.refreshSettings();
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);

        String minResolution = "";
        String folderName = "";
        String albumName = "";
        String searchQuery = "";
        String aspectRatio = "";
        String pinsOnly = "";
        String nsfwResults = "nsfw=false&";
        String maxAge = "";
        String imagesToKeep = "";
        String colorSelection = "";


        if (MainActivity.albumName.length() > 0 ) { albumName = String.format("album=%s&", MainActivity.albumName); }
        if (MainActivity.folderName.length() > 2 && MainActivity.folderName.contains(":")) { folderName = String.format("folder=%s&", MainActivity.folderName); }
        if (MainActivity.searchQuery.length() > 2) { searchQuery = String.format("search=%s&", MainActivity.searchQuery); }
        if (MainActivity.aspectRatio.length() > 2) { aspectRatio = String.format("ratio=%s&", MainActivity.aspectRatio); }
        if (MainActivity.pinsOnly) { pinsOnly = "pins=true&"; }
        if (MainActivity.nsfwResults) { nsfwResults = "nsfw=true&"; }
        if (MainActivity.maxAge > 0) { maxAge = String.format("numdays=%s&", MainActivity.maxAge); }
        if (MainActivity.imagesToKeep > 1) { imagesToKeep = String.format("num=%s&", MainActivity.imagesToKeep); }
        if (!MainActivity.mimResolution.equals("0")) { minResolution = String.format("minres=%s&", MainActivity.mimResolution); }
        if (MainActivity.colorSelection == 1) {
            colorSelection = "dark=false&";
        } else if (MainActivity.colorSelection == 2) {
            colorSelection = "dark=true&";
        }
        String displayName = String.format("displayname=ADSMobile-%s", MainActivity.displayName);

        final String url = String.format("https://%s/ambient-refresh?nocds=true&%s%s%s%s%s%s%s%s%s%s%s", MainActivity.serverName, imagesToKeep, colorSelection, minResolution, folderName, albumName, searchQuery, aspectRatio, pinsOnly, nsfwResults, maxAge, displayName);

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
                            if (imageResponse.length() > 0) {
                                String[][] dataArray = new String[imageResponse.length()][2];

                                SharedPreferences.Editor clearEditorPref = sharedPref.edit().clear();
                                clearEditorPref.apply();

                                // Proccess Images in Response
                                for (int i = 0; i < imageResponse.length(); i++) {
                                    // Get Image in Response
                                    JSONObject singleImage = (JSONObject) imageResponse.get(i);
                                    JSONObject dataImage = new JSONObject();

                                    // Generate Object for storage
                                    final String fullImageURL = singleImage.getString("fullImage");
                                    final String previewImageURL = singleImage.getString("previewImage");
                                    final int imageEid = singleImage.getInt("eid");
                                    final String fileName = String.format("adi-%s", imageEid);
                                    final String previewName = String.format("adp-%s", imageEid);
                                    final int imageColor = android.graphics.Color.rgb(
                                            Integer.parseInt(singleImage.getString("colorR")),
                                            Integer.parseInt(singleImage.getString("colorG")),
                                            Integer.parseInt(singleImage.getString("colorB")));
                                    Log.i("Test", String.format("Color: %s", imageColor));
                                    dataImage.put("fileName", fileName);
                                    dataImage.put("fileUrl", fullImageURL);
                                    dataImage.put("filePreviewUrl", previewImageURL);
                                    dataImage.put("filePreviewName", previewName);
                                    dataImage.put("fileDate", singleImage.getString("date"));
                                    dataImage.put("fileContents", singleImage.getString("contentClean"));
                                    dataImage.put("fileEid", imageEid);
                                    dataImage.put("fileId", singleImage.getInt("id"));
                                    dataImage.put("fileChannelId", singleImage.getString("channelId"));
                                    dataImage.put("fileColor", imageColor);
                                    dataImage.put("location", String.format("%s:/%s/%s",
                                            singleImage.getString("serverName").toUpperCase(),
                                            singleImage.getString("className"),
                                            singleImage.getString("channelName")));
                                    dataImage.put("fileFav", singleImage.getBoolean("pinned"));

                                    // Save Object to Storage
                                    Gson gson = new Gson();
                                    String json = gson.toJson(dataImage);
                                    SharedPreferences.Editor prefsEditor = sharedPref.edit();
                                    prefsEditor.putString(String.format("ambientResponse-%s", imageEid), json);
                                    prefsEditor.apply();
                                    dataArray[i][0] = fileName;
                                    dataArray[i][1] = fullImageURL;
                                }

                                // Request Images to be downloaded
                                File[] oldImages = context.getFilesDir().listFiles();
                                downloadImages(dataArray, new DownloadImageResponse() {
                                    @Override
                                    public void onResponse(Boolean ok) {
                                        completed.onResponse(true);
                                        MainActivity.pendingRefresh = false;
                                        if (oldImages != null && oldImages.length > 0) {
                                            for (File file : oldImages) {
                                                if (!file.isDirectory()) {
                                                    try {
                                                        boolean fileRm = file.delete();
                                                        if (fileRm) {
                                                            Log.i("FilesManager", String.format("Deleted: %s", file.getAbsolutePath()));
                                                        } else {
                                                            Log.i("FilesManager", String.format("Failed to: %s", file.getAbsolutePath()));
                                                        }
                                                    } catch (Exception e) {
                                                        Log.i("FilesManager", String.format("Failed to delete %s: %s", file.getAbsolutePath(), e));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                });
                            } else {
                                Log.e("ADM/Response", "No Results Found");
                                completed.onError("No Results for your settings, please check via Sequenzia if they will return results!");
                            }
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
        AmbientDataManager.pendingJob = true;
        deleteLastImage();
        MainActivity.refreshSettings();
        File[] downloadedImages = context.getFilesDir().listFiles((new FileFilter(){
            public boolean accept(File file) {
                return file.getName().contains("adi-");
            }
        }));
        if (downloadedImages != null && downloadedImages.length > 0) {
            int randomIndex = new Random().nextInt(downloadedImages.length);
            String imageEid = downloadedImages[randomIndex].getName().split("-", 2)[1];
            Log.i("NextImage", String.format("Images in store: %s - Next Image ID: %s", downloadedImages.length, imageEid));
            setImage(imageEid);
        } else if (firstTry) {
            Log.i("NextImage", "No images in store");
            ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                @Override
                public void onError(String message) {
                    Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                    MainActivity.pendingRefresh = true;
                     AmbientDataManager.pendingJob = false;
                }

                @Override
                public void onResponse(Boolean completed) {
                    AmbientDataManager.pendingJob = false;
                }
            });
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setImage(String imageEid) {
        AmbientDataManager.pendingJob = true;
        Log.i("SetImage", String.format("Request to set image ID %s as wallpaper", imageEid));

        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        final String filename = String.format("adi-%s", imageEid);

        Log.i("SetImage", String.format("Using file: %s", filename));

        ImageManager imageManager = new ImageManager(context);
        imageManager.setWallpaperImage(filename, new ImageManager.ImageManagerResponse() {
            @Override
            public void onError(String message) {
                Log.e("SetImage/Img", String.format("Wallpaper Error: %s", message));
                AmbientDataManager.pendingJob = false;
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(Boolean completed) {
                MainActivity.lastChangeTime = System.currentTimeMillis();
                lastNotificationFavRemove = false;
                lastNotification = imageEid;
                updateNotification();
                ambientHistorySet(imageEid);
                AmbientDataManager.pendingJob = false;
            }
        });
    }
    public void deleteLastImage() {
        if (lastNotification != null) {
            File oldFile = new File(context.getFilesDir(), String.format("adi-%s", lastNotification));
            try {
                boolean delete = oldFile.delete();
                if (oldFile.exists()) {
                    context.deleteFile(oldFile.getAbsolutePath());
                } else if (delete) {
                    Log.i("SetImage/Img", String.format("Deleted old file: %s", oldFile.getName()));
                } else {
                    Log.e("SetImage/Img", String.format("Failed to delete old file: %s", oldFile.getName()));
                }
            } catch (Exception e) {
                Log.e("SetImage/Img", String.format("Failed to delete %s: %s", oldFile.getAbsolutePath(), e));
            }
        }
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
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(String response) {
                Toast.makeText(context, "Image was favoured!", Toast.LENGTH_SHORT).show();
                updateNotification();
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
    public void ambientHistorySet (String imageEid) {
        final String url = String.format("https://%s/ambient-history?command=set&displayname=ADSMobile-%s&imageid=%s", MainActivity.serverName, MainActivity.displayName, imageEid);

        StringRequest apiRequest = new StringRequest(Request.Method.GET, url, new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("ADS/History", String.format("Sent History Item: %s", imageEid));
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
                            Log.d("failed to save file", "File not saved", e);

                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show();
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
    private interface DownloadImageResponse {
        void onResponse(Boolean ok);
    }
    public void downloadImages (String[][] imagesToDownload, DownloadImageResponse completed) {
        for (int i=0; i < imagesToDownload.length; i++) {
            final int finalI = i;
            final String fileName = imagesToDownload[i][0];
            final String url = imagesToDownload[i][1];
            sendGetRequest(fileName, url, new GetImageRequest() {
                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onError(String message) {
                    Toast.makeText(context, String.format("Failed to download image %s", url), Toast.LENGTH_SHORT).show();
                    if (imagesToDownload.length - 1 == finalI) {
                        nextImage(false);
                        completed.onResponse(true);
                        AmbientDataManager.pendingJob = false;
                    }
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onResponse(Boolean ok) {
                    if (imagesToDownload.length - 1 == finalI) {
                        nextImage(false);
                        completed.onResponse(true);
                        AmbientDataManager.pendingJob = false;
                    }
                }
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void toggleTimer () {
        MainActivity.toggleTimer();
        updateNotification();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateNotification() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        JsonObject imageObject = null;
        final String responseData = sharedPref.getString(String.format("ambientResponse-%s", lastNotification), null);
        if (responseData != null) {
            try {
                imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
            } catch (JsonIOException e) {
                e.printStackTrace();
            }
        }

        if (imageObject != null) {
            PendingIntent nextImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE"), 0);
            PendingIntent openImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("OPEN_IMAGE:%s", lastNotification)), 0);
            PendingIntent favImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("FAV_IMAGE:%s", lastNotification)), 0);
            PendingIntent pauseImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction("TOGGLE_TIMER"), 0);

            final String notificationText = String.format("%s", imageObject.get("location").getAsString());

            Notification.Builder notification = new Notification.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setTicker(notificationText)
                    .setContentTitle(notificationText)
                    .setSubText(imageObject.get("fileDate").getAsString())
                    .setContentIntent(openImageIntent);
            final String notificationContents = imageObject.get("fileContents").getAsString();
            if (notificationContents.length() >= 5) {
                notification.setContentText(notificationContents);
            }
            if (MainActivity.flipBoardEnabled) {
                notification.addAction(R.drawable.ic_pause, "Pause", pauseImageIntent);
            } else {
                notification.addAction(R.drawable.ic_play, "Resume", pauseImageIntent);
            }
            notification.addAction(R.drawable.ic_next, "Next", nextImageIntent);
            Notification.MediaStyle mediaStyle = new Notification.MediaStyle();
            if (!imageObject.get("fileFav").getAsBoolean() && !lastNotificationFavRemove) {
                notification.addAction(R.drawable.ic_fav, "Favorite", favImageIntent);
                mediaStyle.setShowActionsInCompactView(0,1,2);
            } else {
                mediaStyle.setShowActionsInCompactView(0,1);
            }
            try {
                final String fileName = imageObject.get("fileName").getAsString();
                FileInputStream file = context.openFileInput(fileName);
                Bitmap bitmap = BitmapFactory.decodeStream(file);
                notification.setLargeIcon(bitmap);
                if (imageObject.has("fileColor")) {
                    final int color = imageObject.get("fileColor").getAsInt();
                    notification.setColor(color);
                    notification.setColorized(true);
                }
            } catch (Exception e) {
                Log.e("NotifiManager", String.format("Failed to load bitmap for notification: %s", e));
            }
            notification.setStyle(mediaStyle);
            manager.notify(9854, notification.build());
        }
    }
}
