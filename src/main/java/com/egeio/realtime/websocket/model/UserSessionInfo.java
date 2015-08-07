package com.egeio.realtime.websocket.model;

import com.egeio.core.log.MyUUID;
import com.google.gson.annotations.SerializedName;
import io.netty.channel.Channel;

/**
 * Created by think on 2015/7/31.
 * This class defines the information stored in a session for user
 */
public class UserSessionInfo {
    private transient String token;
    private transient MyUUID sessionID;
    private Channel channel;

    @SerializedName("user_id") private long userID;

    @SerializedName("user_name") private String userName;

    public UserSessionInfo(String token, long userID, String userName,
            Channel channel) {
        //user userID-deviceID in UUID
        this.sessionID = new MyUUID(String.format("%s-%s", userID, channel));
        this.token = token;
        this.userID = userID;
        this.userName = userName;
    }

    public MyUUID getSessionID() {
        return sessionID;
    }

    public String getToken() {
        return token;
    }

    public long getUserID() {
        return userID;
    }

}
