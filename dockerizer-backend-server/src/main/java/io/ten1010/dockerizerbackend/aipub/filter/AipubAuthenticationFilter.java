package io.ten1010.dockerizerbackend.aipub.filter;

import io.ten1010.dockerizerbackend.aipub.dto.SelfSubjectReviewResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AipubAuthenticationFilter extends OncePerRequestFilter {

    private static final String AIPUB_ACCESS_COOKIE = "AIPUB_ACCESS_COOKIE";

    private final RestClient aipubRestClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String cookieValue = extractCookie(request, AIPUB_ACCESS_COOKIE);
        if (cookieValue != null) {
            try {
                SelfSubjectReviewResponse review = aipubRestClient.post()
                        .uri("/api/v1alpha1/selfsubjectreviews")
                        .header(HttpHeaders.COOKIE, AIPUB_ACCESS_COOKIE + "=" + cookieValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}")
                        .retrieve()
                        .body(SelfSubjectReviewResponse.class);

                if (review != null && review.isAuthenticated()) {
                    List<SimpleGrantedAuthority> authorities = review.getRoles() != null
                            ? review.getRoles().stream().map(SimpleGrantedAuthority::new).toList()
                            : List.of();

                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    review.getUsername(), null, authorities));
                }
            } catch (HttpClientErrorException e) {
                // 4xx: 쿠키가 없거나 만료/무효 → 정상적인 미인증으로 처리하고 요청을 계속 진행한다
                // (다운스트림 인가 단계에서 401 처리). SRV-13.
                log.debug("AIPub auth rejected cookie: {}", e.getStatusCode());
            } catch (Exception e) {
                // 5xx·연결 실패 등 일시적 업스트림 장애는 "미인증"과 구분된다. 조용히 통과시키면
                // 잘못된 403 으로 오인되므로, 503 으로 빠르게 실패시켜 재시도를 유도한다(SRV-13).
                log.error("AIPub auth verification failed (upstream error)", e);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Authentication service temporarily unavailable");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

}
