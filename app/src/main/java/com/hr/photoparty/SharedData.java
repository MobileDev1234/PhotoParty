package com.hr.photoparty;

/**
 * Created by RabbitJang on 10/24/2018.
 */

public class SharedData {
    private static SharedData instance = null;

    public int userId;
    public String deviceId;
    public String token;
    public String userName;
    public int uploadedCount;
    public String customerId;
    public int paid;
    public String publicKey;
    public String accessToken;

    public static SharedData getInstance() {
        if(instance == null) {
            instance = new SharedData();
            instance.initInstance();
        }
        return instance;
    }

    public void initInstance() {
        userId = 0;
        deviceId = "";
        token = "";
        userName = "";
        uploadedCount = 0;
        customerId = "";
        paid = 0;
    }
}
