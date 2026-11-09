package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.mymarket.model.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
