package com.payflow.user.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.jwt.JwtProperties;
import com.payflow.common.jwt.JwtTokenPayload;
import com.payflow.common.jwt.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final RequestMatcher PUBLIC_PATHS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/auth/**"),
            new AntPathRequestMatcher("/actuator/**"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // If the request is in the whitelist, skip the authentication
        if (PUBLIC_PATHS.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // It does not have the authorization header, skip the authentication
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        try {
            JwtTokenPayload jwtTokenPayload = jwtUtil.parseAccessToken(token);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + jwtTokenPayload.role()));
            var auth = new UsernamePasswordAuthenticationToken(jwtTokenPayload.userId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            response.setStatus(ex.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(ex.getStatus().value(), ex.getMessage()));
            return;
        }
    }
}
