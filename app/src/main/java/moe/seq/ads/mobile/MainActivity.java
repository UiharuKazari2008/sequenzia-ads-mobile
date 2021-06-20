package moe.seq.ads.mobile;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // button to set the home screen wallpaper when clicked
    private Button bLoginButton;
    private Button bLogoutButton;
    private Button bEnableTimer;
    private Button bDisableTimer;
    private TextView tAuthText, tErrorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final AuthWare authware = new AuthWare(MainActivity.this);
        final AmbientDataManager ambientDataManager = new AmbientDataManager(MainActivity.this);

        bLoginButton = findViewById(R.id.loginButton);
        bEnableTimer = findViewById(R.id.startRefreshTimer);
        bDisableTimer = findViewById(R.id.disableRefreshTimer);

        tAuthText = findViewById(R.id.codeHolder);
        tErrorText = findViewById(R.id.errorText);

        SharedPreferences sharedPref = this.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
        SharedPreferences appPref = this.getSharedPreferences("seq.application", Context.MODE_PRIVATE);
        String storedLoginToken = sharedPref.getString("StaticLogin", null);
        String tokenLogin = "";
        Boolean timerEnabled = appPref.getBoolean("timerEnabled", false);

        if (storedLoginToken != null) {
            authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                @Override
                public void onError(String message) {
                    tErrorText.setText(String.format("Failed to login automatically: %s", message));
                }

                @Override
                public void onResponse(Boolean loginSuccess, String authCode) {
                    if (loginSuccess) {
                        bLoginButton.setText("Refresh Images");
                        ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                            @Override
                            public void onError(String message) {
                                tErrorText.setText(String.format("Failed to login automatically: %s", message));
                                bLoginButton.setEnabled(true);
                            }

                            @Override
                            public void onResponse(Boolean completed) {
                                Toast.makeText(MainActivity.this, "Refreshing Images...", Toast.LENGTH_SHORT).show();
                                bLoginButton.setEnabled(true);
                                bEnableTimer.setEnabled(true);
                                tAuthText.setText("");
                                tErrorText.setText("");
                            }
                        });
                    }
                }
            });
        }
        if (timerEnabled) {

        }
        bLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AuthWare.authComplete) {
                    ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                        @Override
                        public void onError(String message) {
                            tErrorText.setText(String.format("Failed to login: %s", message));
                        }

                        @Override
                        public void onResponse(Boolean completed) {
                            Toast.makeText(MainActivity.this, "Refreshing Images...", Toast.LENGTH_SHORT).show();
                            bEnableTimer.setEnabled(true);
                            tAuthText.setText("");
                            tErrorText.setText("");
                        }
                    });
                } else {
                    authware.validateLogin(true, new AuthWare.AuthWareResponseisValid() {
                        @Override
                        public void onError(String message) {
                            tErrorText.setText(String.format("Failed to login: %s", message));
                        }

                        @Override
                        public void onResponse(Boolean loginSuccess, String authCode) {
                            bLoginButton.setEnabled(true);
                            if (authCode != null) {
                                tAuthText.setText(authCode);
                                bLoginButton.setText("Login");
                            }
                            if (loginSuccess) {
                                bLoginButton.setText("Refresh Images");
                                ambientDataManager.ambientRefresh(new AmbientDataManager.AmbientRefreshResponse() {
                                    @Override
                                    public void onError(String message) {

                                    }

                                    @Override
                                    public void onResponse(Boolean completed) {
                                        Toast.makeText(MainActivity.this, "Refreshing Images...", Toast.LENGTH_SHORT).show();
                                        bEnableTimer.setEnabled(true);
                                        tAuthText.setText("");
                                        tErrorText.setText("");
                                    }
                                });
                            } else {
                                bLoginButton.setText("Login");
                            }
                        }
                    });
                }
            }
        });

        bLogoutButton = findViewById(R.id.logoutButton);
        bLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authware.clearLogin();
                bLoginButton.setText("Login");
                bLoginButton.setEnabled(false);
                tAuthText.setText("");
                tErrorText.setText("");
            }
        });

        bEnableTimer.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                Intent notificationIntent = new Intent(this, ExampleActivity.class);
                PendingIntent pendingIntent =
                        PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, 0);

                Notification notification =
                        new Notification.Builder(MainActivity.this, "PRIORITY_LOW")
                                .setContentTitle("Ambient Display Service")
                                .setContentText("Displaying Images")
                                .setSmallIcon(R.drawable.logo)
                                .setContentIntent(pendingIntent)
                                .setTicker("Ambient Display Service")
                                .build();

                MainActivity.startForeground(101, notification);
            }
        });

        bDisableTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });


    }
}