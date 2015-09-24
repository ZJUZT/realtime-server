package com.egeio.realtime.websocket;

import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
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
                SocketIOHandler.doSync(dataFromProcessor);
            } else {
                logger.info(uuid, "Can't get userID from HTTP request");
            }
        }

    }
}
