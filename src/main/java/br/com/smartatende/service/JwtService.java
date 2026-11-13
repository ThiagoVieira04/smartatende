package br.com.smartatende.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_VERSION = "ver";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:900000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String gerarToken(String email, long tokenVersion) {
        return gerar(email, tokenVersion, TYPE_ACCESS, accessExpirationMs);
    }

    public String gerarRefreshToken(String email, long tokenVersion) {
        return gerar(email, tokenVersion, TYPE_REFRESH, refreshExpirationMs);
    }

    public String extrairEmail(String token) {
        return claims(token).getSubject();
    }

    public long extrairTokenVersion(String token) {
        Number version = claims(token).get(CLAIM_VERSION, Number.class);
        return version == null ? 0 : version.longValue();
    }

    public String extrairTipo(String token) {
        String type = claims(token).get(CLAIM_TYPE, String.class);
        return type == null ? TYPE_ACCESS : type;
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extrairTipo(token));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extrairTipo(token));
    }

    private String gerar(
            String email,
            long tokenVersion,
            String type,
            long expirationMs) {

        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(key)
                .compact();
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
