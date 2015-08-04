package com.egeio.realtime.websocket.utils;

import com.egeio.core.log.Logger;
import com.egeio.core.log.MyUUID;
import com.egeio.realtime.websocket.ChannelManager;
import io.netty.channel.Channel;

/**
 * Created by think on 2015/7/31.
 * This class deals with the issues about log
 */
public class LogUtils {
    public static void logSessionInfo(Logger logger, Channel channel,
            String message, Object... args) {
        MyUUID sessionID = ChannelManager.getUserSessionIDFromChannel(channel);
        logger.info(sessionID, message, args);
    }

    public static void logSessionError(Logger logger, Channel channel,
            String message, Object... args) {
        MyUUID sessionID = ChannelManager.getUserSessionIDFromChannel(channel);
        logger.error(sessionID, message, args);
    }
}
