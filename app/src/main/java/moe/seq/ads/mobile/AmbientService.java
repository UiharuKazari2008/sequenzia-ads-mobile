package moe.seq.ads.mobile;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;

import java.io.File;

public class AmbientService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }

    BroadcastReceiver ambientBroadcastReceiver = new AmbientBroadcastReceiver();


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "ADS Service Stared", Toast.LENGTH_LONG).show();
        startForeground(9854, buildNotification());
        MainActivity.lastChangeTime = 0;
        MainActivity.flipBoardEnabled = true;

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.setPriority(100);
        registerReceiver(ambientBroadcastReceiver, screenStateFilter);

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AmbientDataManager.lastNotification != null) {
            File oldFile = new File(getFilesDir(), String.format("adi-%s", AmbientDataManager.lastNotification));
            try {
                boolean delete = oldFile.delete();
                if (oldFile.exists()) {
                    deleteFile(oldFile.getAbsolutePath());
                } else if (delete) {
                    Log.i("SetImage/Img", String.format("Deleted old file: %s", oldFile.getName()));
                } else {
                    Log.e("SetImage/Img", String.format("Failed to delete old file: %s", oldFile.getName()));
                }
            } catch (Exception e) {
                Log.e("SetImage/Img", String.format("Failed to delete %s: %s", oldFile.getAbsolutePath(), e));
            }
        }
        MainActivity.lastChangeTime = 0;
        MainActivity.flipBoardEnabled = false;
        stopForeground(true);
        unregisterReceiver(ambientBroadcastReceiver);
        Toast.makeText(this, "ADS Service Stopped", Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Notification buildNotification() {

        Notification.Builder notification = new Notification.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText("Ready to go!")
                .setContentTitle("Lock device for first cycle");
        return notification.build();
    }
}