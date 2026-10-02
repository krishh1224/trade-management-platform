package com.team5.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, ObjectMapper objectMapper)
        throws Exception {
        AuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);

        http
            // Stateless bearer-token API: no cookies or sessions, so CSRF protection does not apply.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {
            })
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Error dispatches only follow a request that was already authorised, or rejected by the firewall.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(token ->
                        new JwtAuthenticationToken(token, List.of(), String.valueOf(token.getClaims().get(AccountClaims.ACCOUNT_ID))))
                )
                .authenticationEntryPoint(entryPoint)
            )
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        MacAlgorithm algorithm = resolveAlgorithm(properties.algorithm());
        byte[] secret = requireStrongSecret(properties.sharedSecret(), algorithm);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(secret, jcaName(algorithm)))
            .macAlgorithm(algorithm)
            .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.clockSkew()),
            // JwtTimestampValidator accepts a token with no exp at all; the contract requires one.
            new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
            new JwtClaimValidator<Object>(AccountClaims.ACCOUNT_ID, AccountClaims::isValidAccountId)
        ));
        return decoder;
    }

    private static MacAlgorithm resolveAlgorithm(String configured) {
        MacAlgorithm algorithm = MacAlgorithm.from(configured);
        if (algorithm == null) {
            throw new IllegalStateException(
                "auth.jwt.algorithm must be one of HS256, HS384, HS512 but was '" + configured + "'");
        }
        return algorithm;
    }

    // RFC 7518 section 3.2: an HMAC key must be at least as long as the hash output.
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
