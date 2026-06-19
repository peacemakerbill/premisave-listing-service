package com.premisave.listing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    /**
     * Extracts the role from the JWT.
     * The auth service stores role as a single String under the "roles" claim,
     * e.g. "roles": "ADMIN"
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("roles", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            final Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        } catch (Exception e) {
            log.error("Failed to extract claim: {}", e.getMessage());
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("JWT parsing failed: {}", e.getMessage());
            throw e;
        }
    }

    private Key getSignInKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);

            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
                keyBytes = padded;
            } else if (keyBytes.length > 32) {
                byte[] truncated = new byte[32];
                System.arraycopy(keyBytes, 0, truncated, 0, 32);
                keyBytes = truncated;
            }

            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Failed to create signing key", e);
            return Keys.hmacShaKeyFor(secret.getBytes());
        }
    }

    public boolean isTokenValid(String token) {
        try {
            String username = extractUsername(token);
            if (username == null) return false;
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}