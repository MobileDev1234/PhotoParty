package com.hr.photoparty;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by RabbitJang on 10/24/2018.
 */

public class APIManager {
    public static final int HTTP_POST           = 1;

    public static String SERVER_ADDR                       = "http://34.214.47.217/photo_party/api/";
    public static String PAYMENT_SERVER_ADDR               = "http://34.214.47.217:8093/payment/";

    private static APIManager instance = null;
    private APIManagerCallback callback = null;

    public static APIManager getInstance() {
        if(instance == null) {
            instance = new APIManager();
        }
        return instance;
    }

    // Set callback function after finished api request..
    public void setCallback(APIManagerCallback callback) {
        this.callback = callback;
    }

    public void authenticateGuest(JSONObject object) {
        String API_URL = String.format("login/authenticate_guest.php");
        APITask task = new APITask(SERVER_ADDR, API_URL, object, HTTP_POST, false);
        task.execute((Void) null);
    }

    public void updateUploadedCount(JSONObject object) {
        String API_URL = String.format("update/update_uploaded_count.php");
        APITask task = new APITask(SERVER_ADDR, API_URL, object, HTTP_POST, false);
        task.execute((Void) null);
    }

    public void createCustomer(JSONObject object) {
        String API_URL = String.format("customer");
        APITask task = new APITask(PAYMENT_SERVER_ADDR, API_URL, object, HTTP_POST, true);
        task.execute((Void) null);
    }

    public void charge(JSONObject object) {
        String API_URL = String.format("charge");
        APITask task = new APITask(PAYMENT_SERVER_ADDR, API_URL, object, HTTP_POST, true);
        task.execute((Void) null);
    }
    // API Task..
    private class APITask extends AsyncTask<Void, Void, Boolean> {
        /* ---------------- API Task Variables ---------------- */
        // For calling api request..
        private String serverAddr = "";
        private String apiURL = "";
        private JSONObject reqObject = null;
        private InputStream inputStream;
        private int method = 0;
        private boolean isFormUrlEncoded = false;

        // Result of api request..
        private JSONObject result = null;

        /* ---------------- API Task Functions ---------------- */
        APITask(String serverAddr, String apiURL, JSONObject reqParams, int method, boolean isFormUrlEncoded) {
            this.serverAddr = serverAddr;
            this.apiURL = apiURL;
            this.reqObject = reqParams;
            this.method = method;
            this.isFormUrlEncoded = isFormUrlEncoded;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            result = requestAPI();
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            if (callback != null) {
                callback.APICallback(result);
            }
        }

        @Override
        protected void onCancelled() {
            if (callback != null) {
                callback.APICallback(null);
            }
        }

        private JSONObject requestAPI() {
            JSONObject result = null;

            try {
                String apiRequestURL = serverAddr + apiURL;
                HttpClient httpclient = new DefaultHttpClient();
                if (reqObject == null) {
                    reqObject = new JSONObject();
                }

                if(SharedData.getInstance().userId != 0 && SharedData.getInstance().token != null) {
                    reqObject.accumulate("UserId", SharedData.getInstance().userId);
                    reqObject.accumulate("Token", SharedData.getInstance().token);
                }

                HttpPost httpPost = new HttpPost(apiRequestURL);
                if(isFormUrlEncoded) {
                    List<NameValuePair> pairObj = new ArrayList<NameValuePair>();
                    Iterator<String> keys = reqObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String object = reqObject.getString(key);
                        pairObj.add(new BasicNameValuePair(key, object));
                    }
                    UrlEncodedFormEntity se = new UrlEncodedFormEntity(pairObj);
                    httpPost.setEntity(se);
                }
                else {
                    StringEntity se = new StringEntity(reqObject.toString(), HTTP.UTF_8);
                    httpPost.setEntity(se);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");
                }

                HttpResponse httpResponse = httpclient.execute(httpPost);
                inputStream = httpResponse.getEntity().getContent();
                if (inputStream != null) {
                    String iData = convertInputStreamToString(inputStream);
                    result = new JSONObject(iData);
                } else {
                    Log.d("[APITask] requestAPI", "No input stream prepared!");
                }

            } catch (Exception e) {
                Log.e("[APITask] requestAPI", e.getLocalizedMessage());
            }

            return result;
        }

        private String convertInputStreamToString(InputStream inputStream) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            String result = "";
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    result += line;
                }

                inputStream.close();
            } catch (Exception e) {
                Log.e("[APITask] ConvertInput", e.getLocalizedMessage());
            }
            return result;
        }
    }
}