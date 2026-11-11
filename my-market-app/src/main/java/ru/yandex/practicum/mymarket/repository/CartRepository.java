package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Cart;

public interface CartRepository extends ReactiveCrudRepository<Cart, Long> {

    Mono<Cart> findByUserId(long userId);

    @Query("SELECT * FROM carts WHERE user_id = :userId FOR UPDATE")
    Mono<Cart> findByUserIdForUpdate(@Param("userId") long userId);
}
