package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.model.ActionType;
import com.egeio.realtime.websocket.model.RealTimeMsg;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.util.CharsetUtil;

import java.util.Collection;
import java.util.Map;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by think on 2015/9/23.
 * This class is responsible for handling the post from event processor
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {
    private int port;

    private static Logger logger = LoggerFactory
            .getLogger(HttpServerHandler.class);
    private static MyUUID uuid = new MyUUID();
    private static final String HTTP_REQUEST_PATH = "/push";

    private static final String Event = "realtime";

    public HttpServerHandler(int port) {
        this.port = port;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext,
            Object o) throws Exception {
        if (o instanceof HttpRequest) {
            handleHttp(channelHandlerContext, (FullHttpRequest) o);
        }
    }

    /**
     * Send http response
     *
     * @param ctx channel context
     * @param res http response
     */
    private void sendHttpResponse(ChannelHandlerContext ctx,
            FullHttpResponse res) {
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(),
                    CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ctx.channel().writeAndFlush(res);
        ctx.close();
    }

    /**
     * @param channelHandlerContext context
     * @param request               http request
     * @throws Exception
     */
    private void handleHttp(ChannelHandlerContext channelHandlerContext,
            FullHttpRequest request) throws Exception {

        //POST method
        //handle http request to push new info
        if (request.getMethod() == POST && request.getUri()
                .equals(HTTP_REQUEST_PATH)) {

            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                    new DefaultHttpDataFactory(false), request);
            InterfaceHttpData postData = decoder.getBodyHttpData("data");
            logger.info(uuid, "HTTP request content:{}", postData);

            if (postData == null) {
                logger.error(uuid, "No data in this http request found");
                sendHttpResponse(channelHandlerContext,
                        new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
                return;
            }
            String dataFromProcessor = "";
            if (postData.getHttpDataType()
                    == InterfaceHttpData.HttpDataType.Attribute) {
                Attribute attribute = (Attribute) postData;
                dataFromProcessor = attribute.getValue();
            }

            if (dataFromProcessor != null && !dataFromProcessor.equals("")) {
                sendHttpResponse(channelHandlerContext,
                        new DefaultFullHttpResponse(HTTP_1_1, OK));
                doSync(dataFromProcessor);
            }
            else {
                logger.error(uuid, "Can't get userID from HTTP request");
            }
        }

    }

    /**
     * notify user new info in all the active channel he possesses
     *
     * @param jsonStr data from processor to be parsed
     * @throws Exception
     */
    public void doSync(String jsonStr) throws Exception {
        Gson gson = GsonUtils.getGson();
        JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);

        //filter
        if (jsonObject.get("jobType") == null || !jsonObject.get("jobType")
                .getAsString().equals("Realtime_job")) {
            return;
        }

        //get the users list which the message will send to
        JsonArray userList = RealTimeMsg
                .getByMemberName(jsonObject, "user_id", JsonArray.class);

        // notice difference of user_type between review and comments
        Map userTypeMap = RealTimeMsg
                .getByMemberName(jsonObject, "user_type_map", Map.class);

        Map userMessageMap = RealTimeMsg
                .getByMemberName(jsonObject, "message_map", Map.class);

        if (userMessageMap == null) {
            throw new Exception("Can't find message_map");
        }

        Map userUrlMap = RealTimeMsg
                .getByMemberName(jsonObject, "url_map", Map.class);
        String singleUrl = RealTimeMsg
                .getByMemberName(jsonObject, "url", String.class);

        if (userUrlMap == null && singleUrl == null) {
            throw new Exception("Can't find url information");

        }

        for (JsonElement userID : userList) {

            long uid = userID.getAsLong();
            Collection<SocketIOClient> clients = ChannelManager
                    .getClientsByUserID(uid);

            if (clients == null) {
                logger.info(uuid, "No active channels for user:{}", userID);
                continue;
            }

            RealTimeMsg msg = new RealTimeMsg(ActionType.ACTION_NEW_INFO);
            msg.setActionInfo(jsonObject);

            if (userTypeMap != null) {
                Object userType = userTypeMap.get(uid);

                msg.addActionInfo("user_type", String.valueOf(userType));
            }

            String messageID = String.valueOf(userMessageMap.get(uid));

            msg.addActionInfo("message_id", messageID);
            String url;
            if (userUrlMap != null) {
                url = String.valueOf(userUrlMap.get(uid));
            }
            else {
                url = singleUrl;
            }

            msg.addActionInfo("url", url);
            String request = gson.toJson(msg);
            for (SocketIOClient client : clients) {
                if (!client.isChannelOpen()) {
                    continue;
                }
                client.sendEvent(Event, request);
            }
        }

    }
}
