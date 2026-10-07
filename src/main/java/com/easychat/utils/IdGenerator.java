package com.easychat.utils;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机 ID 生成工具类
 */
public final class IdGenerator {


    /** 起始纪元，2024-01-01 00:00:00 UTC，可用约 69 年 */
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);         // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);          // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 时钟回拨容忍上限（毫秒），超过则抛异常 */
    private static final long MAX_BACKWARD_MS = 5L;

    private static long workerId;
    private static long datacenterId;

    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    public IdGenerator(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 [0, " + MAX_WORKER_ID + "] 之间");
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId 必须在 [0, " + MAX_DATACENTER_ID + "] 之间");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /** 生成下一个 ID，线程安全 */
    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 处理时钟回拨
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_BACKWARD_MS) {
                throw new IllegalStateException("时钟回拨 " + offset + " ms，拒绝生成 ID");
            }
            // 小幅回拨：自旋等待追平
            while (timestamp < lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒序列号用尽，等到下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /** 生成字符串形式的 ID */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    public static Long nextIdLong() {
        return nextId();
    }

    private static long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
