package moe.seq.ads.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;

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
    public static final String SEQUENZIA_HOST = "https://beta.seq.moe";
    public static final String SEQUENZIA_SESSION = String.format("%s/discord/session", SEQUENZIA_HOST);
    public static final String SEQUENZIA_REFRESH = String.format("%s/ambient-refresh", SEQUENZIA_HOST);

    Context context;

    public AmbientDataManager(Context context) {
        this.context = context;
    }

    public interface AmbientRefreshResponse {
        void onError(String message);
        void onResponse(Boolean completed);
    }

    public void ambientRefresh (AmbientRefreshResponse completed) {
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);

        final String url = String.format("%s?folder=Cosplay:*&numdays=365&minres=720&ratio=1-2.1&displayname=ADSMobile-Untitled&nocds=true", SEQUENZIA_REFRESH);

        JsonObjectRequest ambientRefreshRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.w("Ambient", response.toString());

                        List<ResponseImageModel> images = new ArrayList<>();

                        try {
                            JSONArray imageResponse = response.getJSONArray("randomImagev2");
                            JSONArray dataStorage = new JSONArray();
                            Gson gson = new Gson();

                            SharedPreferences.Editor prefsEditor = sharedPref.edit();
                            String json = gson.toJson(imageResponse);
                            prefsEditor.putString("ambientResponse", json);
                            prefsEditor.apply();

                            for (int i=0; i < imageResponse.length(); i++) {
                                JSONObject singleImage = (JSONObject) imageResponse.get(i);
                                JSONObject dataImage = new JSONObject();

                                final String fullImageURL = singleImage.getString("fullImage");
                                final String previewImageURL = singleImage.getString("previewImage");
                                final String fileName = String.format("ads-%s-%s", singleImage.getInt("eid"), fullImageURL.substring(fullImageURL.lastIndexOf('/')+1 ));
                                final String previewName = String.format("ads-%s-%s", singleImage.getInt("eid"), previewImageURL.substring(previewImageURL.lastIndexOf('/')+1 ));

                                dataImage.put("fileName", fileName);
                                dataImage.put("fileUrl", fullImageURL);
                                dataImage.put("filePreviewUrl", previewImageURL);
                                dataImage.put("filePreviewName", previewName);
                                dataImage.put("fileDate", singleImage.getString("date"));
                                dataImage.put("fileEid", singleImage.getInt("eid"));
                                dataImage.put("fileId", singleImage.getInt("id"));
                                dataImage.put("location", String.format("%s:/%s/%s",
                                        singleImage.getString("serverName"),
                                        singleImage.getString("className"),
                                        singleImage.getString("channelName")));
                                dataImage.put("fileFav", singleImage.getBoolean("pinned"));

                                dataStorage.put(dataImage);
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

                            @Override
                            public void onResponse(Boolean completed) {

                            }
                        });
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
