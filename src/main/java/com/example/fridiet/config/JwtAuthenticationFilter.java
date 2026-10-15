package com.example.fridiet.config;

import com.example.fridiet.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/login/oauth2/code/")
        || path.startsWith("/link/oauth2/code/")
        || path.startsWith("/api/auth/")
        || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // HTTP 요청 헤더에서 Authorization 값 추출
        String authHeader = request.getHeader("Authorization");

        // 헤더가 없거나 "Bearer "로 시작하지 않으면 통과(인증 없는 공개 API용)
        if(authHeader == null  || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " 뒤의 토큰 문자열 추출
        String token = authHeader.substring(7);

        // 정상 토큰이라면 유저 ID 추출, 시큐리티 컨텍스트에 인증 정보 등록
        if(jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.extractUserId(token);

            // 시큐리티 전용 인증 토큰 생성(주체에 userId 저장, 권한은 빈 리스트)
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 시큐리티 인증 바구니에 유저 ID 탑승
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
