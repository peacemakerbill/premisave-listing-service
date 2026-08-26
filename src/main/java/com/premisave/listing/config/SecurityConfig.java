package com.premisave.listing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.premisave.listing.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Called when a request has no valid JWT at all (not authenticated).
     * Returns 401 Unauthorized.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Authentication required. Please provide a valid Bearer token.",
                    "path", request.getRequestURI(),
                    "timestamp", LocalDateTime.now().toString()
            )));
        };
    }

    /**
     * Called when a valid JWT exists but the user's role is not allowed.
     * Returns 403 Forbidden.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status", 403,
                    "error", "Forbidden",
                    "message", "You do not have permission to access this resource.",
                    "path", request.getRequestURI(),
                    "timestamp", LocalDateTime.now().toString()
            )));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                // NOTE: SystemController actually maps to /system/health and
                // /system/health/details (it's @RequestMapping("/system")) —
                // the previous permitAll list had a bare "/health" entry that
                // never matched the real path, meaning health checks were
                // silently requiring a JWT. Fixed below.
                .requestMatchers(
                        "/public/**",
                        "/system/health",
                        "/system/health/details",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).permitAll()

                .requestMatchers(HttpMethod.GET, "/listings/me").authenticated()
                .requestMatchers(HttpMethod.GET,
                        "/listings/search",
                        "/listings/short-term",
                        "/listings/owner/**",
                        "/listings/{id}"
                ).permitAll()
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "FINANCE")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}