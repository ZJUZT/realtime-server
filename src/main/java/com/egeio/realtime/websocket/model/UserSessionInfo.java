package com.egeio.realtime.websocket.model;

import com.egeio.core.log.MyUUID;
import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * Created by think on 2015/7/31.
 * This class defines the information stored in a session for user
 */
public class UserSessionInfo {
    private transient String token;
    private transient MyUUID sessionID;

    @SerializedName("device_id") private String deviceID;

    @SerializedName("user_id") private long userID;

    @SerializedName("user_name") private String userName;

    @SerializedName("connect_time") private Date connectTime;

    public UserSessionInfo(String token, String deviceID, long userID,
            String userName) {
        //user userID-deviceID in UUID
        this.sessionID = new MyUUID(String.format("%s-%s", userID, deviceID));
        this.token = token;
        this.deviceID = deviceID;
        this.userID = userID;
        this.connectTime = new Date();
    }

    public MyUUID getSessionID() {
        return sessionID;
    }

    public String getToken() {
        return token;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public long getUserID() {
        return userID;
    }

}
