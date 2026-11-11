package ru.yandex.practicum.payment.service;

import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(properties = "payment.initial-balance=1000")
@AutoConfigureWebTestClient
@Import(PaymentApiTest.JwtDecoderConfig.class)
class PaymentApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private BalanceService balances;

    @BeforeEach
    void resetBalance() {
        balances.clear();
    }

    @Test
    void balanceStartsAtTheConfiguredAmount() {
        client().get().uri("/balance?userId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(1000);
    }

    @Test
    void paymentSubtractsAndThenRefusesWithoutGoingNegative() {
        client().post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":400,\"userId\":1}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.balance").isEqualTo(600);

        client().post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":601,\"userId\":1}")
                .exchange()
                .expectStatus().isEqualTo(PAYMENT_REQUIRED)
                .expectBody()
                .jsonPath("$.message").isEqualTo(BalanceService.INSUFFICIENT);

        client().get().uri("/balance?userId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(600);
    }

    @Test
    void eachBuyerHasASeparateBalance() {
        client().post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":100,\"userId\":1}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(900);

        client().get().uri("/balance?userId=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(1000);

        client().get().uri("/balance?userId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(900);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        client().post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":0,\"userId\":1}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(BalanceService.NON_POSITIVE);

        client().post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        client().get().uri("/balance?userId=1")
                .exchange()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(1000);
    }

    @Test
    void missingTokenIsUnauthorized() {
        webTestClient.get().uri("/balance?userId=1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongScopeIsForbidden() {
        webTestClient.mutateWith(mockJwt())
                .get().uri("/balance?userId=1")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void anythingElseIsClosed() {
        client().get().uri("/other")
                .exchange()
                .expectStatus().isForbidden();
    }

    private WebTestClient client() {
        return webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_payment")));
    }

    @TestConfiguration
    static class JwtDecoderConfig {

        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("storefront")
                    .build());
        }
    }
}
