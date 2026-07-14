package com.gray.anime.gateway.config;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class ClientAddressResolver {
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private final int trustedProxyHops;

    ClientAddressResolver(int trustedProxyHops) {
        if (trustedProxyHops < 0) {
            throw new IllegalArgumentException("trustedProxyHops must not be negative");
        }
        this.trustedProxyHops = trustedProxyHops;
    }

    String resolve(ServerWebExchange exchange) {
        if (trustedProxyHops > 0) {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
            String forwardedAddress = trustedForwardedAddress(forwardedFor);
            if (forwardedAddress != null) {
                return forwardedAddress;
            }
        }
        return remoteAddress(exchange.getRequest().getRemoteAddress());
    }

    private String trustedForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        List<String> addresses = Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (addresses.size() < trustedProxyHops) {
            return null;
        }
        String candidate = addresses.get(addresses.size() - trustedProxyHops);
        return isIpLiteral(candidate) ? candidate.toLowerCase() : null;
    }

    private boolean isIpLiteral(String value) {
        if (IPV4.matcher(value).matches()) {
            String[] octets = value.split("\\.");
            return Arrays.stream(octets).allMatch(octet -> Integer.parseInt(octet) <= 255);
        }
        return value.contains(":") && IPV6.matcher(value).matches();
    }

    private String remoteAddress(InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        if (address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return address.getHostString();
    }
}
