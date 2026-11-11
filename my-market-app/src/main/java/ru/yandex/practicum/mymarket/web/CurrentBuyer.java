package ru.yandex.practicum.mymarket.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.config.SecurityConfig;
import ru.yandex.practicum.mymarket.security.Buyer;

final class CurrentBuyer {

    private CurrentBuyer() {
    }

    static Mono<SecurityContext> context() {
        return ReactiveSecurityContextHolder.getContext()
                .defaultIfEmpty(SecurityConfig.anonymousContext());
    }

    static Long userId(SecurityContext context) {
        Authentication authentication = context.getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof Buyer buyer)) {
            return null;
        }
        return buyer.getId();
    }

    static Mono<Long> id() {
        return ReactiveSecurityContextHolder.getContext().mapNotNull(CurrentBuyer::userId);
    }
}
