package com.egeio.realtime.websocket;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.monitor.MonitorClient;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.model.*;
import com.egeio.realtime.websocket.utils.AuthenticationUtils;
import com.egeio.realtime.websocket.utils.LogUtils;
import com.egeio.realtime.websocket.utils.NetworkUtils;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by think on 2015/7/31.
 * This class is the key of the real-time server, it handles http request and establish webSocket connection,
 * perform different action according to the types of action
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private int port;

    private static Logger logger = LoggerFactory
            .getLogger(WebSocketHandler.class);
    private static MyUUID uuid = new MyUUID();

    //status code
    private static final int OK_STATUS_CODE = 0;
    private static final int FAILED_STATUS_CODE = 1;
    private static final int INVALID_ACTION_STATUS_CODE = 2;

    //    private static final int INITIAL_DELAY_IN_SECONDS = 2;
    private WebSocketServerHandshaker handShaker;
    private static final String WEBSOCKET_PATH = "/websocket";
    private static final String HTTP_REQUEST_PATH = "/push";

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
            @Override public void run() {
                sendMonitorInfo();
            }
        }, monitorInterval, monitorInterval, TimeUnit.SECONDS);
    }

    public WebSocketHandler(int port) {
        this.port = port;
    }

    //send moi
    private static void sendMonitorInfo() {
        Map<String, String> tags = new HashMap<>();

        long value = ChannelManager.getOnlineUserNum();
        tags.put("type", "online_users_count");

        try {
            tags.put("host", InetAddress.getLocalHost().getHostName());
        }
        catch (UnknownHostException e) {
            logger.error(uuid, "unknow host error!", e);
        }

        MonitorClient.Record record = new MonitorClient.Record(value, tags);
        List<MonitorClient.Record> records = new ArrayList<>();
        records.add(record);

        opentsdbClient.send(records);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext,
            Object o) throws Exception {
        if (o instanceof HttpRequest) {
            //HTTP request
            handleHttp(channelHandlerContext, (FullHttpRequest) o);
        }
        else {
            //WebSocket frame
            handleWebSocket(channelHandlerContext, (WebSocketFrame) o);
        }
    }

    /**
     * remove channel from mapping when channel is inactive
     *
     * @param ctx channel context
     * @throws Exception
     */
    @Override public void channelInactive(ChannelHandlerContext ctx)
            throws Exception {
        LogUtils.logSessionInfo(logger, ctx.channel(), "channel inactive");
        ChannelManager.removeUserChannel(ctx.channel());
        super.channelInactive(ctx);
    }

    /**
     * remove channel from mapping when channel is unregistered
     *
     * @param ctx channel context
     * @throws Exception
     */
    @Override public void channelUnregistered(ChannelHandlerContext ctx)
            throws Exception {
        LogUtils.logSessionInfo(logger, ctx.channel(), "channel unregistered");
        ChannelManager.removeUserChannel(ctx.channel());
        super.channelUnregistered(ctx);
    }

    /**
     * handle http request
     * 1. The http request sent before webSocket establishment
     * 2. The http request sent by HAProxy for load balance
     * 3. The http request sent by EventProcessor to hint new info to push to client
     *
     * @param channelHandlerContext channel context
     * @param request               request sent from client
     * @throws Exception
     */
    private void handleHttp(ChannelHandlerContext channelHandlerContext,
            FullHttpRequest request) throws Exception {
        if (request.method() == GET) {
            if (request.uri().equals(WEBSOCKET_PATH) || request.uri()
                    .equals("/")) {
                //handshake
                WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                        getWebSocketLocation(), null, true);
                handShaker = wsFactory.newHandshaker(request);
                if (handShaker == null) {
                    WebSocketServerHandshakerFactory
                            .sendUnsupportedVersionResponse(
                                    channelHandlerContext.channel());
                }
                else {
                    handShaker.handshake(channelHandlerContext.channel(),
                            request);
                }
            }
        }
        //POST method
        //handle action to push new information in http request
        if (request.method() == POST && request.uri()
                .equals(HTTP_REQUEST_PATH)) {

            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                    new DefaultHttpDataFactory(false), request);
            InterfaceHttpData postData = decoder.getBodyHttpData("userID");
            logger.info(uuid, "HTTP request content:{}", postData);
            String userID = "";
            if (postData.getHttpDataType()
                    == InterfaceHttpData.HttpDataType.Attribute) {
                Attribute attribute = (Attribute) postData;
                userID = attribute.getValue();
            }

            if (userID != null && !userID.equals("")) {
                sendHttpResponse(channelHandlerContext,
                        new DefaultFullHttpResponse(HTTP_1_1, OK));
                doSync(Long.valueOf(userID));
            }
            else {
                logger.info(uuid, "Can't get userID from HTTP request");
            }
        }

    }

    /**
     * Get webSocket location
     *
     * @return webSocket location
     */
    private String getWebSocketLocation() {
        String protocol = "ws";
        String uri = null;
        try {
            uri = String.format("%s://%s:%s%s", protocol,
                    NetworkUtils.getExternalIpAddress(), port, WEBSOCKET_PATH);
        }
        catch (Exception e) {
            logger.error(uuid, e, "failed to get the ip address, exit");
            System.exit(-1);
        }
        return uri;
    }

    /**
     * Send http response
     *
     * @param ctx channel context
     * @param res http response
     */
    private void sendHttpResponse(ChannelHandlerContext ctx,
            FullHttpResponse res) {
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled
                    .copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ctx.channel().writeAndFlush(res);
        ctx.close();
    }

    /**
     * parse webSocket frame, get the content and perform the correct action
     *
     * @param channelHandlerContext channel context
     * @param frame                 webSocket frame
     * @throws Exception
     */
    private void handleWebSocket(ChannelHandlerContext channelHandlerContext,
            WebSocketFrame frame) throws Exception {
        Channel channel = channelHandlerContext.channel();
        if (frame instanceof CloseWebSocketFrame) {
            //user closing channel
            LogUtils.logSessionInfo(logger, channel,
                    "Channel closed from client");

            //remove the channel from mapping
            ChannelManager.removeUserChannel(channel);

            handShaker.close(channelHandlerContext.channel(),
                    (CloseWebSocketFrame) frame.retain());
            return;
        }
        else if (frame instanceof PingWebSocketFrame) {
            channel.writeAndFlush(
                    new PingWebSocketFrame(frame.content().retain()));
            return;
        }
        else if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported",
                            frame.getClass().getName()));
        }

        String req = ((TextWebSocketFrame) frame).text();
        LogUtils.logSessionInfo(logger, channel, "channel received {}", req);
        String res;
        JsonObject reqJson = GsonUtils.getGson()
                .fromJson(req, JsonObject.class);
        BaseResponse baseRes = new BaseResponse(
                reqJson.get("action").getAsString(), OK_STATUS_CODE);
        try {
            if (reqJson.get("action") == null) {
                baseRes.setStatusCode(FAILED_STATUS_CODE);
                baseRes.setErrorMessage("No action to be taken");
                res = GsonUtils.getGson().toJson(baseRes);
                channel.writeAndFlush(new TextWebSocketFrame(res));
                return;
            }

            //parse json
            //{action:"login",action_info:{authToken:...,deviceId:...}}
            if (reqJson.get("action").getAsString()
                    .equalsIgnoreCase(ActionType.ACTION_LOGIN)) {
                doLogin(reqJson, channel);
            }

            else if (reqJson.get("action").getAsString()
                    .equalsIgnoreCase(ActionType.ACTION_LOGOUT)) {
                doLogout(channel);
            }
            else {
                baseRes.setStatusCode(INVALID_ACTION_STATUS_CODE);
                baseRes.setErrorMessage("invalid action");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            baseRes.setStatusCode(FAILED_STATUS_CODE);
        }

        res = GsonUtils.getGson().toJson(baseRes);
        if (res != null) {
            channel.writeAndFlush(new TextWebSocketFrame(res));
        }

    }

    /**
     * add user channel into mapping
     *
     * @param json    json object sent by client
     * @param channel user channel
     * @throws Exception
     */
    private void doLogin(JsonObject json, Channel channel) throws Exception {
        String authToken = json.get("action_info").getAsJsonObject()
                .get("auth_token").getAsString();

        String deviceId = json.get("action_info").getAsJsonObject()
                .get("device_id").getAsString();
        UserInfo userInfo = AuthenticationUtils.getUserInfoFromToken(authToken);

        // put it into the map
        UserSessionInfo info = new UserSessionInfo(authToken, deviceId,
                userInfo.getUserId(), userInfo.getUserName());
        ChannelManager.addUserChannel(info, channel);
        LogUtils.logSessionInfo(logger, channel, "Add user channel into map {}",
                info.getUserID());
    }

    /**
     * remove user channel from mapping
     *
     * @param channel user channel
     * @throws Exception
     */
    private void doLogout(Channel channel) throws Exception {
        ChannelManager.removeUserChannel(channel);
        channel.writeAndFlush("user logout");
        channel.writeAndFlush(new CloseWebSocketFrame());
    }

    /**
     * notify user new info in all the active channel he possesses
     *
     * @param userID user to be notified
     * @throws Exception
     */
    private void doSync(long userID) throws Exception {
        Collection<Channel> channels = ChannelManager
                .getChannelByUserID(userID);
        ChannelManager.displayUserChannelMapping();
        if (channels == null) {
            logger.info(uuid, "No active channels for user:{}", userID);
            return;
        }
        BaseAction msg = new BaseAction(ActionType.ACTION_NEW_INFO);
        String request = GsonUtils.getGson().toJson(msg);
        for (Channel channel : channels) {
            if (!channel.isOpen()) {
                continue;
            }
            channel.writeAndFlush(new TextWebSocketFrame(request));
        }
    }
}
