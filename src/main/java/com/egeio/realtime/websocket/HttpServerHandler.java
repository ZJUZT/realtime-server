package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.model.ActionType;
import com.google.common.reflect.TypeToken;
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
import java.util.HashMap;
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
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
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
            ByteBuf buf = Unpooled
                    .copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ctx.channel().writeAndFlush(res);
        ctx.close();
    }

    private void handleHttp(ChannelHandlerContext channelHandlerContext,
                            FullHttpRequest request) throws Exception {
        //POST method
        //handle action to push new information in http request
        if (request.getMethod() == POST && request.getUri()
                .equals(HTTP_REQUEST_PATH)) {

            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                    new DefaultHttpDataFactory(false), request);
            InterfaceHttpData postData = decoder.getBodyHttpData("data");
            logger.info(uuid, "HTTP request content:{}", postData);

            if (postData == null) {
                logger.info(uuid, "No data in this http request found");
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
            } else {
                logger.info(uuid, "Can't get userID from HTTP request");
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
//            ChannelManager.displayUserChannelMapping();
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
                client.sendEvent(Event,request);
            }
        }

    }
}
