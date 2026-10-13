package com.team5.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, StrategyTokenProperties.class})
public class SecurityConfig {

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtDecoder jwtDecoder,
        JwtProperties jwtProperties,
        StrategyTokenProperties strategyTokenProperties,
        ObjectMapper objectMapper
    ) throws Exception {
        AuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);
        JwtIssuerAuthenticationManagerResolver tokenResolver =
            issuerResolver(jwtDecoder, jwtProperties, strategyTokenProperties);

        http
            // Stateless bearer-token API: no cookies or sessions, so CSRF protection does not apply.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // handle the CORS preflight before auth kicks in
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(authorize -> authorize
                // Error dispatches only follow a request that was already authorised, or rejected by the firewall.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                // Swagger UI and the static spec it renders; the API routes it calls still need a token
                .requestMatchers(HttpMethod.GET,
                    "/docs", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/swagger.yaml").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // The one route an automated strategy's delegated token may call. The route's own
                // validation, accountId check and idempotency apply to it exactly as to a customer.
                .requestMatchers(HttpMethod.POST, "/api/v1/orders").authenticated()
                .anyRequest().access(customerTokensOnly())
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                // the token's issuer picks the key and the claim rules it is verified with
                .authenticationManagerResolver(tokenResolver)
                .authenticationEntryPoint(entryPoint)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(new ApiAccessDeniedHandler(objectMapper)));

        return http.build();
    }

    // Authenticated, and not with a strategy's delegated token.
    static AuthorizationManager<RequestAuthorizationContext> customerTokensOnly() {
        return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
            Authentication current = authentication.get();
            boolean granted = TRUST_RESOLVER.isAuthenticated(current) && !isStrategyToken(current);
            return new AuthorizationDecision(granted);
        };
    }

    static boolean isStrategyToken(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwtAuthentication
            && StrategyTokenProperties.ISSUER.equals(jwtAuthentication.getToken().getClaimAsString(JwtClaimNames.ISS));
    }

    // Customer tokens (issued by the auth service) and, when a key is configured, strategy tokens.
    // A token from any other issuer is refused before its signature is even checked.
    JwtIssuerAuthenticationManagerResolver issuerResolver(
        JwtDecoder customerDecoder,
        JwtProperties jwtProperties,
        StrategyTokenProperties strategyTokenProperties
    ) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(jwtProperties.issuer(), authenticationManager(customerDecoder));
        if (strategyTokenProperties.configured()) {
            managers.put(StrategyTokenProperties.ISSUER,
                authenticationManager(strategyJwtDecoder(strategyTokenProperties, jwtProperties)));
        }
        Map<String, AuthenticationManager> byIssuer = Map.copyOf(managers);
        return new JwtIssuerAuthenticationManagerResolver(byIssuer::get);
    }

    private static AuthenticationManager authenticationManager(JwtDecoder decoder) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(SecurityConfig::toAuthentication);
        return new ProviderManager(provider);
    }

    private static AbstractAuthenticationToken toAuthentication(Jwt token) {
        return new JwtAuthenticationToken(token, List.of(), String.valueOf(token.getClaims().get(AccountClaims.ACCOUNT_ID)));
    }

    // same as nest-auth, only the angular app on 4200 (localhost or 127.0.0.1) is allowed
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://127.0.0.1:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        MacAlgorithm algorithm = resolveAlgorithm(properties.algorithm());
        byte[] secret = requireStrongSecret(properties.sharedSecret(), algorithm, "auth.jwt.shared-secret (JWT_SECRET)");

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(secret, jcaName(algorithm)))
            .macAlgorithm(algorithm)
            .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.clockSkew()),
            // JwtTimestampValidator accepts a token with no exp at all; the contract requires one.
            new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
            new JwtClaimValidator<Object>(AccountClaims.ACCOUNT_ID, AccountClaims::isValidAccountId),
            new JwtClaimValidator<String>(JwtClaimNames.ISS,
                issuer -> Objects.equals(issuer, properties.issuer()))
        ));
        return decoder;
    }

    // A strategy's delegated token: its own key (never the customer key), its own issuer, the one scope
    // it is minted with, a strategy subject, and a lifetime no longer than the configured ttl.
    JwtDecoder strategyJwtDecoder(StrategyTokenProperties strategy, JwtProperties customer) {
        byte[] secret = requireStrongSecret(strategy.sharedSecret(), MacAlgorithm.HS256,
            "auth.strategy-token.shared-secret (STRATEGY_TOKEN_SECRET)");
        if (customer.sharedSecret() != null
            && Arrays.equals(secret, customer.sharedSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                "auth.strategy-token.shared-secret (STRATEGY_TOKEN_SECRET) must differ from JWT_SECRET");
        }
        if (StrategyTokenProperties.ISSUER.equals(customer.issuer())) {
            throw new IllegalStateException("auth.jwt.issuer must not be " + StrategyTokenProperties.ISSUER);
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(secret, jcaName(MacAlgorithm.HS256)))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        Duration longestLifetime = strategy.ttl().plus(customer.clockSkew());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(customer.clockSkew()),
            new JwtClaimValidator<Instant>(JwtClaimNames.EXP,
                exp -> exp != null && !exp.isAfter(Instant.now().plus(longestLifetime))),
            new JwtClaimValidator<Object>(AccountClaims.ACCOUNT_ID, AccountClaims::isValidAccountId),
            new JwtClaimValidator<String>(JwtClaimNames.ISS, StrategyTokenProperties.ISSUER::equals),
            new JwtClaimValidator<String>(JwtClaimNames.SUB,
                sub -> sub != null && sub.startsWith(StrategyTokenProperties.SUBJECT_PREFIX)),
            new JwtClaimValidator<String>(StrategyTokenProperties.SCOPE_CLAIM,
                StrategyTokenProperties.ORDERS_PLACE_SCOPE::equals)
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
    private static byte[] requireStrongSecret(String sharedSecret, MacAlgorithm algorithm, String name) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        byte[] secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        int minimumBytes = switch (algorithm) {
            case HS256 -> 32;
            case HS384 -> 48;
            case HS512 -> 64;
        };
        if (secret.length < minimumBytes) {
            throw new IllegalStateException(name + " must be at least "
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
