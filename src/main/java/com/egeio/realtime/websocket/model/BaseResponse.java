package com.egeio.realtime.websocket.model;

import com.google.gson.annotations.SerializedName;

/**
 * This is the base entity class of responses
 *
 * @author rogerlai
 * @date 2014/12/08
 */

public class BaseResponse {
    @SerializedName("status_code") private int statusCode;

    @SerializedName("action") private String action;

    @SerializedName("action_info") private Object actionInfo;

    @SerializedName("error_message") private String errorMessage;

    public BaseResponse(String action, int statusCode) {
        this.action = action;
        this.statusCode = statusCode;
    }

    public BaseResponse(String action, int statusCode, Object actionInfo) {
        this.action = action;
        this.statusCode = statusCode;
        this.actionInfo = actionInfo;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setActionInfo(Object actionInfo) {
        this.actionInfo = actionInfo;
    }

    public void setStatusCode(int code) {
        this.statusCode = code;
    }

    public void setErrorMessage(String message) {
        this.errorMessage = message;
    }
}
