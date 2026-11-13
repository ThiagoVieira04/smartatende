package br.com.smartatende.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTests {

    private final AtomicLong agora = new AtomicLong(1_000);

    private final FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(Duration.ofSeconds(60), agora::get);

    private final RateLimitFilter filter = new RateLimitFilter(
            limiter,
            new JsonMapper(),
            true,
            2,
            2);

    @Test
    void bloqueiaAposLimiteEEnviaHeaders() throws Exception {
        MockHttpServletResponse primeira =
                executar(filter, "POST", "/api/auth/login");

        assertEquals(200, primeira.getStatus());
        assertEquals("2", primeira.getHeader(RateLimitFilter.HEADER_LIMIT));
        assertEquals("1", primeira.getHeader(RateLimitFilter.HEADER_REMAINING));
        assertNull(primeira.getHeader(RateLimitFilter.HEADER_RETRY_AFTER));

        MockHttpServletResponse segunda =
                executar(filter, "POST", "/api/auth/login");

        assertEquals(200, segunda.getStatus());
        assertEquals("0", segunda.getHeader(RateLimitFilter.HEADER_REMAINING));

        MockHttpServletResponse bloqueada =
                executar(filter, "POST", "/api/auth/login");

        assertEquals(429, bloqueada.getStatus());
        assertEquals("2", bloqueada.getHeader(RateLimitFilter.HEADER_LIMIT));
        assertEquals("0", bloqueada.getHeader(RateLimitFilter.HEADER_REMAINING));
        assertEquals("60", bloqueada.getHeader(RateLimitFilter.HEADER_RETRY_AFTER));
        assertTrue(bloqueada.getContentAsString()
                .contains("Muitas requisi\u00e7\u00f5es"));
    }

    @Test
    void limitesDeAuthEChatSaoIndependentes() throws Exception {
        executar(filter, "POST", "/api/auth/login");
        executar(filter, "POST", "/api/auth/login");

        MockHttpServletResponse authBloqueado =
                executar(filter, "POST", "/api/auth/login");
        MockHttpServletResponse chatPermitido =
                executar(filter, "POST", "/api/chat");

        assertEquals(429, authBloqueado.getStatus());
        assertEquals(200, chatPermitido.getStatus());
        assertEquals("1", chatPermitido.getHeader(
                RateLimitFilter.HEADER_REMAINING));
    }

    @Test
    void requisicoesGetNaoSaoLimitadas() throws Exception {
        MockHttpServletResponse response =
                executar(filter, "GET", "/api/auth/login");

        assertEquals(200, response.getStatus());
        assertNull(response.getHeader(RateLimitFilter.HEADER_LIMIT));
    }

    @Test
    void naoAplicaQuandoDesabilitado() throws Exception {
        RateLimitFilter desabilitado = new RateLimitFilter(
                limiter,
                new JsonMapper(),
                false,
                1,
                1);

        MockHttpServletResponse response =
                executar(desabilitado, "POST", "/api/auth/login");

        assertEquals(200, response.getStatus());
        assertNull(response.getHeader(RateLimitFilter.HEADER_LIMIT));
    }

    private MockHttpServletResponse executar(
            RateLimitFilter rateLimitFilter,
            String method,
            String path) throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilter(
                request,
                response,
                new MockFilterChain());

        return response;
    }
}
