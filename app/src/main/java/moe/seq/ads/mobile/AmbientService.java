package moe.seq.ads.mobile;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import java.io.File;

public class AmbientService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private boolean isRunning;
    private Context context;
    //private Thread backgroundThread;

    BroadcastReceiver ambientBroadcastReceiver = new AmbientBroadcastReceiver();
    public AuthWare authware;
    public SharedPreferences authPref;

//    private Runnable serviceTasks = new Runnable() {
//        public void run() {
//            // Do something here
//            stopSelf();
//        }
//    };

    @Override
    public void onCreate() {
        this.context = this;
        this.isRunning = false;
        authware = new AuthWare(this.context);
        authPref = this.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
        //this.backgroundThread = new Thread(serviceTasks);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Sequenzia ADS Running", Toast.LENGTH_LONG).show();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        startForeground(200, buildNotification(MainActivity.NOTIFICATION_CHANNEL_ID_2));
        startForeground(100, buildNotification(MainActivity.NOTIFICATION_CHANNEL_ID_1));

        MainActivity.lastChangeTime[0] = 0;
        MainActivity.lastChangeTime[1] = 0;
        MainActivity.flipBoardEnabled[0] = true;
        MainActivity.flipBoardEnabled[1] = true;

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_TIME_CHANGED);
        screenStateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        screenStateFilter.addAction(Intent.ACTION_DATE_CHANGED);
        screenStateFilter.setPriority(100);
        registerReceiver(ambientBroadcastReceiver, screenStateFilter);
        sendBroadcast(new Intent(this.context, AmbientBroadcastReceiver.class).setAction("NEXT_IMAGE"));
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AmbientDataManager.lastNotification[0] != null) {
            File oldFile = new File(getFilesDir(), String.format("adi-wallpaper-%s%s", (AmbientDataManager.lastTimeSelect) ? "night-" : "", AmbientDataManager.lastNotification[0]));
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
        if (AmbientDataManager.lastNotification[1] != null) {
            File oldFile = new File(getFilesDir(), String.format("adi-lockscreen-%s%s", (AmbientDataManager.lastTimeSelect) ? "night-" : "", AmbientDataManager.lastNotification[1]));
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
        MainActivity.lastChangeTime[0] = 0;
        MainActivity.lastChangeTime[1] = 0;
        MainActivity.flipBoardEnabled[0] = false;
        MainActivity.flipBoardEnabled[1] = false;
        stopForeground(true);
        unregisterReceiver(ambientBroadcastReceiver);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.cancel(200);
        notificationManager.cancel(100);
        Toast.makeText(this, "Sequenzia ADS Stopped!", Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Notification buildNotification(String channel) {

        Notification.Builder notification = new Notification.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText("Initializing")
                .setContentTitle("Loading Images...");
        return notification.build();
    }
}