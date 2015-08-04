package com.egeio.realtime.websocket.utils;

import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.dom4j.Element;

/**
 * This is an utility class for network related tasks such as getting the
 * external IP address of this server
 * 
 * @author rogerlai
 * @date 2014/12/08
 */

public class NetworkUtils {
    private static Logger logger = LoggerFactory.getLogger(NetworkUtils.class);
    private static MyUUID uuid = new MyUUID();

    private static String externalIpAddress;

    public static String getExternalIpAddress() {
        if (externalIpAddress == null) {
            externalIpAddress = getExternalIpAddressFromConfig();
        }

        return externalIpAddress;
    }

    private static String getExternalIpAddressFromConfig() {
        String address = "";
        Element ipElement = Config.getConfig().getElement(
                "/configuration/ip_address");

        if (ipElement != null) {
            address = ipElement.getTextTrim();

            // check if valid address
            if (!InetAddressValidator.getInstance().isValid(address)) {
                logger.error(uuid,
                        "invalid ip_address set in configuration, exit");
                System.exit(-1);
            }
        }
        else {
            logger.error(uuid, "no ip_address set in configuration, exit");
            System.exit(-1);
        }

        return address;
    }
}
