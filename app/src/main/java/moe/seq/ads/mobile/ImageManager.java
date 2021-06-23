package moe.seq.ads.mobile;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.WindowManager;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ImageManager {

    Context context;

    public ImageManager(Context context) {
        this.context = context;
    }

    public interface ImageManagerResponse {
        void onError(String message);
        void onResponse(Boolean completed);
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setWallpaperImage (String fileName, ImageManagerResponse cb) {
        SharedPreferences sharedPref = context.getSharedPreferences("seq.ambientData", Context.MODE_PRIVATE);
        WallpaperManager manager = WallpaperManager.getInstance(context);

        try {
            File file = new File(fileName);
            FileInputStream fileStream = context.openFileInput(file.getName());
            Bitmap bitmap = BitmapFactory.decodeStream(fileStream);
            try {
                int displaySelect = WallpaperManager.FLAG_SYSTEM + WallpaperManager.FLAG_LOCK;
                if (MainActivity.wallSelection == 3) {
                    final boolean lastSet = sharedPref.getBoolean("displayFlip", false);
                    if (lastSet) {
                        displaySelect = WallpaperManager.FLAG_LOCK;
                    } else {
                        displaySelect = WallpaperManager.FLAG_SYSTEM;
                    }
                    SharedPreferences.Editor prefsEditor = sharedPref.edit();
                    prefsEditor.putBoolean("displayFlip", !lastSet);
                    prefsEditor.apply();
                } else if (MainActivity.wallSelection == 1) {
                    displaySelect = WallpaperManager.FLAG_SYSTEM;
                } else if (MainActivity.wallSelection == 2) {
                    displaySelect = WallpaperManager.FLAG_LOCK;
                }

                if (MainActivity.centerImage) {
                    manager.setBitmap(cropBitmapFromCenterAndScreenSize(bitmap), null, false, displaySelect);
                } else {
                    manager.setBitmap(bitmap, null, false, displaySelect);
                }
                cb.onResponse(true);
            } catch (IOException e) {
                e.printStackTrace();
                cb.onError("Failed to set wallpaper");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            cb.onError("Failed to find internal file!");
        }
    }
    // Add Storage Manager
    private void clearStorage () {
        File[] folder = context.getFilesDir().listFiles();
        assert folder != null;
        for (File file : folder) {
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

    private Bitmap cropBitmapFromCenterAndScreenSize(Bitmap bitmap) {
        float screenWidth, screenHeight;
        float bitmap_width = bitmap.getWidth(), bitmap_height = bitmap
                .getHeight();
        Point size = new Point();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealSize(size);

        final int screenRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        if (screenRotation == 1 || screenRotation == 3) {
            screenWidth = size.y;
            screenHeight = size.x;
        } else {
            screenWidth = size.x;
            screenHeight = size.y;
        }

        Log.v("TAG", "bitmap_width " + bitmap_width);
        Log.v("TAG", "bitmap_height " + bitmap_height);

        float bitmap_ratio = (float) (bitmap_width / bitmap_height);
        float screen_ratio = (float) (screenWidth / screenHeight);
        int bitmapNewWidth, bitmapNewHeight;

        Log.v("TAG", "bitmap_ratio " + bitmap_ratio);
        Log.v("TAG", "screen_ratio " + screen_ratio);

        if (screen_ratio > bitmap_ratio) {
            bitmapNewWidth = (int) screenWidth;
            bitmapNewHeight = (int) (bitmapNewWidth / bitmap_ratio);
        } else {
            bitmapNewHeight = (int) screenHeight;
            bitmapNewWidth = (int) (bitmapNewHeight * bitmap_ratio);
        }

        bitmap = Bitmap.createScaledBitmap(bitmap, bitmapNewWidth,
                bitmapNewHeight, true);

        Log.v("TAG", "screenWidth " + screenWidth);
        Log.v("TAG", "screenHeight " + screenHeight);
        Log.v("TAG", "bitmapNewWidth " + bitmapNewWidth);
        Log.v("TAG", "bitmapNewHeight " + bitmapNewHeight);

        int bitmapGapX, bitmapGapY;
        bitmapGapX = (int) ((bitmapNewWidth - screenWidth) / 2.0f);
        bitmapGapY = (int) ((bitmapNewHeight - screenHeight) / 2.0f);

        Log.v("TAG", "bitmapGapX " + bitmapGapX);
        Log.v("TAG", "bitmapGapY " + bitmapGapY);

        bitmap = Bitmap.createBitmap(bitmap, bitmapGapX, bitmapGapY, (int) screenWidth, (int) screenHeight);
        return bitmap;
    }
}
