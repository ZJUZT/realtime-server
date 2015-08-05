package com.egeio.realtime.websocket.exception;

/**
 * This is the com.egeio.realtime.exception class to identify that something wrong happened when
 * trying to send request and get response from remote server
 *
 * @author rogerlai
 * @date 2014/12/08
 */

public class WebException extends Exception {
    private static final long serialVersionUID = 4103322407080930513L;

    public WebException(String message) {
        super(message);
    }

    public WebException(String message, Throwable cause) {
        super(message, cause);
    }
}
