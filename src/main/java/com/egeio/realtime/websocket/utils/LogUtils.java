package com.egeio.realtime.websocket.utils;

import com.corundumstudio.socketio.SocketIOClient;
import com.egeio.core.log.Logger;
import com.egeio.core.log.MyUUID;
import com.egeio.realtime.websocket.ChannelManager;

/**
 * Created by think on 2015/7/31.
 * This class deals with the issues about log
 */
public class LogUtils {
    public static void logSessionInfo(Logger logger, SocketIOClient client,
            String message, Object... args) {
        MyUUID sessionID = ChannelManager.getSessionID(client);
        logger.info(sessionID, message, args);
    }

    public static void logSessionError(Logger logger, SocketIOClient client,
            String message, Object... args) {
        MyUUID sessionID = ChannelManager.getSessionID(client);
        logger.error(sessionID, message, args);
    }
}
