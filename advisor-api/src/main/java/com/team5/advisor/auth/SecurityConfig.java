package com.team5.advisor.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Mirrors trade-api's SecurityConfig: stateless bearer-token auth with the shared HMAC secret. */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String UNAUTHORISED_BODY = "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(UNAUTHORISED_BODY.getBytes(StandardCharsets.UTF_8));
        };

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {
            })
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(entryPoint)
            )
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        MacAlgorithm algorithm = MacAlgorithm.from(properties.algorithm());
        if (algorithm == null) {
            throw new IllegalStateException(
                "auth.jwt.algorithm must be one of HS256, HS384, HS512 but was '" + properties.algorithm() + "'");
        }
        byte[] secret = requireStrongSecret(properties.sharedSecret(), algorithm);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(secret, jcaName(algorithm)))
            .macAlgorithm(algorithm)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.clockSkew()),
            new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
            new JwtClaimValidator<Object>(AccountClaims.ACCOUNT_ID, AccountClaims::isValidAccountId),
            new JwtClaimValidator<String>(JwtClaimNames.ISS, issuer -> Objects.equals(issuer, properties.issuer()))
        ));
        return decoder;
    }

    private static byte[] requireStrongSecret(String sharedSecret, MacAlgorithm algorithm) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException("auth.jwt.shared-secret (JWT_SECRET) must be set");
        }
        byte[] secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        int minimumBytes = switch (algorithm) {
            case HS256 -> 32;
            case HS384 -> 48;
            case HS512 -> 64;
        };
        if (secret.length < minimumBytes) {
            throw new IllegalStateException("auth.jwt.shared-secret (JWT_SECRET) must be at least "
                + minimumBytes + " bytes for " + algorithm.getName() + " but was " + secret.length);
        }
        return secret;
    }

    private static String jcaName(MacAlgorithm algorithm) {
        return switch (algorithm) {
            case HS256 -> "HmacSHA256";
            case HS384 -> "HmacSHA384";
            case HS512 -> "HmacSHA512";
        };
    }
}
