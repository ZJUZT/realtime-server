package com.egeio.realtime.websocket;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.egeio.core.config.Config;

/**
 * Created by think on 2015/9/18.
 * Use socket.io to replace webSocket for more compatible
 *
 * SocketIO Server: (default port: 8080
 * maintain long connection
 *
 * Http server: (default port:8081)
 * handle http request from event processor
 */
public class SocketServer {

    private final int port;
    private final static String serverHost = Config.getConfig()
            .getElement("/configuration/ip_address").getText();
    private final static int socketIOPort = Config
            .getNumber("/configuration/socket_io_port", 8080);
    private final static int HttpRequestPort = Config
            .getNumber("/configuration/http_request_port", 8081);

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
        new SocketServer(socketIOPort).run();
        new HttpServer(HttpRequestPort).run();
    }
}
