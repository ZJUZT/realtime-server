package com.egeio.realtime.websocket.model;

import com.google.gson.annotations.SerializedName;

/**
 * Created by think on 2015/7/31.
 * Base entity for class of actions
 */
public class BaseAction {
    @SerializedName("action") protected String action;

    @SerializedName("action_info") protected Object actionInfo;

    public BaseAction(String action, Object info) {
        this.action = action;
        this.actionInfo = info;
    }

}
