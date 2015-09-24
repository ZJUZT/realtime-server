package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.monitor.MonitorClient;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.model.ActionType;
import com.egeio.realtime.websocket.model.BaseResponse;
import com.egeio.realtime.websocket.model.UserInfo;
import com.egeio.realtime.websocket.model.UserSessionInfo;
import com.egeio.realtime.websocket.utils.AuthenticationUtils;
import com.egeio.realtime.websocket.utils.LogUtils;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by think on 2015/7/31.
 * This class is the key of the real-time server, it handles http request and establish webSocket connection,
 * perform different action according to the types of action
 */
public class SocketIOHandler {

    private static Logger logger = LoggerFactory
            .getLogger(SocketIOHandler.class);
    private static MyUUID uuid = new MyUUID();

    //status code
    private static final int OK_STATUS_CODE = 0;
    private static final int FAILED_STATUS_CODE = 1;
    private static final int INVALID_ACTION_STATUS_CODE = 2;


    //monitoring thread
    private static MonitorClient opentsdbClient;
    private static ScheduledExecutorService monitorExecutor = Executors
            .newSingleThreadScheduledExecutor();

    static {
        String metricPath = "/configuration/monitor/metric";
        String intervalPath = "/configuration/monitor/interval";
        opentsdbClient = MonitorClient.getInstance(
                Config.getConfig().getElement(metricPath).getText());

        long monitorInterval = Config.getNumber(intervalPath, 601);

        monitorExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendMonitorInfo();
            }
        }, monitorInterval, monitorInterval, TimeUnit.SECONDS);
    }

    //send monitor information
    private static void sendMonitorInfo() {
        Map<String, String> tags = new HashMap<>();

        long value = ChannelManager.getOnlineUserNum();
        tags.put("type", "online_users_count");

        try {
            tags.put("host", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            logger.error(uuid, "unknow host error!", e);
        }

        MonitorClient.Record record = new MonitorClient.Record(value, tags);
        List<MonitorClient.Record> records = new ArrayList<>();
        records.add(record);

        opentsdbClient.send(records);
    }

    @OnConnect
    public void onConnectHandler(SocketIOClient client) {
        logger.info(uuid, "connected to SocketIO Server");
    }

    @OnDisconnect
    public void onDisconnectHandler(SocketIOClient client) throws Exception {
        ChannelManager.removeUserClient(client);
        logger.info(uuid, "disconnect with SocketIO Server");
    }

    @OnEvent("realtime")
    public void onRealtimeHandler(SocketIOClient client, String reqJson, AckRequest ackSender) throws Exception {
        Gson gson = GsonUtils.getGson();
        JsonObject req = gson.fromJson(reqJson,JsonObject.class);
        LogUtils.logSessionInfo(logger, client, "channel received {}", req);
        if(req.get("action")==null||req.get("action_info")==null){
            logger.info(uuid,"Invalid login request");
            return;
        }
        String action = req.get("action").getAsString();
        BaseResponse baseRes = new BaseResponse(
                action, OK_STATUS_CODE);
        String res;
        if (action == null) {
            baseRes.setStatusCode(FAILED_STATUS_CODE);
            baseRes.setErrorMessage("No action to be taken");
            res = gson.toJson(baseRes);
            client.sendEvent("ack", res);
            return;
        }

        try {
            //parse json
            //{action:"login",action_info:{authToken:...}}
            if (action.equalsIgnoreCase(ActionType.ACTION_LOGIN)) {
                doLogin(req.get("action_info").getAsJsonObject(), client);
            } else if (action.equalsIgnoreCase(ActionType.ACTION_LOGOUT)) {
                doLogout(client);
            } else {
                baseRes.setStatusCode(INVALID_ACTION_STATUS_CODE);
                baseRes.setErrorMessage("invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            baseRes.setStatusCode(FAILED_STATUS_CODE);
        }

        res = gson.toJson(baseRes);
        if (res != null) {
//            channel.writeAndFlush(new TextWebSocketFrame(res));
            client.sendEvent("ack", res);
        }
    }

    /**
     * add user channel into mapping
     *
     * @param action_info action_info sent by client
     * @param client     user client
     * @throws Exception
     */
    private void doLogin(JsonObject action_info, SocketIOClient client) throws Exception {
//        JsonObject json = GsonUtils.getGson().fromJson(action_info, JsonObject.class);
        String authToken = action_info.get("auth_token").getAsString();
        UserInfo userInfo = AuthenticationUtils.getUserInfoFromToken(authToken);
        if (userInfo != null) {
            UserSessionInfo info = new UserSessionInfo(authToken,
                    userInfo.getUserId(), userInfo.getUserName(), client);
            ChannelManager.addUserClient(info, client);
            LogUtils.logSessionInfo(logger, client,
                    "Add user {} channel into mapping", info.getUserID());
        }
    }

    /**
     * remove user channel from mapping
     *
     * @param client user channel
     * @throws Exception
     */
    private void doLogout(SocketIOClient client) throws Exception {
        ChannelManager.removeUserClient(client);
        client.sendEvent("ack", "user logout");
    }

    /**
     * notify user new info in all the active channel he possesses
     *
     * @param jsonStr data from processor to be parsed
     * @throws Exception
     */
    public static void doSync(String jsonStr) throws Exception {
        Gson gson = GsonUtils.getGson();
        JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);
        //filter out not realtime type message
        if (jsonObject.get("jobType") == null || !jsonObject.get("jobType")
                .getAsString().equals("Realtime_job")) {
            return;
        }

        //get the users list to which the message will send
        JsonArray userList = jsonObject.get("user_id").getAsJsonArray();
        jsonObject.remove("user_id");

        //审阅、评论 user_type 会有区分
        HashMap<Long, Object> userTypeMap = new HashMap<>();
        HashMap<Long, String> userMessageMap = null;
        HashMap<Long, String> userUrlMap = null;
        String singleUrl = null;

        if (jsonObject.get("user_type_map") != null) {
            userTypeMap = gson.fromJson(jsonObject.get("user_type_map"),
                    new TypeToken<Map<Long, Object>>() {
                    }.getType());
            jsonObject.remove("user_type_map");
        }

        if (jsonObject.get("message_map") != null) {
            userMessageMap = gson.fromJson(jsonObject.get("message_map"),
                    new TypeToken<Map<Long, String>>() {
                    }.getType());
            jsonObject.remove("message_map");
        } else {
            throw new Exception("Can't find message_map");
        }

        if (jsonObject.get("url_map") != null) {
            userUrlMap = gson.fromJson(jsonObject.get("url_map"),
                    new TypeToken<Map<Long, String>>() {
                    }.getType());
            jsonObject.remove("url_map");
        } else if (jsonObject.get("url") != null) {
            singleUrl = jsonObject.get("url").getAsString();
        } else {
            throw new Exception("Can't find url information");
        }

        for (JsonElement userID : userList) {
            long uid = userID.getAsLong();
            Collection<SocketIOClient> clients = ChannelManager
                    .getClientsByUserID(uid);
            ChannelManager.displayUserChannelMapping();
            if (clients == null) {
                logger.info(uuid, "No active channels for user:{}", userID);
                return;
            }
            JsonObject msg = new JsonObject();
            msg.add("action", gson.fromJson(ActionType.ACTION_NEW_INFO,
                    JsonElement.class));
            if (userTypeMap != null) {
                Object userType = userTypeMap.get(uid);

                jsonObject.add("user_type",
                        gson.fromJson(String.valueOf(userType),
                                JsonElement.class));
            }

            String messageID = userMessageMap.get(uid);
            jsonObject.add("message_id",
                    gson.fromJson(messageID, JsonElement.class));

            String url;
            if (userUrlMap != null) {
                url = userUrlMap.get(uid);
            } else {
                url = singleUrl;
            }

            jsonObject.add("url", gson.fromJson(url, JsonElement.class));
            msg.add("action_info", jsonObject);

            String request = gson.toJson(msg);
            for (SocketIOClient client : clients) {
                if (!client.isChannelOpen()) {
                    continue;
                }
//                channel.writeAndFlush(new TextWebSocketFrame(request));
                client.sendEvent("new_info",request);
            }
        }

    }
}
