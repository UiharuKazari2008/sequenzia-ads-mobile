package moe.seq.ads.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import org.json.JSONObject;

public class AuthWare {
    public static Boolean authComplete = false;

    Context context;
    public AuthWare(Context context) {
        this.context = context;
    }


    public interface AuthWareResponseisValid {
        void onError(String message);
        void onResponse(Boolean loginSuccess, String authCode, String sessionID);
    }
    public void validateLogin (Boolean useLoginToken, AuthWareResponseisValid isValid) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        if (authComplete) {
            // Fast Return if already logged in.
            isValid.onResponse(true, null, null);
            Log.i("AuthWare", "Already Authenticated!");
        } else {
            // Get stored login token
            SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
            SharedPreferences cookiePref = context.getSharedPreferences("seq.cookies", Context.MODE_PRIVATE);
            String sessionId = cookiePref.getString("SessionId", "");
            String storedLoginToken = sharedPref.getString("StaticLogin", null);
            String tokenLogin = "";
            if (useLoginToken && storedLoginToken != null) {
                tokenLogin = String.format("&key=%s", storedLoginToken);
                Log.v("AuthWare", String.format("Found Stored Static Login Key: %s", tokenLogin));
            }
            // Generate URL
            final String url = String.format("%s://%s/ping?json=true%s", (prefs.getBoolean("swHTTPS", true)) ? "https" : "http", prefs.getString("etServerName", "seq.moe"), tokenLogin);

            // Request JSON version of Ping request
            JsonObjectRequest loginCheckRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                if (response.getBoolean("loggedin")) {
                                    // Login was successful!
                                    Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show();
                                    // Refresh Session to required data
                                    Log.i("Session", String.format("Cache: %s", sessionId));
                                    Log.i("Session", String.format("Reponse: %s", response.getString("session")));
                                    String responseString = "";
                                    try {
                                        responseString = response.getString("session");
                                    } catch (Exception e) {
                                        Toast.makeText(context, "Failed to get a session token", Toast.LENGTH_SHORT).show();
                                    }
                                    if (!responseString.equals(sessionId)) {
                                        refreshSession(new AuthWareRefreshAccount() {
                                            @Override
                                            public void onError(String message) {
                                                isValid.onResponse(false, null, null);
                                            }

                                            @Override
                                            public void onResponse(Boolean ok) {
                                                // Async Get Static Login Token
                                                getStaticLogin(new AuthWareResponseStaticKey() {
                                                    @Override
                                                    public void onError(String message) {
                                                        Toast.makeText(context, "Failed to get static login key", Toast.LENGTH_SHORT).show();
                                                    }

                                                    @Override
                                                    public void onResponse(Boolean validToken) {
                                                        if (!validToken) {
                                                            // Request user to generate a login token if it does not exist already.
                                                            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
                                                            dialog.setMessage("Account does not have static login token! Would you like to setup a new token?");
                                                            dialog.setTitle("AuthWare");
                                                            dialog.setPositiveButton("YES",
                                                                    new DialogInterface.OnClickListener() {
                                                                        public void onClick(DialogInterface dialog, int which) {
                                                                            newStaticLogin(new AuthWareResponseStaticKey() {
                                                                                @Override
                                                                                public void onError(String message) {
                                                                                    Toast.makeText(context, String.format("Failed to get a login token - %s", message), Toast.LENGTH_SHORT).show();
                                                                                }

                                                                                @Override
                                                                                public void onResponse(Boolean validTokenResponse) {
                                                                                    Toast.makeText(context, "Login Token Generated!", Toast.LENGTH_SHORT).show();
                                                                                }
                                                                            });
                                                                        }
                                                                    });
                                                            dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    Toast.makeText(context, "You will have to login again when session expires", Toast.LENGTH_SHORT).show();
                                                                }
                                                            });
                                                            AlertDialog alertDialog = dialog.create();
                                                            alertDialog.show();
                                                        }
                                                    }
                                                });
                                                authComplete = true;
                                                isValid.onResponse(true, null, null);
                                                try {
                                                    cookiePref.edit().putString("SessionId", response.getString("session")).apply();
                                                    Toast.makeText(context, "Persistent Session Saved", Toast.LENGTH_SHORT).show();
                                                } catch (Exception e) {
                                                    Toast.makeText(context, "Failed to save session token", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                    } else {
                                        authComplete = true;
                                        isValid.onResponse(true, null, null);
                                    }
                                } else {
                                    // Login required, get code if possible.
                                    Toast.makeText(context, "Login Required!", Toast.LENGTH_SHORT).show();
                                    String authCode = null;
                                    String sessionID = null;
                                    try {
                                        if (response.has("code")) { authCode = response.getString("code"); }
                                    } catch (Exception e) {
                                        Toast.makeText(context, "Oh No, Did not get a Express Login code from the server! Manual Login Required", Toast.LENGTH_SHORT).show();
                                    }
                                    try {
                                        if (response.has("session")) { sessionID = response.getString("session"); }
                                    } catch (Exception e) {
                                        Toast.makeText(context, "Oh No, Did not get a Session ID from the server! Login unavailable at this time", Toast.LENGTH_SHORT).show();
                                    }
                                    isValid.onResponse(false, authCode, sessionID);
                                }
                            } catch (Exception e) {
                                Log.e("AuthWare", String.format("Failed to get required response: %s", e));
                                Toast.makeText(context, "Login Unavailable, Server Error", Toast.LENGTH_SHORT).show();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(context, String.format("Login Unavailable, Network Error: %s", error), Toast.LENGTH_SHORT).show();
                            isValid.onError("CONNECTION_ERROR");
                        }
                    });
            loginCheckRequest.setShouldCache(false);
            NetworkManager.getInstance(context).addToRequestQueue(loginCheckRequest);
        }
    }

    public void clearLogin () {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);

        Log.i("AuthWare", "Logout Requested");
        // Get Static Login Key and Erase it!
        SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear();
        editor.apply();
        authComplete = false;


        final String url = String.format("%s://%s/discord/destroy", (prefs.getBoolean("swHTTPS", true)) ? "https" : "http", prefs.getString("etServerName", "seq.moe"));

        // Request to close session
        StringRequest destroyRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("AuthWare", "Logout Completed");
                Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("AuthWare", String.format("Logout Error: %s", error));
                Toast.makeText(context, String.format("Network Error: %s", error), Toast.LENGTH_SHORT).show();
            }
        });

        destroyRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(destroyRequest);
    }

    public interface AuthWareResponseStaticKey {
        void onError(String message);
        void onResponse(Boolean validTokenResponse);
    }
    public void getStaticLogin (AuthWareResponseStaticKey staticKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);

        Log.v("AuthWare/StaticLogin", "Requested to get static login token");
        final String url = String.format("%s://%s/discord/token?action=get", (prefs.getBoolean("swHTTPS", true)) ? "https" : "http", prefs.getString("etServerName", "seq.moe"));
        StringRequest tokenRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.equals("NO STATIC LOGIN TOKEN")) {
                    Log.w("AuthWare/StaticLogin", "Account Missing Static Login Token");
                    staticKey.onResponse(false);
                } else {
                    Log.i("AuthWare/StaticLogin", "Got Static Login Token");
                    SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("StaticLogin", response).apply();
                    staticKey.onResponse(true);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("AuthWare/StaticLogin", String.format("Failed to get the static login token: %s", error));
                staticKey.onError(String.valueOf(error));
            }
        });

        tokenRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(tokenRequest);
    }
    public void newStaticLogin (AuthWareResponseStaticKey staticKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);

        Log.v("AuthWare/StaticLogin", "Requested to generate static login token");
        final String url = String.format("%s://%s/discord/token?action=renew", (prefs.getBoolean("swHTTPS", true)) ? "https" : "http", prefs.getString("etServerName", "seq.moe"));
        StringRequest tokenRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("AuthWare/StaticLogin", "Got New Static Login Token");
                SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("StaticLogin", response).apply();
                staticKey.onResponse(true);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("AuthWare/StaticLogin", String.format("Failed to get new the static login token: %s", error));
                staticKey.onError(String.valueOf(error));
            }
        });
        tokenRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(tokenRequest);
    }

    public interface AuthWareRefreshAccount {
        void onError(String message);
        void onResponse(Boolean ok);
    }
    public void refreshSession (AuthWareRefreshAccount cb) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);

        Log.v("AuthWare/Session", "Session Refresh Requested");
        final String url = String.format("%s://%s/discord/refresh", (prefs.getBoolean("swHTTPS", true)) ? "https" : "http", prefs.getString("etServerName", "seq.moe"));
        StringRequest tokenRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i("AuthWare/Session", "Session Refreshed");
                cb.onResponse(true);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i("AuthWare/Session", String.format("Session Failed to Generate: %s", error));
                cb.onError(String.valueOf(error));
            }
        });
        tokenRequest.setShouldCache(false);
        NetworkManager.getInstance(context).addToRequestQueue(tokenRequest);

    }
}
