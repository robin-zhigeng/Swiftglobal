package com.higgsblock.global.chain.app.utils;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;

/**
 * Calculate size of an object by its json format.
 *
 * @author chenjiawei
 * @date 2018-03-28
 */
@Slf4j
public class JsonSizeCounter implements ISizeCounter {
    private static volatile JsonSizeCounter singleton;
    private static final Object LOCK = new Object();

    private JsonSizeCounter() {
    }

    /**
     * Get the singleton-designed JsonSizeCounter.
     */
    public static JsonSizeCounter getJsonSizeCounter() {
        if (singleton == null) {
            synchronized (LOCK) {
                if (singleton == null) {
                    singleton = new JsonSizeCounter();
                }
            }
        }

        return singleton;
    }

    @Override
    public long calculateSize(Object o) {
        try {
            return JSON.toJSONString(o).getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            LOGGER.info(e.getMessage(), e);
        }
        return Long.MAX_VALUE;
    }
}
