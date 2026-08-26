package com.premisave.listing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
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
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("JWT parsing failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Mirrors auth-service key derivation exactly: truncate/pad to 32 bytes
     * so tokens are cross-verifiable.
     *
     * Previously, if decoding/padding failed here, this silently fell back
     * to signing with the *raw, unpadded* secret bytes — a different key
     * derivation than the padded/truncated one used on the success path —
     * while only logging an error rather than surfacing the failure. That
     * meant a misconfigured secret could silently verify tokens against the
     * wrong key instead of failing loudly. It now propagates the failure
     * (caught by extractAllClaims/extractClaim above, same as any other
     * malformed-token case): a bad secret is a startup/config problem that
     * should be visible, not papered over per-request.
     */
    private SecretKey getSignInKey() {
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