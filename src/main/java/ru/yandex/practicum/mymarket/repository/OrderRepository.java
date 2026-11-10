package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Order;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    Flux<Order> findBySessionIdOrderByIdDesc(String sessionId);

    Mono<Order> findByIdAndSessionId(long id, String sessionId);
}
