package ru.yandex.practicum.mymarket.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    static final String SESSION_COOKIE = "SESSION";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new ServerSecurityContextRepository() {
            private static final String ATTR =
                    WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME;

            @Override
            public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
                return exchange.getSession()
                        .doOnNext(session -> {
                            if (context == null) {
                                session.getAttributes().remove(ATTR);
                            } else {
                                session.getAttributes().put(ATTR, context);
                            }
                        })
                        .then();
            }

            @Override
            public Mono<SecurityContext> load(ServerWebExchange exchange) {
                return exchange.getSession()
                        .mapNotNull(session -> session.getAttribute(ATTR));
            }
        };
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ServerSecurityContextRepository securityContextRepository) {
        RedirectServerLogoutSuccessHandler logoutSuccess = new RedirectServerLogoutSuccessHandler();
        logoutSuccess.setLogoutSuccessUrl(URI.create("/login?logout"));
        SecurityContextServerLogoutHandler clearContext = new SecurityContextServerLogoutHandler();
        clearContext.setSecurityContextRepository(securityContextRepository);
        return http
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/", "/items", "/items/{id}", "/login", "/images/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/login").permitAll()
                        .anyExchange().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/logout"))
                        .logoutHandler(new DelegatingServerLogoutHandler(List.of(
                                clearContext,
                                new WebSessionServerLogoutHandler(),
                                (exchange, authentication) -> {
                                    exchange.getExchange().getResponse().addCookie(ResponseCookie.from(SESSION_COOKIE, "")
                                            .path("/")
                                            .maxAge(Duration.ZERO)
                                            .httpOnly(true)
                                            .build());
                                    return Mono.empty();
                                })))
                        .logoutSuccessHandler(logoutSuccess))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((exchange, exception) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create("/login"));
                    return Mono.empty();
                }))
                .anonymous(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(new StableSessionCsrfTokenRepository()))
                .build();
    }

    @Bean
    public WebFilter csrfTokenSubscriber() {
        return (exchange, chain) -> {
            Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
            if (token == null) {
                return chain.filter(exchange);
            }
            return token.then(chain.filter(exchange));
        };
    }

    public static SecurityContext anonymousContext() {
        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(new AnonymousAuthenticationToken(
                "anonymous",
                "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        return context;
    }
}
