package com.gray.anime.common.api;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TraceIds {
    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern ACCEPTED = Pattern.compile("^[a-fA-F0-9]{16,64}$");

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
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    public static void set(String traceId) {
        String resolved = resolve(traceId);
        CURRENT.set(resolved);
        MDC.put(MDC_KEY, resolved);
    }

    public static String resolve(String traceId) {
        if (traceId == null || !ACCEPTED.matcher(traceId).matches()) {
            return newTraceId();
        }
        return traceId.toLowerCase(Locale.ROOT);
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }
}
