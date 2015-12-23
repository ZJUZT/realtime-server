package com.egeio.realtime.websocket.model;

import com.egeio.core.utils.GsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Created by think on 2015/12/23.
 * RealTime message sent to client
 */
public class RealTimeMsg {

    @SerializedName("action") private String action;

    @SerializedName("action_info") private JsonObject actionInfo;

    /**
     * get component from json object by name
     * and convert to specific type
     * then remove the component from json object
     *
     * @param jsonObject message in json format
     * @param memberName json member name
     * @param type       class of member value
     * @param <T>        generic
     * @return json member value
     * @throws Exception
     */
    public static <T> T getByMemberName(JsonObject jsonObject,
            String memberName, Class<T> type) throws Exception {

        if (jsonObject == null || jsonObject.get(memberName) == null) {
            return null;
        }
        T userTypeMap = GsonUtils.getGson()
                .fromJson(jsonObject.get(memberName), type);
        jsonObject.remove(memberName);

        return userTypeMap;
    }

    public RealTimeMsg(String action) {
        this.action = action;
    }

    public void setActionInfo(JsonObject actionInfo) {
        this.actionInfo = actionInfo;
    }

    public void addActionInfo(String memberName, String value)
            throws Exception {
        actionInfo.add(memberName,
                GsonUtils.getGson().fromJson(value, JsonElement.class));
    }
}
