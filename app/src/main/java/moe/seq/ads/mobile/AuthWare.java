package moe.seq.ads.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

public class AuthWare {
    public static final String SEQUENZIA_HOST = "https://beta.seq.moe";
    public static final String SEQUENZIA_VALID_LOGIN = String.format("%s/ping", SEQUENZIA_HOST);
    public static final String SEQUENZIA_TOKEN_MANAGER = String.format("%s/discord/token", SEQUENZIA_HOST);
    public static final String SEQUENZIA_CLEAR_LOGIN = String.format("%s/discord/destroy", SEQUENZIA_HOST);
    public static final String SEQUENZIA_REFRESH_SESSION = String.format("%s/discord/refresh", SEQUENZIA_HOST);
    public static Boolean authComplete = false;

    Context context;

    public AuthWare(Context context) {
        this.context = context;
    }

    public interface AuthWareResponseisValid {
        void onError(String message);
        void onResponse(Boolean loginSuccess, String authCode);
    }

    public void validateLogin (Boolean useLoginToken, AuthWareResponseisValid isValid) {
        if (authComplete) {
            // Fast Return if already logged in.
            isValid.onResponse(true, null);
        } else {
            SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
            String storedLoginToken = sharedPref.getString("StaticLogin", null);
            String tokenLogin = "";
            if (useLoginToken && storedLoginToken != null) {
                tokenLogin = String.format("&key=%s", storedLoginToken);
            }
            final String url = String.format("%s?json=true%s", SEQUENZIA_VALID_LOGIN, tokenLogin);

            JsonObjectRequest loginCheckRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            Log.w("AuthWare", response.toString());
                            try {
                                if (response.getBoolean("loggedin")) {
                                    Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show();
                                    refreshSession(new AuthWareRefreshAccount() {
                                        @Override
                                        public void onError(String message) {
                                            Toast.makeText(context, "Failed to load Account Session", Toast.LENGTH_SHORT).show();
                                            isValid.onResponse(false, null);
                                        }

                                        @Override
                                        public void onResponse(Boolean ok) {
                                            getStaticLogin(new AuthWareResponseStaticKey() {
                                                @Override
                                                public void onError(String message) {
                                                    Log.e("AuthWare", "Failed to get static login key");
                                                    Toast.makeText(context, "Account does not have static login token!", Toast.LENGTH_LONG).show();
                                                }

                                                @Override
                                                public void onResponse(String staticLoginToken) {
                                                    Log.i("AuthWare", String.format("Static Login Key: %s", staticLoginToken));
                                                }
                                            });
                                            authComplete = true;
                                            isValid.onResponse(true, null);
                                        }
                                    });
                                } else {
                                    Toast.makeText(context, "Login Required!", Toast.LENGTH_SHORT).show();
                                    String authCode = null;
                                    if (response.has("code")) {
                                        authCode = response.getString("code");
                                    }
                                    isValid.onResponse(false, authCode);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            isValid.onError("Failed to login due to a error");
                            error.printStackTrace();
                        }
                    });

            NetworkManager.getInstance(context).addToRequestQueue(loginCheckRequest);
        }
    }

    public void clearLogin () {
        SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear();
        editor.apply();

        StringRequest destroyRequest = new StringRequest(Request.Method.GET, SEQUENZIA_CLEAR_LOGIN, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, "Logged out but with error", Toast.LENGTH_SHORT).show();
            }
        });

        NetworkManager.getInstance(context).addToRequestQueue(destroyRequest);
    }

    public interface AuthWareResponseStaticKey {
        void onError(String message);
        void onResponse(String staticLoginToken);
    }

    public void getStaticLogin (AuthWareResponseStaticKey staticKey) {
        final String url = String.format("%s?action=get", SEQUENZIA_TOKEN_MANAGER);

        StringRequest tokenRequest = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.equals("NO STATIC LOGIN TOKEN")) {
                    // Get a token
                    staticKey.onError("No Static Token Available");
                } else {
                    SharedPreferences sharedPref = context.getSharedPreferences("seq.authWare", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("StaticLogin", response);
                    editor.apply();
                    staticKey.onResponse(response);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        NetworkManager.getInstance(context).addToRequestQueue(tokenRequest);

    }


    public interface AuthWareRefreshAccount {
        void onError(String message);
        void onResponse(Boolean ok);
    }

    public void refreshSession (AuthWareRefreshAccount cb) {
        StringRequest tokenRequest = new StringRequest(Request.Method.GET, SEQUENZIA_REFRESH_SESSION, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                cb.onResponse(true);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                cb.onError("Failed to refresh Account Session");
                error.printStackTrace();
            }
        });
        NetworkManager.getInstance(context).addToRequestQueue(tokenRequest);

    }


}
