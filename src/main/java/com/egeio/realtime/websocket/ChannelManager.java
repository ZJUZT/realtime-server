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

    private static final AttributeKey<UserSessionInfo> userSessionKey = AttributeKey
            .valueOf("userSessionInfo");
    //    private static ConcurrentHashMap<Long, ConcurrentHashMap<String, Channel>> userChannelMapping = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Long, Vector<Channel>> userChannelMapping = new ConcurrentHashMap<>();
    //configuration the ip address and port for the server
    //which will be stored into memory cache
    private final static String serverHost = Config.getConfig()
            .getElement("/configuration/ip_address").getText();
    private final static long serverPort = Config
            .getNumber("/configuration/websocket_port", 8080);

    private static void setUserSessionInfoInChannel(Channel channel,
            UserSessionInfo info) {
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
     * @param info    user session info
     * @param channel user channel
     */
    public static void addUserChannel(UserSessionInfo info, Channel channel)
            throws Exception {
        long userID = info.getUserID();
        if (userChannelMapping.get(userID) == null) {
            userChannelMapping.put(userID, new Vector<Channel>());
        }
        if(userChannelMapping.get(userID).contains(channel)){
            return;
        }
        setUserSessionInfoInChannel(channel, info);
        userChannelMapping.get(userID).add(channel);

        //store the real-time server info for each online user in cache
        String address = String.format("%s:%s", serverHost, serverPort);
        MemCachedUtil.writeMemCached(userID + "", 0, address);
        LogUtils.logSessionInfo(logger, channel,
                "Added to the cache: user {} is on {}", userID, address);
    }

    /**
     * remover user-channel from mapping
     *
     * @param channel user channel
     */
    public static void removeUserChannel(Channel channel) throws Exception {
        UserSessionInfo info = getUserSessionInfoFromChannel(channel);
        if (channel.attr(userSessionKey).get() == null) {
            return;
        }
        LogUtils.logSessionInfo(logger, channel,
                "Try to remove user channel from mapping");
        Vector<Channel> session = userChannelMapping.get(info.getUserID());
        if (session == null) {
            LogUtils.logSessionInfo(logger, channel,
                    "cannot find user channel in mapping");
            return;
        }

        userChannelMapping.get(info.getUserID()).remove(channel);
        LogUtils.logSessionInfo(logger, channel,
                "channel removed from mapping");
        if (userChannelMapping.get(info.getUserID()).isEmpty()) {
            userChannelMapping.remove(info.getUserID());
            //delete the real-time node entry for the user
            String address = String.format("%s:%s", serverHost, serverPort);
            MemCachedUtil.deleteFromMemCached(info.getUserID() + "", address);
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
            return userChannelMapping.get(userID);
        }
        return null;
    }
}
