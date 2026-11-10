package ru.yandex.practicum.payment.service;

import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "payment.initial-balance=1000")
@AutoConfigureWebTestClient
class PaymentApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private BalanceService balances;

    @BeforeEach
    void resetBalance() {
        balances.reset(1000);
    }

    @Test
    void balanceStartsAtTheConfiguredAmount() {
        webTestClient.get().uri("/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(1000);
    }

    @Test
    void paymentSubtractsAndThenRefusesWithoutGoingNegative() {
        webTestClient.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":400}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.balance").isEqualTo(600);

        webTestClient.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":601}")
                .exchange()
                .expectStatus().isEqualTo(PAYMENT_REQUIRED)
                .expectBody()
                .jsonPath("$.message").isEqualTo(BalanceService.INSUFFICIENT);

        webTestClient.get().uri("/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(600);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        webTestClient.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":0}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(BalanceService.NON_POSITIVE);

        webTestClient.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get().uri("/balance")
                .exchange()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(1000);
    }
}
