package ru.yandex.practicum.payment.service;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BalanceService {

    public static final String NON_POSITIVE = "Сумма платежа должна быть положительной";
    public static final String INSUFFICIENT = "Недостаточно средств";

    private final AtomicLong balance;

    public BalanceService(@Value("${payment.initial-balance}") long initial) {
        this.balance = new AtomicLong(initial);
    }

    public Mono<Long> current() {
        return Mono.fromSupplier(balance::get);
    }

    public Mono<Long> pay(long amount) {
        if (amount <= 0) {
            return Mono.error(new PaymentException(HttpStatus.BAD_REQUEST, NON_POSITIVE));
        }
        return Mono.defer(() -> withdraw(amount));
    }

    void reset(long amount) {
        balance.set(amount);
    }

    private Mono<Long> withdraw(long amount) {
        while (true) {
            long current = balance.get();
            if (current < amount) {
                return Mono.error(new PaymentException(HttpStatus.PAYMENT_REQUIRED, INSUFFICIENT));
            }
            if (balance.compareAndSet(current, current - amount)) {
                return Mono.just(current - amount);
            }
        }
    }
}
