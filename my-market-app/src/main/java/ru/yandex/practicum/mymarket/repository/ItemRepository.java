package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Item;

public interface ItemRepository extends ReactiveCrudRepository<Item, Long> {

    @Query("""
            SELECT * FROM items
            WHERE LOWER(title) LIKE :pattern ESCAPE '!'
               OR LOWER(description) LIKE :pattern ESCAPE '!'
            ORDER BY
                CASE WHEN :sort = 'ALPHA' THEN LOWER(title) END ASC,
                CASE WHEN :sort = 'PRICE' THEN price END ASC,
                id ASC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Item> search(
            @Param("pattern") String pattern,
            @Param("sort") String sort,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*) FROM items
            WHERE LOWER(title) LIKE :pattern ESCAPE '!'
               OR LOWER(description) LIKE :pattern ESCAPE '!'
            """)
    Mono<Long> countSearch(@Param("pattern") String pattern);
}
