package com.higgsblock.global.browser.app.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @author yangshenghong
 * @date 2018-05-24
 */
@Slf4j
public class UrlUtils {

    public static final String GET_BLOCK = "/v1.0.0/blocks/getBlocks";

    public static final String SEND_TRANSACTION = "/v1.0.0/transactions/send";

    private static final int MAX_PORT = 65535;

    public static boolean ipPortCheckout(String ip, Integer port) {
        if (StringUtils.isEmpty(ip) || port == null) {
            LOGGER.error("ip or port is empty,ip={},port={}", ip, port);
            return false;
        }
        String regex = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
        boolean flag = ip.matches(regex);
        if (flag) {
            if (port > 0 && port <= MAX_PORT) {
                return true;
            }
        }
        return false;
    }

    public static String builderUrl(String ip, Integer port, String address) {
        if (port == null || port.intValue() == 0) {
            port = 80;
        }
        return new StringBuffer().append(ip).append(":").append(port).append(address).toString();
    }
}