package com.egeio.realtime.websocket;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.realtime.websocket.model.UserSessionInfo;
import com.egeio.realtime.websocket.utils.LogUtils;
import com.egeio.realtime.websocket.utils.MemCachedUtil;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by think on 2015/7/31.
 * This class manages all the information for each user channel
 */
public class ChannelManager {
    private static Logger logger = LoggerFactory
            .getLogger(ChannelManager.class);
    private static MyUUID uuid = new MyUUID();

    private static final AttributeKey<UserSessionInfo> userSessionKey = AttributeKey
            .valueOf("userSessionInfo");
    private static ConcurrentHashMap<Long, ConcurrentHashMap<String, Channel>> userChannelMapping = new ConcurrentHashMap<>();

    //configuration the ip address and port for the server
    //which will be stored into memory cache
    private final static String serverHost = Config.getConfig()
            .getElement("/configuration/ip_address").getText();
    private final static long serverPort = Config
            .getNumber("/configuration/websocket_port", 8080);



    private static synchronized void setUserSessionInfoInChannel(
            Channel channel, UserSessionInfo info) {
        channel.attr(userSessionKey).set(info);
    }

    private static UserSessionInfo getUserSessionInfoFromChannel(
            Channel channel) {
        return channel.attr(userSessionKey).get();
    }

    public static MyUUID getUserSessionIDFromChannel(Channel channel) {
        UserSessionInfo info = channel.attr(userSessionKey).get();
        if (info == null) {
            return null;
        }
        return info.getSessionID();
    }


    /**
     * add user-channel into the mapping
     *
     * @param info
     * @param channel
     */
    public static void addUserChannel(UserSessionInfo info, Channel channel)
            throws Exception {
        long userID = info.getUserID();
        String deviceID = info.getDeviceID();
        if (userChannelMapping.get(userID) == null) {
            ConcurrentHashMap<String, Channel> map = new ConcurrentHashMap<>();
            userChannelMapping.put(userID, map);
        }
        setUserSessionInfoInChannel(channel, info);
        userChannelMapping.get(userID).put(deviceID, channel);

        //stored the real-time server info for each online user in cache

        //        memClient.set(userID + "", 0, serverHost + ":" + serverPort);
        MemCachedUtil.writeMemCached(userID + "", 0,
                serverHost + ":" + serverPort);
        //TODO
        LogUtils.logSessionInfo(logger, channel,
                "Added to the cache: user {} is on {}", userID,
                serverHost + ":" + serverPort);
    }

    /**
     * remover user-channel from mapping
     *
     * @param channel
     */
    public static void removeUserChannel(Channel channel) throws Exception {
        UserSessionInfo info = getUserSessionInfoFromChannel(channel);
        if (channel.attr(userSessionKey).get() == null) {
            return;
        }
        //        if (userChannelMapping.size() == 0 && info==null) {
        //            return;
        //        }
        LogUtils.logSessionInfo(logger, channel,
                "Try to remove user channel from mapping");
        ConcurrentHashMap<String, Channel> session = userChannelMapping
                .get(info.getUserID());
        LogUtils.logSessionInfo(logger, channel, "Current Map {}.",
                userChannelMapping);
        if (session == null) {
            LogUtils.logSessionInfo(logger, channel,
                    "cannot find user channel in mapping");
            return;
        }
        LogUtils.logSessionInfo(logger, channel,
                "channel removed from mapping");
        userChannelMapping.get(info.getUserID()).remove(info.getDeviceID());
        if (userChannelMapping.get(info.getUserID()).isEmpty()) {
            userChannelMapping.remove(info.getUserID());
            //delete the real-time node entry for the user
            //            memClient.delete(info.getUserID() + "");
            MemCachedUtil.deleteFromMemCached(info.getUserID() + "", 0,
                    serverHost + ":" + serverPort);
            LogUtils.logSessionInfo(logger, channel,
                    "Removed from cache: user {}", info.getUserID());
        }
    }

    public static void displayUserChannelMapping() {
        logger.info(uuid, "Current User Channel Mapping: {}",
                userChannelMapping);
    }

    public static long getOnlineUserNum() {
        return userChannelMapping.size();
    }

    public static Collection<Channel> getChannelByUserID(long userID) {
        if (userChannelMapping.get(userID) != null) {
            return userChannelMapping.get(userID).values();
        }
        return null;
    }
}
