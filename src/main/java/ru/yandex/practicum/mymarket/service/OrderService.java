package ru.yandex.practicum.mymarket.service;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.model.Cart;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CartRepository carts;
    private final EntityManager entityManager;

    public OrderService(OrderRepository orders, CartRepository carts, EntityManager entityManager) {
        this.orders = orders;
        this.carts = carts;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<OrderDto> list(String sessionId) {
        return orders.findBySessionIdOrderByIdDesc(sessionId).stream()
                .map(OrderService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(long id, String sessionId) {
        return orders.findByIdAndSessionId(id, sessionId)
                .map(OrderService::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Optional<Long> buy(String sessionId) {
        Cart cart = carts.findBySessionId(sessionId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            return Optional.empty();
        }
        List<CartItem> lines = new ArrayList<>();
        for (CartItem line : cart.getItems()) {
            if (line.getCount() > 0) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        Order order = new Order(sessionId);
        for (CartItem line : lines) {
            order.add(new OrderItem(line.getItem().getTitle(), line.getItem().getPrice(), line.getCount()));
        }
        Order saved = orders.save(order);
        cart.getItems().clear();
        // Clearing the bag does not delete inverse children, so remove each row.
        lines.forEach(entityManager::remove);
        return Optional.of(saved.getId());
    }

    private static OrderDto toDto(Order order) {
        List<OrderItemDto> lines = order.getItems().stream()
                .map(line -> new OrderItemDto(line.getId(), line.getTitle(), line.getPrice(), line.getCount()))
                .toList();
        return OrderDto.of(order.getId(), lines);
    }
}
