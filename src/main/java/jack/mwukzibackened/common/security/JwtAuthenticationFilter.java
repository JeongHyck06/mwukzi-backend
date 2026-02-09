package jack.mwukzibackened.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jack.mwukzibackened.common.jwt.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            String subject = claims.getSubject();
            UUID userId = parseUserId(subject);
            if (userId != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = Collections.emptyList();
                Object role = claims.get("role");
                String roleName = null;
                if (role instanceof String roleValue) {
                    roleName = roleValue.trim();
                }
                if (roleName != null && !roleName.isBlank()) {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()));
                }

                Object providerClaim = claims.get("provider");
                String provider = providerClaim instanceof String value ? value : null;
                AuthenticatedUser principal = new AuthenticatedUser(userId, provider, roleName);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.debug("JWT 검증 실패: path={}", request.getRequestURI());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private UUID parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
