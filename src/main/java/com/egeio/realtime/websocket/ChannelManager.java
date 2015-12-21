package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.SocketIOClient;
import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.realtime.websocket.model.UserSessionInfo;
import com.egeio.realtime.websocket.utils.LogUtils;
import com.egeio.realtime.websocket.utils.MemCachedUtil;

import java.util.Collection;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by think on 2015/7/31.
 * This class manages all the information for each user channel
 */
public class ChannelManager {
    private static Logger logger = LoggerFactory
            .getLogger(ChannelManager.class);
    private static MyUUID uuid = new MyUUID();

//    private static final AttributeKey<UserSessionInfo> userSessionKey = AttributeKey
//            .valueOf("userSessionInfo");
    private static ConcurrentHashMap<Long, Vector<SocketIOClient>> userClientMapping = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<SocketIOClient, UserSessionInfo> ClientSessionMap = new ConcurrentHashMap<>();

    //configuration the ip address and port for the server
    //which will be stored into memory cache
    private final static String serverHost = Config.getConfig()
            .getElement("/configuration/ip_address").getText();
    private final static long serverPort = Config
            .getNumber("/configuration/http_request_port", 8080);


    /*private static void setUserSessionInfoInChannel(Channel channel,
                                                    UserSessionInfo info) {
        channel.attr(userSessionKey).set(info);
    }*/

    /*private static UserSessionInfo getUserSessionInfoFromChannel(
            Channel channel) {
        return channel.attr(userSessionKey).get();
    }*/

    /*public static MyUUID getUserSessionIDFromChannel(Channel channel) {
        UserSessionInfo info = channel.attr(userSessionKey).get();
        if (info == null) {
            return null;
        }
        return info.getSessionID();
    }*/


    public static void setUserSessionInfoInChannel(SocketIOClient client, UserSessionInfo info) {
        ClientSessionMap.put(client, info);
    }

    public static UserSessionInfo getUserSessionInfo(SocketIOClient client) {
        return ClientSessionMap.get(client);
    }

    public static MyUUID getSessionID(SocketIOClient client) {
        UserSessionInfo info = ClientSessionMap.get(client);
        if (info == null) {
            return null;
        }
        return info.getSessionID();
    }

    /**
     * @param info
     * @param client
     * @throws Exception
     */
    public static void addUserClient(UserSessionInfo info, SocketIOClient client)
            throws Exception {
        long userID = info.getUserID();
        if (userClientMapping.get(userID) == null) {
            userClientMapping.put(userID, new Vector<SocketIOClient>());
        }
        if (userClientMapping.get(userID).contains(client)) {
            return;
        }
//        setUserSessionInfoInChannel(channel, info);
        userClientMapping.get(userID).add(client);

        //store the real-time server info for each online user in cache
        String address = String.format("%s:%s", serverHost, serverPort);
        MemCachedUtil.writeMemCached(userID + "", 0, address);
        LogUtils.logSessionInfo(logger, client,
                "Added to the cache: user {} is on {}", userID, address);
//        logger.info(uuid, "Added to the cache: user {} is on {}", userID, address);
    }


    public static void removeUserClient(SocketIOClient client) throws Exception {
        UserSessionInfo info = getUserSessionInfo(client);
//        if (channel.attr(userSessionKey).get() == null) {
//            return;
//        }
        if (ClientSessionMap.get(client) == null) {
            return;
        }
//        LogUtils.logSessionInfo(logger, channel,
//                "Try to remove user channel from mapping");

        Vector<SocketIOClient> session = userClientMapping.get(info.getUserID());
        if (session == null) {
            LogUtils.logSessionInfo(logger, client,
                    "cannot find user channel in mapping");
            return;
        }

        userClientMapping.get(info.getUserID()).remove(client);
        LogUtils.logSessionInfo(logger, client,
                "channel removed from mapping");
        if (userClientMapping.get(info.getUserID()).isEmpty()) {
            userClientMapping.remove(info.getUserID());
            //delete the real-time node entry for the user
            String address = String.format("%s:%s", serverHost, serverPort);
            MemCachedUtil.deleteFromMemCached(info.getUserID() + "", address);
            LogUtils.logSessionInfo(logger, client,
                    "Removed from cache: user {}", info.getUserID());
        }
    }

    public static void displayUserChannelMapping() {
        logger.info(uuid, "Current User Channel Mapping: {}",
                userClientMapping);
    }

    public static long getOnlineUserNum() {
        return userClientMapping.size();
    }

    public static Collection<SocketIOClient> getClientsByUserID(long userID) {
        if (userClientMapping.get(userID) != null) {
            return userClientMapping.get(userID);
        }
        return null;
    }
}
