package com.egeio.realtime.websocket.model;

public class UserInfo {
    private String userName;
    private long userId;

    public UserInfo(long id, String name) {
        this.userId = id;
        this.userName = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }
}
