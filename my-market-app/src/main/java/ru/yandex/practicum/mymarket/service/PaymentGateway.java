package ru.yandex.practicum.mymarket.service;

import reactor.core.publisher.Mono;

public interface PaymentGateway {

    Mono<Long> balance(long userId);

    Mono<Void> pay(long userId, long amount);
}
