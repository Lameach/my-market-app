package ru.yandex.practicum.mymarket.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ItemRepository items;

    public OrderService(
            OrderRepository orders,
            OrderItemRepository orderItems,
            CartRepository carts,
            CartItemRepository cartItems,
            ItemRepository items) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.carts = carts;
        this.cartItems = cartItems;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public Mono<List<OrderDto>> list(String sessionId) {
        return orders.findBySessionIdOrderByIdDesc(sessionId)
                .concatMap(this::toDto)
                .collectList();
    }

    @Transactional(readOnly = true)
    public Mono<OrderDto> get(long id, String sessionId) {
        return orders.findByIdAndSessionId(id, sessionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(this::toDto);
    }

    @Transactional
    public Mono<Long> buy(String sessionId) {
        return carts.findBySessionIdForUpdate(sessionId)
                .flatMap(cart -> cartItems.findByCartIdOrderByIdAsc(cart.getId())
                        .filter(line -> line.getCount() > 0)
                        .collectList()
                        .flatMap(lines -> place(sessionId, lines)));
    }

    private Mono<Long> place(String sessionId, List<CartItem> lines) {
        if (lines.isEmpty()) {
            return Mono.empty();
        }
        List<Long> ids = lines.stream().map(CartItem::getItemId).toList();
        return items.findAllById(ids)
                .collectMap(Item::getId)
                .flatMap(byId -> orders.save(new Order(sessionId))
                        .flatMap(saved -> Flux.fromIterable(lines)
                                .concatMap(line -> {
                                    Item item = byId.get(line.getItemId());
                                    return orderItems.save(new OrderItem(
                                            saved.getId(),
                                            item.getTitle(),
                                            item.getImgPath(),
                                            item.getPrice(),
                                            line.getCount()));
                                })
                                .then(cartItems.deleteAll(lines))
                                .thenReturn(saved.getId())));
    }

    private Mono<OrderDto> toDto(Order order) {
        return orderItems.findByOrderIdOrderByIdAsc(order.getId())
                .map(line -> new OrderItemDto(
                        line.getId(), line.getTitle(), line.getImgPath(), line.getPrice(), line.getCount()))
                .collectList()
                .map(lines -> OrderDto.of(order.getId(), lines));
    }
}
