package ru.yandex.practicum.mymarket.config;

import java.util.UUID;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.WebSessionServerCsrfTokenRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stores the CSRF token in the web session without calling {@code changeSessionId}.
 * The stock repository rotates the session id on every save, so the next request
 * still carrying the previous {@code SESSION} cookie is treated as anonymous.
 */
class StableSessionCsrfTokenRepository implements ServerCsrfTokenRepository {

    static final String ATTRIBUTE = WebSessionServerCsrfTokenRepository.class.getName() + ".CSRF_TOKEN";

    @Override
    public Mono<CsrfToken> generateToken(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", UUID.randomUUID().toString()));
    }

    @Override
    public Mono<Void> saveToken(ServerWebExchange exchange, CsrfToken token) {
        return exchange.getSession()
                .doOnNext(session -> {
                    if (token == null) {
                        session.getAttributes().remove(ATTRIBUTE);
                    } else {
                        session.getAttributes().put(ATTRIBUTE, token);
                    }
                })
                .then();
    }

    @Override
    public Mono<CsrfToken> loadToken(ServerWebExchange exchange) {
        return exchange.getSession()
                .filter(session -> session.getAttributes().containsKey(ATTRIBUTE))
                .map(session -> session.getAttribute(ATTRIBUTE));
    }
}
