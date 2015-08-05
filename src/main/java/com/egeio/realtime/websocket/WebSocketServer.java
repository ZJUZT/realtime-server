package com.egeio.realtime.websocket;

import com.egeio.core.log.Logger;
import com.egeio.core.log.LoggerFactory;
import com.egeio.core.log.MyUUID;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Created by think on 2015/8/1.
 * Main function entrance, set up the webSocket server
 * bind the server to the specific port
 */
public class WebSocketServer {
    private final int port;
    private static Logger logger = LoggerFactory
            .getLogger(WebSocketServer.class);
    private static MyUUID uuid;

    //leave out ssl temporarily
    public WebSocketServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketInitializer(port))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture f = b.bind(port).sync();
            logger.info(uuid, "WebSocket server started at port {}", port);
            f.channel().closeFuture().sync();
        }
        finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt((args[0]));
        }
        else {
            port = 8080;
        }
        try {
            new WebSocketServer(port).run();
        }
        catch (Exception e) {
            //            e.printStackTrace();
            logger.error(uuid, e, "failed to start the realTime server");
        }
    }

}
