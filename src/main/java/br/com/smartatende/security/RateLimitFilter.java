package br.com.smartatende.security;

import br.com.smartatende.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_SECONDS = "60";

    private final FixedWindowRateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int authLimit;
    private final int chatLimit;

    public RateLimitFilter(
            FixedWindowRateLimiter limiter,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.auth-per-minute:20}") int authLimit,
            @Value("${app.rate-limit.chat-per-minute:30}") int chatLimit) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.authLimit = authLimit;
        this.chatLimit = chatLimit;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String categoria = categoria(request);

        if (categoria != null) {
            int limite = "auth".equals(categoria) ? authLimit : chatLimit;
            String chave = request.getRemoteAddr() + ":" + categoria;

            if (!limiter.tryAcquire(chave, limite)) {
                escreverResposta(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String categoria(HttpServletRequest request) {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        String path = request.getRequestURI();

        if (path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")) {
            return "auth";
        }

        if (path.equals("/api/chat")) {
            return "chat";
        }

        return null;
    }

    private void escreverResposta(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Muitas requisições. Tente novamente em instantes.",
                request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
