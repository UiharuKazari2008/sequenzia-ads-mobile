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
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
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
import java.util.*;

public class AmbientDataManager {
    public static String[] lastNotification = new String[] {null, null};
    public static Bitmap[] lastNotificationPreview = new Bitmap[] {null, null};
    public static Boolean[] lastNotificationFavRemove = new Boolean[] {false, false};

    Context context;

    public AmbientDataManager(Context context) {
        this.context = context;
    }

    public interface AmbientRefreshRequest {
        void onError(String message);
        void onResponse(Boolean completed);
    }
    public void ambientRefresh (Boolean wallpaperSelection, AmbientRefreshRequest completed) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences settingsPrefs = null;

        String filePrefix = "wallpaper";
        if (wallpaperSelection) {
            settingsPrefs = context.getSharedPreferences("seq.settings.wallpaper", Context.MODE_PRIVATE);
        } else {
            settingsPrefs = context.getSharedPreferences("seq.settings.lockscreen", Context.MODE_PRIVATE);
            filePrefix = "lockscreen";
        }
        SharedPreferences sharedPref = context.getSharedPreferences(String.format("seq.ambientData.%s", filePrefix), Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        Map<String, ?> allOldKeys = sharedPref.getAll();

        String minResolution = settingsPrefs.getString("minRes", "");
        String folderName = settingsPrefs.getString("folder", "");
        String albumName = settingsPrefs.getString("album", "");
        String searchQuery = settingsPrefs.getString("search", "");
        String aspectRatio = settingsPrefs.getString("ratio", "");
        String pinsOnly = settingsPrefs.getString("pins", "");
        String nsfwResults = String.format("nsfw=%s&", settingsPrefs.getString("nsfwEnabled", "false"));
        String maxAge = settingsPrefs.getString("maxAge", "");
        String imagesToKeep = String.format("num=%s&", prefs.getString("ltImageCount", "5"));
        String colorSelection = settingsPrefs.getString("bright", "");
        String displayName = String.format("displayname=ADSMobile-%s", settingsPrefs.getString("etDisplayName", "Untitled"));

        if (albumName.length() > 0 ) { albumName = String.format("album=%s&", albumName); }
        if (folderName.length() > 2 && folderName.contains(":")) { folderName = String.format("folder=%s&", folderName); }
        if (searchQuery.length() > 2) { searchQuery = String.format("search=%s&", searchQuery); }
        if (aspectRatio.length() > 2) { aspectRatio = String.format("ratio=%s&", aspectRatio); }
        if (maxAge.length() > 0) { maxAge = String.format("numdays=%s&", maxAge); }
        if (pinsOnly.length() > 0) { pinsOnly = String.format("pins=%s&", pinsOnly); }
        if (minResolution.length() > 0) { minResolution = String.format("minres=%s&", minResolution); }
        if (colorSelection.equals("1")) {
            colorSelection = "dark=false&";
        } else if (colorSelection.equals("2")) {
            colorSelection = "dark=true&";
        } else {
            colorSelection = "";
        }


        final String url = String.format("https://%s/ambient-refresh?nocds=true&%s%s%s%s%s%s%s%s%s%s%s", prefs.getString("etServerName", "seq.moe"), imagesToKeep, colorSelection, minResolution, folderName, albumName, searchQuery, aspectRatio, pinsOnly, nsfwResults, maxAge, displayName);

        Log.i("ADM/Dispatch", url);

        String finalFilePrefix = filePrefix;
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
                                ArrayList<String> imagesDownloaded = new ArrayList<>();
                                ArrayList<String> imagesKeys = new ArrayList<>();

                                // Proccess Images in Response
                                for (int i = 0; i < imageResponse.length(); i++) {
                                    // Get Image in Response
                                    JSONObject singleImage = (JSONObject) imageResponse.get(i);
                                    JSONObject dataImage = new JSONObject();

                                    // Generate Object for storage
                                    final String fullImageURL = singleImage.getString("fullImage");
                                    final String previewImageURL = singleImage.getString("previewImage");
                                    final int imageEid = singleImage.getInt("eid");
                                    final String fileName = String.format("adi-%s-%s", finalFilePrefix, imageEid);
                                    final String previewName = String.format("adp-%s-%s", finalFilePrefix, imageEid);
                                    if (singleImage.getString("colorR") != null) {
                                        final int imageColor = android.graphics.Color.rgb(
                                                Integer.parseInt(singleImage.getString("colorR")),
                                                Integer.parseInt(singleImage.getString("colorG")),
                                                Integer.parseInt(singleImage.getString("colorB")));
                                        dataImage.put("fileColor", imageColor);
                                    }
                                    dataImage.put("fileName", fileName);
                                    dataImage.put("fileUrl", fullImageURL);
                                    dataImage.put("filePreviewUrl", previewImageURL);
                                    dataImage.put("filePreviewName", previewName);
                                    dataImage.put("fileDate", singleImage.getString("date"));
                                    dataImage.put("fileContents", singleImage.getString("contentClean"));
                                    dataImage.put("fileEid", imageEid);
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
                                    prefsEditor.putString(String.format("ambientResponse-%s", imageEid), json).apply();

                                    dataArray[i][0] = fileName;
                                    imagesDownloaded.add(fileName);
                                    imagesKeys.add(String.format("ambientResponse-%s", imageEid));
                                    dataArray[i][1] = fullImageURL;
                                    if (i == imageResponse.length() -1) {
                                        // Request Images to be downloaded
                                        File[] oldImages = context.getFilesDir().listFiles((new FileFilter(){
                                            public boolean accept(File file) {
                                                return file.getName().contains(String.format("adi-%s-", finalFilePrefix)) && !imagesDownloaded.contains(file.getName());
                                            }
                                        }));
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
                                            for (Map.Entry<String, ?> entry: allOldKeys.entrySet()) {
                                                if (!imagesKeys.contains(entry.getKey())) {
                                                    sharedPref.edit().remove(entry.getKey()).apply();
                                                }
                                            }
                                        }
                                        downloadImages(dataArray, new DownloadImageResponse() {
                                            @Override
                                            public void onResponse(Boolean ok) {
                                                completed.onResponse(true);
                                                MainActivity.pendingRefresh[(wallpaperSelection) ? 0 : 1] = false;
                                            }
                                        });
                                    }
                                }
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
        ambientRefreshRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(ambientRefreshRequest);
    }
    public interface AmbientRefreshResponse {
        void onError(String message);
        void onResponse(Boolean completed);
    }
    public void ambientRefreshRequest (AmbientRefreshResponse completed) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean syncWallpapers = prefs.getBoolean("swSyncWallpaper", true);
        boolean enableLockscreen = prefs.getBoolean("swEnableLockscreen", false);
        boolean enableWallpaper = prefs.getBoolean("swEnableWallpaper", false);
        if (syncWallpapers && (enableLockscreen || enableWallpaper)) {
            ambientRefresh(true, new AmbientDataManager.AmbientRefreshRequest() {
                @Override
                public void onError(String message) {
                    completed.onError(message);
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onResponse(Boolean ok) {
                    completed.onResponse(true);
                    if (!MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                        context.startService(new Intent(context, AmbientService.class));
                    } else {
                        nextImage(false, true);
                    }
                    if (!MainActivity.flipBoardEnabled[0] || !MainActivity.flipBoardEnabled[1]) {
                        MainActivity.lastChangeTime[0] = 0;
                        MainActivity.lastChangeTime[1] = 0;
                        MainActivity.flipBoardEnabled[0] = true;
                        MainActivity.flipBoardEnabled[1] = true;
                    }
                }
            });
        } else if (enableWallpaper && enableLockscreen) {
            ambientRefresh(true, new AmbientDataManager.AmbientRefreshRequest() {
                @Override
                public void onError(String message) {
                    completed.onError(message);
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onResponse(Boolean ok) {
                    if (MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                        nextImage(false, true);
                    }

                    ambientRefresh(false, new AmbientDataManager.AmbientRefreshRequest() {
                        @Override
                        public void onError(String message) {
                            completed.onError(message);
                        }

                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void onResponse(Boolean ok) {
                            completed.onResponse(true);
                            nextImage(false, false);
                            if (!MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                                context.startService(new Intent(context, AmbientService.class));
                            } else {
                                nextImage(false, true);
                            }
                            if (!MainActivity.flipBoardEnabled[0] || !MainActivity.flipBoardEnabled[1]) {
                                MainActivity.lastChangeTime[0] = 0;
                                MainActivity.lastChangeTime[1] = 0;
                                MainActivity.flipBoardEnabled[0] = true;
                                MainActivity.flipBoardEnabled[1] = true;
                            }
                        }
                    });
                }
            });
        } else if (enableLockscreen || enableWallpaper) {
            ambientRefresh(enableWallpaper, new AmbientDataManager.AmbientRefreshRequest() {
                @Override
                public void onError(String message) {
                    completed.onError(message);
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onResponse(Boolean ok) {
                    completed.onResponse(true);
                    if (!MainActivity.isMyServiceRunning(AmbientService.class, context)) {
                        context.startService(new Intent(context, AmbientService.class));
                    } else {
                        nextImage(false, enableWallpaper);
                    }
                    if (!MainActivity.flipBoardEnabled[0] || !MainActivity.flipBoardEnabled[1]) {
                        MainActivity.lastChangeTime[0] = 0;
                        MainActivity.lastChangeTime[1] = 0;
                        MainActivity.flipBoardEnabled[0] = true;
                        MainActivity.flipBoardEnabled[1] = true;
                    }
                }
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void nextImage(Boolean firstTry, Boolean imageSelection) {
        deleteLastImage(imageSelection);
        String filePrefix = "wallpaper";
        if (!imageSelection) {
            filePrefix = "lockscreen";
        }
        String finalFilePrefix = filePrefix;
        File[] downloadedImages = context.getFilesDir().listFiles((new FileFilter(){
            public boolean accept(File file) {
                return file.getName().contains(String.format("adi-%s-", finalFilePrefix));
            }
        }));
        if (downloadedImages != null && downloadedImages.length > 0) {
            int randomIndex = new Random().nextInt(downloadedImages.length);
            String imageEid = downloadedImages[randomIndex].getName().split("-", 3)[2];
            Log.i("NextImage", String.format("Images in store: %s - Next Image ID: %s", downloadedImages.length, imageEid));
            setImage(imageEid, imageSelection);
        } else if (firstTry) {
            Log.i("NextImage", "No images in store");
            ambientRefreshRequest(new AmbientDataManager.AmbientRefreshResponse() {
                @Override
                public void onError(String message) {
                    Toast.makeText(context, String.format("AmbientDataManager Failure: %s", message), Toast.LENGTH_SHORT).show();
                    MainActivity.pendingRefresh[(imageSelection) ? 0 : 1] = true;
                }

                @Override
                public void onResponse(Boolean completed) {

                }
            });
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setImage(String imageEid, Boolean imageSelection) {
        Log.i("SetImage", String.format("Request to set image ID %s as wallpaper", imageEid));
        String filePrefix = "wallpaper";
        if (!imageSelection) {
            filePrefix = "lockscreen";
        }
        final String filename = String.format("adi-%s-%s", filePrefix, imageEid);

        Log.i("SetImage", String.format("Using file: %s", filename));

        ImageManager imageManager = new ImageManager(context);
        imageManager.setWallpaperImage(filename, imageSelection, new ImageManager.ImageManagerResponse() {
            @Override
            public void onError(String message) {
                Log.e("SetImage/Img", String.format("Wallpaper Error: %s", message));
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(Boolean completed) {
                MainActivity.lastChangeTime[(imageSelection) ? 0 : 1] = System.currentTimeMillis();
                if (!isDisplayOn()) {
                    MainActivity.pendingTimeReset[(imageSelection) ? 0 : 1] = true;
                }
                lastNotificationFavRemove[(imageSelection) ? 0 : 1] = false;
                lastNotification[(imageSelection) ? 0 : 1] = imageEid;
                lastNotificationPreview[(imageSelection) ? 0 : 1] = null;
                updateNotification(imageSelection);
                ambientHistorySet(imageEid, (imageSelection) ? 0 : 1);
            }
        });
    }
    public void deleteLastImage(Boolean imageSelection) {
        String filePrefix = "wallpaper";
        if (!imageSelection) {
            filePrefix = "lockscreen";
        }
        if (lastNotification[(imageSelection) ? 0 : 1 ] != null) {
            File oldFile = new File(context.getFilesDir(), String.format("adi-%s-%s", filePrefix, lastNotification[(imageSelection) ? 0 : 1 ]));
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

    public void ambientFavorite(int index, boolean imageSelection) {
        String channelId = "";
        String messageEid = "";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences sharedPref = context.getSharedPreferences(String.format("seq.ambientData.%s", (imageSelection) ? "wallpaper" : "lockscreen"), Context.MODE_PRIVATE);
        final String responseData = sharedPref.getString(String.format("ambientResponse-%s",index), null);
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

        final String url = String.format("https://%s/actions/v1", prefs.getString("etServerName", "seq.moe"));

        StringRequest apiRequest = new StringRequest(Request.Method.POST, url, new com.android.volley.Response.Listener<String>() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onResponse(String response) {
                Toast.makeText(context, "Image was favoured!", Toast.LENGTH_SHORT).show();
                updateNotification(imageSelection);
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
        apiRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(apiRequest);
    }
    public void ambientHistorySet (String imageEid, Integer index) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String url = String.format("https://%s/ambient-history?command=set&displayname=ADSMobile-%s&screen=%s&imageid=%s", prefs.getString("etServerName", "seq.moe"), prefs.getString("etDisplayName", "Untitled"), index, imageEid);

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
        apiRequest.setShouldCache(false);
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
        downloadRequest.setShouldCache(false);
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
                        completed.onResponse(true);
                    }
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onResponse(Boolean ok) {
                    if (imagesToDownload.length - 1 == finalI) {
                        completed.onResponse(true);
                    }
                }
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void toggleTimer (Boolean timerSelection) {
        MainActivity.toggleTimer(timerSelection);
        updateNotification(timerSelection);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateNotification(Boolean imageSelection) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        String filePrefix = "wallpaper";
        if (!imageSelection) {
            filePrefix = "lockscreen";
        }
        SharedPreferences sharedPref = context.getSharedPreferences(String.format("seq.ambientData.%s", filePrefix), Context.MODE_PRIVATE);
        JsonObject imageObject = null;
        final String requestedImage = String.format("ambientResponse-%s", lastNotification[(imageSelection) ? 0 : 1 ]);
        final String responseData = sharedPref.getString(requestedImage, null);
        Log.v("Notification", String.format("Requesting Image : %s", requestedImage));
        if (responseData != null) {
            try {
                imageObject = new Gson().fromJson(responseData, JsonObject.class).getAsJsonObject("nameValuePairs");
            } catch (JsonIOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e("Notification", "Failed to get data for notification");
        }

        if (imageObject != null) {
            PendingIntent nextImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("NEXT_IMAGE:%s", (imageSelection) ? "0" : "1")), 0);
            PendingIntent openImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("OPEN_IMAGE:%s:%s", (imageSelection) ? "0" : "1", lastNotification[(imageSelection) ? 0 : 1])), 0);
            PendingIntent favImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("FAV_IMAGE:%s:%s", (imageSelection) ? "0" : "1", lastNotification[(imageSelection) ? 0 : 1])), 0);
            PendingIntent pauseImageIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AmbientBroadcastReceiver.class).setAction(String.format("TOGGLE_TIMER:%s", (imageSelection) ? "0" : "1")), 0);

            final String notificationText = String.format("%s - %s", imageObject.get("fileDate").getAsString(), imageObject.get("location").getAsString());

            Notification.Builder notification = new Notification.Builder(context, (imageSelection) ? MainActivity.NOTIFICATION_CHANNEL_ID_1 : MainActivity.NOTIFICATION_CHANNEL_ID_2)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setTicker(notificationText)
                    .setContentTitle(notificationText)
                    .setSubText((imageSelection) ? "Wallpaper" : "Lockscreen")
                    .setContentIntent(openImageIntent)
                    .setOngoing(true);
            final String notificationContents = imageObject.get("fileContents").getAsString();
            if (notificationContents.length() >= 5) {
                notification.setContentText(notificationContents);
            }
            if (MainActivity.flipBoardEnabled[(imageSelection) ? 0 : 1]) {
                notification.addAction(R.drawable.ic_pause, "Pause", pauseImageIntent);
            } else {
                notification.addAction(R.drawable.ic_play, "Resume", pauseImageIntent);
            }
            notification.addAction(R.drawable.ic_next, "Next", nextImageIntent);
            Notification.MediaStyle mediaStyle = new Notification.MediaStyle();
            if (!imageObject.get("fileFav").getAsBoolean() && !lastNotificationFavRemove[(imageSelection) ? 0 : 1]) {
                notification.addAction(R.drawable.ic_fav, "Favorite", favImageIntent);
                mediaStyle.setShowActionsInCompactView(0,1,2);
            } else {
                mediaStyle.setShowActionsInCompactView(0,1);
            }
            try {
                if (lastNotificationPreview[(imageSelection) ? 0 : 1] != null) {
                    notification.setLargeIcon(lastNotificationPreview[(imageSelection) ? 0 : 1]);
                } else {
                    final String fileName = imageObject.get("fileName").getAsString();
                    FileInputStream file = context.openFileInput(fileName);
                    Bitmap srcBitmap = BitmapFactory.decodeStream(file);
                    float aspectRatio = srcBitmap.getWidth() /
                            (float) srcBitmap.getHeight();
                    int width = 512;
                    int height = Math.round(width / aspectRatio);
                    srcBitmap = Bitmap.createScaledBitmap(srcBitmap, width, height, false);
                    lastNotificationPreview[(imageSelection) ? 0 : 1] = srcBitmap;
                    notification.setLargeIcon(srcBitmap);
                }

                if (imageObject.has("fileColor")) {
                    final int color = imageObject.get("fileColor").getAsInt();
                    notification.setColor(color);
                    //notification.setColorized(true);
                }
            } catch (Exception e) {
                Log.e("NotifiManager", String.format("Failed to load bitmap for notification: %s", e));
            }
            notification.setStyle(mediaStyle);
            manager.notify((imageSelection) ? 100 : 200, notification.build());
        } else {
            Log.e("Notification", "Failed to get data for notification");
        }
    }

    private Boolean isDisplayOn() {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isInteractive()){ return true; }
        return false;
    }

}
