package ru.yandex.practicum.payment.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BalanceService {

    public static final String NON_POSITIVE = "Сумма платежа должна быть положительной";
    public static final String INSUFFICIENT = "Недостаточно средств";

    private final long initial;
    private final ConcurrentHashMap<Long, AtomicLong> accounts = new ConcurrentHashMap<>();

    public BalanceService(@Value("${payment.initial-balance}") long initial) {
        this.initial = initial;
    }

    public Mono<Long> current(long userId) {
        return Mono.fromSupplier(() -> account(userId).get());
    }

    public Mono<Long> pay(long userId, long amount) {
        if (amount <= 0) {
            return Mono.error(new PaymentException(HttpStatus.BAD_REQUEST, NON_POSITIVE));
        }
        return Mono.defer(() -> withdraw(userId, amount));
    }

    void clear() {
        accounts.clear();
    }

    private AtomicLong account(long userId) {
        return accounts.computeIfAbsent(userId, id -> new AtomicLong(initial));
    }

    private Mono<Long> withdraw(long userId, long amount) {
        AtomicLong balance = account(userId);
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
