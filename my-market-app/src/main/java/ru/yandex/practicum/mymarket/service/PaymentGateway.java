package ru.yandex.practicum.mymarket.service;

import reactor.core.publisher.Mono;

public interface PaymentGateway {

    Mono<Long> balance();

    Mono<Void> pay(long amount);
}
