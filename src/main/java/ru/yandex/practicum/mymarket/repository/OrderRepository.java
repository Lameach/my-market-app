package ru.yandex.practicum.mymarket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.mymarket.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findBySessionIdOrderByIdDesc(String sessionId);

    Optional<Order> findByIdAndSessionId(long id, String sessionId);
}
