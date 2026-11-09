package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Cart;

public interface CartRepository extends ReactiveCrudRepository<Cart, Long> {

    Mono<Cart> findBySessionId(String sessionId);
}
