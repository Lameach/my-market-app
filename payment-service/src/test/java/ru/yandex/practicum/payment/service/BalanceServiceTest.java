package ru.yandex.practicum.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BalanceServiceTest {

    private static final long USER = 1L;

    @Test
    void chargesUntilTheBalanceWouldGoNegative() {
        BalanceService balances = new BalanceService(1000);

        assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isEqualTo(1000);
        assertThat(balances.pay(USER, 400).block(Duration.ofSeconds(2))).isEqualTo(600);
        assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isEqualTo(600);

        assertThatThrownBy(() -> balances.pay(USER, 601).block(Duration.ofSeconds(2)))
                .isInstanceOf(PaymentException.class)
                .extracting(thrown -> ((PaymentException) thrown).status())
                .isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isEqualTo(600);
    }

    @Test
    void oneBuyerDoesNotSpendAnothersMoney() {
        BalanceService balances = new BalanceService(1000);

        assertThat(balances.pay(USER, 400).block(Duration.ofSeconds(2))).isEqualTo(600);
        assertThat(balances.current(2L).block(Duration.ofSeconds(2))).isEqualTo(1000);
        assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isEqualTo(600);
    }

    @Test
    void rejectsANonPositiveAmountWithoutChangingTheBalance() {
        BalanceService balances = new BalanceService(500);

        assertThatThrownBy(() -> balances.pay(USER, 0).block(Duration.ofSeconds(2)))
                .isInstanceOf(PaymentException.class)
                .extracting(thrown -> ((PaymentException) thrown).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> balances.pay(USER, -10).block(Duration.ofSeconds(2)))
                .isInstanceOf(PaymentException.class);
        assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isEqualTo(500);
    }

    @Test
    void concurrentChargesNeverOverdraw() throws Exception {
        BalanceService balances = new BalanceService(1000);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        return false;
                    }
                    try {
                        balances.pay(USER, 100).block(Duration.ofSeconds(2));
                        return true;
                    } catch (PaymentException ex) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long paid = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    paid++;
                }
            }
            assertThat(paid).isEqualTo(10);
            assertThat(balances.current(USER).block(Duration.ofSeconds(2))).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
