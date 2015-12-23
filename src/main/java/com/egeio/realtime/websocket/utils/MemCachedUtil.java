package com.egeio.realtime.websocket.utils;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.ChannelManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * This class provides interface for writing data and deleting data from MemCached
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
     * Considering one user may have different devices and possesses different channels
     * the channels may be connected to different real-time server node
     * so we have to store all the distinct node address for each user
     *
     * @param userID              user id
     * @param realTimeNodeAddress real-time server address
     */
    public static void writeMemCached(String userID, int expireTime,
            String realTimeNodeAddress) throws Exception {
        Gson gson = GsonUtils.getGson();
        Set<String> addresses;
        //make sure key exists
        CASValue casValue = memClient.gets(userID);
        if (casValue == null) {
            addresses = new HashSet<>();
            memClient.set(userID, expireTime, gson.toJson(addresses));
            casValue = memClient.gets(userID);
        }

        String jsonObj = gson.toJson(casValue.getValue());
        addresses = gson.fromJson(
                jsonObj.substring(1, jsonObj.length() - 1).replace("\\", ""),
                new TypeToken<Set<String>>() {
                }.getType());

        addresses.add(realTimeNodeAddress);
        CASResponse casResponse = memClient
                .cas(userID, casValue.getCas(), gson.toJson(addresses));
        if (casResponse == CASResponse.OK) {
            return;
        }
        Thread.sleep(50);
        writeMemCached(userID, expireTime, realTimeNodeAddress);
    }

    /**
     * when no active channels on this server, remove server address from memCached
     *
     * @param userID              user id
     * @param realTimeNodeAddress real-time server address
     * @throws Exception
     */
    public static void deleteFromMemCached(String userID,
            String realTimeNodeAddress) throws Exception {
        if (ChannelManager.getClientsByUserID(Long.valueOf(userID)) != null) {
            //still has active channels, no need deleting node node address from cache
            return;
        }

        CASValue casValue = memClient.gets(userID);

        if (casValue == null) {
            logger.info(uuid,
                    "Can't find real-time server in cache for user:{}", userID);
            return;
        }

        Gson gson = GsonUtils.getGson();
        String jsonObj = gson.toJson(casValue.getValue());

        //delete "\" and quotation marks embracing the json object
        Set<String> addresses = gson.fromJson(
                jsonObj.substring(1, jsonObj.length() - 1).replace("\\", ""),
                new TypeToken<Set<String>>() {
                }.getType());
        addresses.remove(realTimeNodeAddress);
        if (addresses.isEmpty()) {
            Future<Boolean> future = memClient.delete(userID);
            if (future.get()) {
                return;
            }
        }
        else {
            CASResponse casResponse = memClient
                    .cas(userID, casValue.getCas(), gson.toJson(addresses));
            if (casResponse == CASResponse.OK) {
                return;
            }
        }

        Thread.sleep(50);
        deleteFromMemCached(userID, realTimeNodeAddress);
    }
}
