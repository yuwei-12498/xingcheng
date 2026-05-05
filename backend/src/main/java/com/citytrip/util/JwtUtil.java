package com.citytrip.util;

import com.citytrip.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    private static final int MIN_SECRET_LENGTH = 32;
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;
    private static final long MAX_EXPIRATION_HOURS = 24L * 365L * 10L;

    private final SecretKey secretKey;
    private final long expirationTimeMillis;

    public JwtUtil(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters long");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.trim().getBytes(StandardCharsets.UTF_8));
        long expirationHours = Math.max(jwtProperties.getExpirationHours(), 1);
        long boundedHours = Math.min(expirationHours, MAX_EXPIRATION_HOURS);
        this.expirationTimeMillis = boundedHours * ONE_HOUR_MILLIS;
    }

    public String generateToken(Long userId, Integer role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTimeMillis))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
