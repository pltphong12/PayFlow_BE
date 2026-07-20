package com.payflow.gateway.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.jwt.JwtTokenPayload;
import com.payflow.common.jwt.JwtUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/actuator/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        try {
            JwtTokenPayload payload = jwtUtil.parseAccessToken(authHeader.substring(7));
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", payload.userId().toString())
                    .header("X-User-Role", payload.role())
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (BusinessException ex) {
            return writeError(exchange, ex.getStatus(), ex.getMessage());
        }
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                    ApiResponse.error(status.value(), message)
            );
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
