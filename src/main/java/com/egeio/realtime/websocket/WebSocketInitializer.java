package com.egeio.realtime.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * Created by think on 2015/7/31.
 * This class is responsible for initializing netty channel
 */
public class WebSocketInitializer extends ChannelInitializer<SocketChannel> {
    private int port;

    public WebSocketInitializer(int port) {
        this.port = port;
    }

    @Override protected void initChannel(SocketChannel socketChannel)
            throws Exception {
        socketChannel.pipeline().addLast(new HttpRequestDecoder());
        socketChannel.pipeline().addLast(new HttpResponseEncoder());
        socketChannel.pipeline().addLast(new HttpObjectAggregator(65536));
        socketChannel.pipeline().addLast(new WebSocketHandler(port));
    }
}
