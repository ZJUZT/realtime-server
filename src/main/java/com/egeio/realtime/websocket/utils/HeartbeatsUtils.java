package com.egeio.realtime.websocket.utils;

import com.egeio.core.config.Config;

/**
 * Created by think on 2015/7/31.
 * This class is responsible for heartbeats
 */
public class HeartbeatsUtils {
    private static int MAX_READER_IDLE_TIME_IN_SECONDS = 120;
    private static int MAX_WRITER_IDLE_TIME_IN_SECONDS = 60;
    private static int MAX_ALL_IDLE_TIME_IN_SECONDS = 120;

    static {
        MAX_READER_IDLE_TIME_IN_SECONDS = Config
                .getNumber("/configuration/heartbeat/reader_idle_time",
                        MAX_READER_IDLE_TIME_IN_SECONDS);
        MAX_WRITER_IDLE_TIME_IN_SECONDS = Config.getNumber(
                "/configuration/heartbeat/writer_idle_time",
                MAX_WRITER_IDLE_TIME_IN_SECONDS);
        MAX_ALL_IDLE_TIME_IN_SECONDS = Config.getNumber(
                "/configuration/heartbeat/all_idle_time",
                MAX_ALL_IDLE_TIME_IN_SECONDS);
    }

    public static int getMaxReaderIdleTime() {
        return MAX_READER_IDLE_TIME_IN_SECONDS;
    }

    public static int getMaxWriterIdleTime() {
        return MAX_WRITER_IDLE_TIME_IN_SECONDS;
    }

    public static int getMaxAllIdleTime() {
        return MAX_ALL_IDLE_TIME_IN_SECONDS;
    }
}
