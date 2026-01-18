package com.nefodov.oneline.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR_HEADER);
        if (header != null && !header.isBlank()) {
            int comma = header.indexOf(',');
            return (comma >= 0 ? header.substring(0, comma) : header).trim();
        }
        return request.getRemoteAddr();
    }
}
