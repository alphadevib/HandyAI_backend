package com.handyai.build.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the bearer token once per request and stashes the caller on the request, and in Spring
 * Security's context with a {@code ROLE_<role>} authority so {@code @PreAuthorize} checks work.
 *
 * <p>The filter never rejects a request on its own: endpoints that need a user ask
 * {@link CurrentUserProvider} for one, which keeps public browsing open to anonymous visitors and
 * keeps authentication logic in a single place.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "handyai.currentUser";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenVersions tokenVersions;

    public JwtAuthenticationFilter(JwtService jwtService, TokenVersions tokenVersions) {
        this.jwtService = jwtService;
        this.tokenVersions = tokenVersions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            // A token from before the user's last sign-out (or for a deleted user) is ignored.
            jwtService.verify(header.substring(PREFIX.length()).trim())
                    .filter(user -> tokenVersions.current(user.userId()).orElse(-1) == user.tokenVersion())
                    .ifPresent(user -> authenticate(request, user));
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticate(HttpServletRequest request, AuthenticatedUser user) {
        request.setAttribute(REQUEST_ATTRIBUTE, user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
        SecurityContextHolder.setContext(context);
    }
}
