package moe.seq.ads.mobile;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.RequiresApi;

public class AmbientService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "ADS Service Stared", Toast.LENGTH_LONG).show();
        startForeground(9854, buildNotification());
        MainActivity.alarmManager.setInexactRepeating(AlarmManager.RTC, 5000, MainActivity.interval, MainActivity.pendingIntent);
        MainActivity.alarmManagerActive = true;
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        Toast.makeText(this, "ADS Service Stopped", Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Notification buildNotification() {

        Notification.Builder notification = new Notification.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Sequenzia")
                .setContentTitle("Please Wait...");
        return notification.build();
    }
}