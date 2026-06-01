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
            } catch (Exception e) {
                log.warn("AIPub auth verification failed: {}", e.getMessage());
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
