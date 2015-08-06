package com.egeio.realtime.websocket;

import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.model.ActionType;
import com.egeio.realtime.websocket.model.BaseAction;
import com.egeio.realtime.websocket.utils.LogUtils;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Created by think on 2015/7/31.
 * This class is responsible for handling idle event in the pipeline of netty channel
 */
public class IdleEventHandler extends ChannelHandlerAdapter {
    public static Logger logger = LoggerFactory
            .getLogger(IdleEventHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // send ping message to client
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame(getPingMessage()));
            }
            else {
                //close the channel if client doesn't response for a given time
                LogUtils.logSessionInfo(logger, ctx.channel(),
                        "This channel has been idle {} for a while, the channel is closing",
                        event.state().name());
                ChannelManager.removeUserChannel(ctx.channel());
                ctx.channel().writeAndFlush(
                        new CloseWebSocketFrame(4009, "user in idle state"));
            }
        }
    }

    private String getPingMessage() throws Exception {
        BaseAction ping = new BaseAction(ActionType.ACTION_PING);
        return GsonUtils.getGson().toJson(ping);
    }
}
