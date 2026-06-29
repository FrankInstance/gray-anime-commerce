package com.gray.anime.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TraceIds.set(request.getHeader(TraceIds.HEADER));
        response.setHeader(TraceIds.HEADER, TraceIds.current());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIds.clear();
        }
    }
}
