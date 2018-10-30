package com.hr.photoparty;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;

import static com.hr.photoparty.Util.VERSION;

/**
 * Created by RabbitJang on 10/13/2018.
 */

public class SplashActivity extends Activity {
    SharedPreferences preferences;
    String deviceId;
    private DropboxAPI<AndroidAuthSession> mDBApi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        initValue();
        configureDesign();
        String accessToken = preferences.getString("AccessToken", "");
        if(!accessToken.equals("")) {
            SharedData.getInstance().accessToken = accessToken;
            doGuestLogin();
        }
        /*Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doGuestLogin();
            }
        }, 1500);*/
    }

    private void initValue() {
        preferences = PreferenceManager.getDefaultSharedPreferences(SplashActivity.this);
        AppKeyPair appKeys = new AppKeyPair("25zyw1bojlmiji7", "qvf0iuw0tqeynh2");
        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
    }

    private void configureDesign() {
        TextView loginTv = findViewById(R.id.login_tv);
        loginTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDBApi.getSession().startOAuth2Authentication(SplashActivity.this);
            }
        });
    }

    protected void onResume() {
        super.onResume();

        if (mDBApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                mDBApi.getSession().finishAuthentication();
                SharedData.getInstance().accessToken = mDBApi.getSession().getOAuth2AccessToken();
                preferences.edit().putString("AccessToken", SharedData.getInstance().accessToken).apply();
                doGuestLogin();
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
    }

    private void doGuestLogin() {
        APIManager.getInstance().setCallback(new APIManagerCallback() {
            @Override
            public void APICallback(JSONObject objAPIResult) {
                Util.hideProgressDialog();
                if(objAPIResult == null) {
                    Util.showToast("Check network and try again", SplashActivity.this);
                    return;
                }
                try {
                    if(objAPIResult.getBoolean("Success")) {
                        JSONObject dataObj = objAPIResult.getJSONObject("Data");
                        SharedData.getInstance().userId = dataObj.getInt("Id");
                        SharedData.getInstance().deviceId = deviceId;
                        SharedData.getInstance().token = dataObj.getString("Token");
                        SharedData.getInstance().userName = dataObj.getString("UserName");
                        SharedData.getInstance().uploadedCount = dataObj.getInt("UploadedCount");
                        SharedData.getInstance().customerId = dataObj.getString("CustomerId");
                        SharedData.getInstance().paid = dataObj.getInt("Paid");
                        SharedData.getInstance().publicKey = objAPIResult.getString("PublicKey");
                        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                        startActivity(intent);
                    }
                    else {
                        String message = objAPIResult.getString("Message");
                        Util.showToast(message, SplashActivity.this);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Util.showToast("Failed and try again.", SplashActivity.this);
                }
            }
        });

        Util.showProgressDialog("Loading..", SplashActivity.this);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        JSONObject object = new JSONObject();
        try {
            object.accumulate("DeviceId", deviceId);
            object.accumulate("PhoneType", "android");
            object.accumulate("Version", VERSION);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        APIManager.getInstance().authenticateGuest(object);
    }
}
