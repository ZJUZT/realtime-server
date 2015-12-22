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
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String Event = "realtime";


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

    @OnEvent(Event)
    public void onRealtimeHandler(SocketIOClient client, String reqJson, AckRequest ackSender) throws Exception {
        Gson gson = GsonUtils.getGson();
        JsonObject req = gson.fromJson(reqJson, JsonObject.class);
        LogUtils.logSessionInfo(logger, client, "channel received {}", req);

        if (req.get("action") == null || req.get("action_info") == null) {
            logger.error(uuid, "Invalid login request");
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
            client.sendEvent(Event, res);
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
            client.sendEvent(Event, res);
        }
    }

    /**
     * add user channel into mapping
     *
     * @param action_info action_info sent by client
     * @param client      user client
     * @throws Exception
     */
    private void doLogin(JsonObject action_info, SocketIOClient client) throws Exception {

        String authToken = action_info.get("auth_token").getAsString();
        UserInfo userInfo = AuthenticationUtils.getUserInfoFromToken(authToken);

        if (userInfo != null) {
            UserSessionInfo info = new UserSessionInfo(authToken,
                    userInfo.getUserId(), userInfo.getUserName(), client);
            ChannelManager.addUserClient(info, client);
            UserSessionInfo sessionInfo = new UserSessionInfo(authToken, info.getUserID(), info.getUserName(), client);
            ChannelManager.setUserSessionInfoInChannel(client, sessionInfo);
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
        client.sendEvent("realtime", "user logout");
    }


}
