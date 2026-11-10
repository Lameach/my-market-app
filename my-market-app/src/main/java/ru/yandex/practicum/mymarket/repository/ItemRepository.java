package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import ru.yandex.practicum.mymarket.model.Item;

public interface ItemRepository extends ReactiveCrudRepository<Item, Long> {
}
