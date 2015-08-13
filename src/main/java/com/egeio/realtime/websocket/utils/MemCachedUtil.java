package com.egeio.realtime.websocket.utils;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.ChannelManager;
import com.google.gson.reflect.TypeToken;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class provides interface for writing data and deleting data from memory cached
 * Created by think on 2015/8/4.
 */
public class MemCachedUtil {
    private static Logger logger = LoggerFactory.getLogger(MemCachedUtil.class);
    private static MyUUID uuid = new MyUUID();
    private static MemcachedClient memClient;

    private final static String memHost = Config.getConfig()
            .getElement("/configuration/memcached/host").getText();
    private final static long memPort = Config
            .getNumber("/configuration/memcached/port", 11211);

    //for MemCached Client
    static {
        try {
            memClient = new MemcachedClient(
                    AddrUtil.getAddresses(memHost + ":" + memPort));
        }
        catch (IOException e) {
            logger.error(uuid, "Init MemCached Client failed");
        }
    }

    /**
     * Considering one user may have different device and possesses different channels
     * the channels may be connected to different real-time server node
     * so we have to store all the distinct node address for each user
     *
     * @param userID              user id
     * @param realTimeNodeAddress real-time server address
     */
    public synchronized static void writeMemCached(String userID,
            int expireTime, String realTimeNodeAddress) throws Exception {
        Set<String> addresses;
        if (memClient.get(userID) == null) {
            addresses = new HashSet<>();
        }
        else {
            String jsonObj = GsonUtils.getGson().toJson(memClient.get(userID));
            logger.info(uuid, "jsonObj:{}", jsonObj);
            addresses = GsonUtils.getGson().fromJson(
                    jsonObj.substring(1, jsonObj.length() - 1)
                            .replace("\\", ""), new TypeToken<Set<String>>() {
                    }.getType());
        }
        addresses.add(realTimeNodeAddress);
        memClient.set(userID, expireTime,
                GsonUtils.getGson().toJson(addresses));
    }

    /**
     * when no active channels on this server, remove server address from memCached
     *
     * @param userID              user id
     * @param expireTime          0 for stored forever
     * @param realTimeNodeAddress real-time server address
     * @throws Exception
     */
    public synchronized static void deleteFromMemCached(String userID,
            int expireTime, String realTimeNodeAddress) throws Exception {
        if (ChannelManager.getChannelByUserID(Long.valueOf(userID)) != null) {
            //still has active channels, no need deleting node node address from cache
            return;
        }

        if (memClient.get(userID) == null) {
            logger.info(uuid, "Can't find real-time server in cache for user:{}", userID);
            return;
        }
        else {
            String jsonObj = GsonUtils.getGson().toJson(memClient.get(userID));
            //delete "\" and quotation marks embracing the json object
            Set<String> addresses = GsonUtils.getGson().fromJson(
                    jsonObj.substring(1, jsonObj.length() - 1)
                            .replace("\\", ""), new TypeToken<Set<String>>() {
                    }.getType());
            addresses.remove(realTimeNodeAddress);
            if (addresses.isEmpty()) {
                memClient.delete(userID);
            }
            else {
                memClient.set(userID, expireTime,
                        GsonUtils.getGson().toJson(addresses));
            }
        }
    }
}
