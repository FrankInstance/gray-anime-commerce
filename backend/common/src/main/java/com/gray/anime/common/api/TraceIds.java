package com.gray.anime.common.api;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class TraceIds {
    public static final String HEADER = "X-Trace-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {
    }

    public static String newTraceId() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String current() {
        String traceId = CURRENT.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = newTraceId();
            CURRENT.set(traceId);
        }
        return traceId;
    }

    public static void set(String traceId) {
        CURRENT.set(traceId == null || traceId.isBlank() ? newTraceId() : traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
