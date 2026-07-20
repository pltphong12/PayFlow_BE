package com.payflow.common.jwt;

import com.payflow.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(JwtClaimsConstants.USER_ID, userId.toString())
                .claim(JwtClaimsConstants.ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    public JwtTokenPayload parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.get(JwtClaimsConstants.USER_ID, String.class));
            String role = claims.get(JwtClaimsConstants.ROLE, String.class);
            return new JwtTokenPayload(userId, role);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Access token expired");
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
    }
}
