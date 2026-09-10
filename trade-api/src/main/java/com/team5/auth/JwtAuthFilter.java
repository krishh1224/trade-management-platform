package com.team5.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team5.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenVerifier jwtTokenVerifier;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtTokenVerifier jwtTokenVerifier, ObjectMapper objectMapper) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorisation = request.getHeader("Authorization");
        if (authorisation == null || !authorisation.startsWith("Bearer ")) {
            writeUnauthorised(response);
            return;
        }

        String token = authorisation.substring("Bearer ".length()).trim();
        try {
            Long accountId = jwtTokenVerifier.verifyAndGetAccountId(token);
            request.setAttribute(RequestAuthContext.AUTHENTICATED_ACCOUNT_ID, accountId);
            filterChain.doFilter(request, response);
        } catch (UnauthorisedException exception) {
            writeUnauthorised(response);
        }
    }

    private void writeUnauthorised(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("AUTH-401", "Unauthorised"));
    }
}