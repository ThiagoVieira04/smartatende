package br.com.smartatende.config;

import br.com.smartatende.dto.ErrorResponse;
import br.com.smartatende.security.JwtAuthenticationFilter;
import br.com.smartatende.security.RateLimitFilter;
import br.com.smartatende.security.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP_POLICY = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self'; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestIdFilter requestIdFilter;
    private final ObjectMapper objectMapper;
    private final boolean metricsPublic;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RequestIdFilter requestIdFilter,
            ObjectMapper objectMapper,
            @Value("${app.security.metrics-public:true}") boolean metricsPublic) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.requestIdFilter = requestIdFilter;
        this.objectMapper = objectMapper;
        this.metricsPublic = metricsPublic;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
            RequestIdFilter filter) {
        return disabled(filter);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(CSP_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/",
                            "/index.html",
                            "/style.css",
                            "/app.js",
                            "/favicon.ico",
                            "/error",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/v3/api-docs/**")
                            .permitAll();

                    if (metricsPublic) {
                        auth.requestMatchers(
                                "/actuator/metrics",
                                "/actuator/metrics/**")
                                .permitAll();
                    } else {
                        auth.requestMatchers(
                                "/actuator/metrics",
                                "/actuator/metrics/**")
                                .authenticated();
                    }

                    auth.requestMatchers(
                            "/api/auth/login",
                            "/api/auth/register",
                            "/api/auth/refresh")
                            .permitAll();

                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        unauthorized(request, response)))
                .addFilterBefore(
                        rateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        requestIdFilter,
                        RateLimitFilter.class)
                .addFilterAfter(
                        jwtAuthenticationFilter,
                        RateLimitFilter.class)
                .build();
    }

    private void unauthorized(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Autenticação necessária.",
                request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
