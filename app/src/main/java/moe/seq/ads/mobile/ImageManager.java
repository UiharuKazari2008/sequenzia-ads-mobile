package moe.seq.ads.mobile;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

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

    public void setWallpaperImage (String fileName, ImageManagerResponse cb) {
        WallpaperManager manager = WallpaperManager.getInstance(context);

        try {
            FileInputStream file = context.openFileInput(fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(file);
            try {
                manager.setBitmap(cropBitmapFromCenterAndScreenSize(bitmap));
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
    /*private void clearStorage () {

    }*/

    private Bitmap cropBitmapFromCenterAndScreenSize(Bitmap bitmap) {
        float screenWidth, screenHeight;
        float bitmap_width = bitmap.getWidth(), bitmap_height = bitmap
                .getHeight();
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();

        Log.i("TAG", "bitmap_width " + bitmap_width);
        Log.i("TAG", "bitmap_height " + bitmap_height);

        float bitmap_ratio = (float) (bitmap_width / bitmap_height);
        float screen_ratio = (float) (screenWidth / screenHeight);
        int bitmapNewWidth, bitmapNewHeight;

        Log.i("TAG", "bitmap_ratio " + bitmap_ratio);
        Log.i("TAG", "screen_ratio " + screen_ratio);

        if (screen_ratio > bitmap_ratio) {
            bitmapNewWidth = (int) screenWidth;
            bitmapNewHeight = (int) (bitmapNewWidth / bitmap_ratio);
        } else {
            bitmapNewHeight = (int) screenHeight;
            bitmapNewWidth = (int) (bitmapNewHeight * bitmap_ratio);
        }

        bitmap = Bitmap.createScaledBitmap(bitmap, bitmapNewWidth,
                bitmapNewHeight, true);

        Log.i("TAG", "screenWidth " + screenWidth);
        Log.i("TAG", "screenHeight " + screenHeight);
        Log.i("TAG", "bitmapNewWidth " + bitmapNewWidth);
        Log.i("TAG", "bitmapNewHeight " + bitmapNewHeight);

        int bitmapGapX, bitmapGapY;
        bitmapGapX = (int) ((bitmapNewWidth - screenWidth) / 2.0f);
        bitmapGapY = (int) ((bitmapNewHeight - screenHeight) / 2.0f);

        Log.i("TAG", "bitmapGapX " + bitmapGapX);
        Log.i("TAG", "bitmapGapY " + bitmapGapY);

        bitmap = Bitmap.createBitmap(bitmap, bitmapGapX, bitmapGapY, (int) screenWidth, (int) screenHeight);
        return bitmap;
    }
}
