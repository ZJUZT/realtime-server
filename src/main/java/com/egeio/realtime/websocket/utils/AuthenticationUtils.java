package com.egeio.realtime.websocket.utils;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import com.egeio.core.utils.GsonUtils;
import com.egeio.realtime.websocket.exception.NeedAuthException;
import com.egeio.realtime.websocket.exception.WebException;
import com.egeio.realtime.websocket.model.UserInfo;
import com.google.gson.JsonObject;
import org.dom4j.Element;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * This is the utility class for authentication with web server
 *
 * @author rogerlai
 * @date 2014/12/08
 */

public class AuthenticationUtils {
    private static Logger logger = LoggerFactory
            .getLogger(AuthenticationUtils.class);
    private static MyUUID uuid = new MyUUID();

    private static WebClient webClient;
    private static String PROTOCOL;
    private static String HOST;
    private static String API;

    static {
        Element protocol = Config.getConfig()
                .getElement("/configuration/auth_endpoint/protocol");

        Element host = Config.getConfig()
                .getElement("/configuration/auth_endpoint/host");

        Element api = Config.getConfig()
                .getElement("/configuration/auth_endpoint/api");

        if (protocol == null || host == null || api == null) {
            logger.error(uuid,
                    "fatal error, no valid auth point set in configuration");
            System.exit(-1);
        }

        PROTOCOL = protocol.getTextTrim();
        HOST = host.getTextTrim();
        API = api.getTextTrim();

        try {
            webClient = new WebClient();
        }
        catch (GeneralSecurityException | IOException e) {
            logger.error(uuid, e, "failed to create the web client");
            System.exit(-1);
        }
    }

    public static UserInfo getUserInfoFromToken(String token)
            throws Exception {
        UserInfo userInfo = null;
        String url = String.format("%s://%s%s", PROTOCOL, HOST, API);

        try {
            String response = webClient.doGet(url, token);
//            logger.info(uuid,"url:{}",url);
            logger.info(uuid, "get response from authentication server: {}",
                    response);
            if (response == null) {
                throw new Exception("failed to get response");
            }

            JsonObject jsonResponse = GsonUtils.getGson()
                    .fromJson(response, JsonObject.class);

            if (jsonResponse == null) {
                throw new Exception("failed to get correct response");
            }

            if (jsonResponse.get("status_code") != null
                    && jsonResponse.get("status_code").getAsInt() != 0) {
                throw new NeedAuthException();
            }

            // parse the JSON to get user information
            long userId = jsonResponse.get("id").getAsInt();
            String userName = jsonResponse.get("name").getAsString();

            userInfo = new UserInfo(userId, userName);
        }
        catch (WebException e) {
            logger.error(uuid, e, "failed to get user info from token");
            throw new NeedAuthException();
        }
        return userInfo;
    }
}
