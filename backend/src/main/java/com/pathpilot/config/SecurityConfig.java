package com.pathpilot.config;

import com.pathpilot.ai.AiRateLimitFilter;
import com.pathpilot.common.ApiErrorResponse;
import com.pathpilot.security.AuthRateLimitFilter;
import com.pathpilot.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties appProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final AiRateLimitFilter aiRateLimitFilter;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SecurityConfig(
            AppProperties appProperties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthRateLimitFilter authRateLimitFilter,
            AiRateLimitFilter aiRateLimitFilter,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.appProperties = appProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.aiRateLimitFilter = aiRateLimitFilter;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isLocalProfile = environment.acceptsProfiles(Profiles.of("local"));

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiErrorResponse body = new ApiErrorResponse(
                                    Instant.now(),
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "UNAUTHORIZED",
                                    "Authentication required.",
                                    Map.of()
                            );
                            objectMapper.writeValue(response.getOutputStream(), body);
                        })
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health").permitAll();
                    if (isLocalProfile) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(aiRateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
