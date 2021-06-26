package moe.seq.ads.mobile;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

public class AmbientBootReceiver extends BroadcastReceiver {

    Context context;

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        AuthWare authware = new AuthWare(context);

        if (sharedPrefs.getBoolean("swOnBoot", false)) {
            //Intent i = new Intent(context, MainActivity.class);
            //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //context.startActivity(i);

            authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                @Override
                public void onError(String message) {
                    Toast.makeText(context, String.format("Unable to login: %s", message), Toast.LENGTH_SHORT).show();
                }

                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onResponse(Boolean loginSuccess, String authCode, String sessionId) {
                    if (loginSuccess) {
                        context.startForegroundService(new Intent(context, AmbientService.class));
                    }
                }
            });
        }
    }
}
