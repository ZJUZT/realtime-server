package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.egeio.core.config.Config;
import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;

/**
 * Created by think on 2015/9/18.
 * Use socket.io to replace websocket for more compatible
 */
public class SocketServer {
    private final int port;
    private static Logger logger = LoggerFactory
            .getLogger(SocketServer.class);
    private static MyUUID uuid = new MyUUID();
    private final static String serverHost = Config.getConfig()
            .getElement("/configuration/ip_address").getText();
    private final static String socketIOPort = Config.getConfig()
            .getElement("/configuration/socket_io_port").getText();
    private final static String HttpRequestPort = Config.getConfig()
            .getElement("/configuration/http_request_port").getText();

    public SocketServer(int port) {
        this.port = port;
    }

    public void run() {
        Configuration config = new Configuration();
        config.setHostname(serverHost);
        config.setPort(port);

        final SocketIOServer server = new SocketIOServer(config);
        server.addListeners(new SocketIOHandler());

        server.start();

    }

    public static void main(String[] args) {
        new SocketServer(Integer.valueOf(socketIOPort)).run();
        new HttpServer(Integer.valueOf(HttpRequestPort)).run();
    }
}
