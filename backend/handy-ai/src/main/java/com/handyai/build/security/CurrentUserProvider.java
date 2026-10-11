package com.handyai.build.security;

import com.handyai.build.exception.UnauthorizedException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Single read point for "who is calling", backed by the request attribute the filter set. */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(JwtAuthenticationFilter.REQUEST_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return value instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    public Optional<Long> currentUserId() {
        return current().map(AuthenticatedUser::userId);
    }

    public AuthenticatedUser require() {
        return current().orElseThrow(
                () -> new UnauthorizedException("Sign in to continue"));
    }

    public Long requireUserId() {
        return require().userId();
    }
}
