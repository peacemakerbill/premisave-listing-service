package com.premisave.listing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            boolean isValid = jwtService.isTokenValid(jwt);

            if (isValid) {
                String userId = jwtService.extractUserId(jwt);
                String role = jwtService.extractRole(jwt); // e.g. "ADMIN", "FINANCE", "CLIENT"

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // Spring Security requires authorities prefixed with "ROLE_"
                    // so hasRole('ADMIN') matches "ROLE_ADMIN"
                    List<GrantedAuthority> authorities = (role != null)
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : Collections.emptyList();

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userId, jwt, authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.info("Authenticated userId: {} with role: {}", userId, role);
                }
            }
        } catch (Exception e) {
            log.error("Error processing JWT token", e);
        }

        filterChain.doFilter(request, response);
    }
}