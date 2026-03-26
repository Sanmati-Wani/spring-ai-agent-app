package com.prismx.ai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentApiInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-Agent-Api-Key";

    private final AgentPlatformProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AgentApiInterceptor(AgentPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/api/agent")) {
            return true;
        }

        String configuredApiKey = properties.getApiKey();
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            String providedKey = request.getHeader(API_KEY_HEADER);
            if (!configuredApiKey.equals(providedKey)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Invalid API key");
                return false;
            }
        }

        String clientKey = request.getRemoteAddr();
        long minute = Instant.now().getEpochSecond() / 60;
        counters.compute(clientKey, (key, current) -> {
            if (current == null || current.minute != minute) {
                return new WindowCounter(minute, 1);
            }
            return new WindowCounter(current.minute, current.count + 1);
        });
        int limit = Math.max(properties.getRateLimitPerMinute(), 1);
        if (counters.get(clientKey).count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded");
            return false;
        }
        return true;
    }

    private record WindowCounter(long minute, int count) {
    }
}
