package com.finotech.jewellery.shared.config;

import com.finotech.jewellery.shared.exception.ApiError;
import com.finotech.jewellery.shared.exception.ErrorCode;
import com.finotech.jewellery.shared.security.AuthenticatedCustomer;
import com.finotech.jewellery.shared.security.BranchContextFilter;
import com.finotech.jewellery.shared.security.JwtAuthenticationFilter;
import com.finotech.jewellery.shared.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security. Authorization is enforced per endpoint with
 * {@code @PreAuthorize} against permission authorities.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] publicPaths = securityProperties.publicPaths().toArray(String[]::new);

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        // The customer app: its own principal, and nothing else.
                        .requestMatchers("/api/v1/storefront/**")
                                .hasAuthority(AuthenticatedCustomer.AUTHORITY)
                        // Everything else is staff-only. A customer token is a
                        // valid signature but not a user, so it is refused here
                        // rather than left to each endpoint's @PreAuthorize.
                        .anyRequest().access((authentication, context) -> {
                            Authentication current = authentication.get();
                            return new AuthorizationDecision(current != null
                                    && current.isAuthenticated()
                                    && !(current instanceof AnonymousAuthenticationToken)
                                    && !(current.getPrincipal() instanceof AuthenticatedCustomer));
                        }))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                write(response, ErrorCode.UNAUTHORIZED, "Authentication required",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, deniedException) ->
                                write(response, ErrorCode.FORBIDDEN,
                                        "You do not have permission to perform this action",
                                        request.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Branch header scoping needs the principal, so it follows JWT auth.
                .addFilterAfter(new BranchContextFilter(objectMapper), JwtAuthenticationFilter.class);

        return http.build();
    }

    private void write(jakarta.servlet.http.HttpServletResponse response, ErrorCode code,
                       String message, String path) throws java.io.IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(code, message, path, CorrelationIdFilter.current()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
