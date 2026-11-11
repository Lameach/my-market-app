package ru.yandex.practicum.mymarket.service;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.ItemLookup;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.Cart;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;

@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ItemLookup items;
    private final TransactionalOperator transactions;

    public CartService(
            CartRepository carts,
            CartItemRepository cartItems,
            ItemLookup items,
            ReactiveTransactionManager transactionManager) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.items = items;
        this.transactions = TransactionalOperator.create(transactionManager);
    }

    @Transactional(readOnly = true)
    public Mono<Map<Long, Integer>> counts(String sessionId) {
        return carts.findBySessionId(sessionId)
                .flatMap(cart -> cartItems.findByCartIdOrderByIdAsc(cart.getId())
                        .collectMap(CartItem::getItemId, CartItem::getCount))
                .defaultIfEmpty(Map.of());
    }

    @Transactional(readOnly = true)
    public Mono<List<ItemDto>> lines(String sessionId) {
        return carts.findBySessionId(sessionId)
                .flatMap(cart -> cartItems.findByCartIdOrderByIdAsc(cart.getId()).collectList())
                .flatMap(this::toLines)
                .defaultIfEmpty(List.of());
    }

    public Mono<Void> change(String sessionId, long itemId, CartAction action) {
        return once(sessionId, itemId, action)
                .onErrorResume(DuplicateKeyException.class, ex -> once(sessionId, itemId, action));
    }

    private Mono<Void> once(String sessionId, long itemId, CartAction action) {
        return transactions.transactional(changeLocked(sessionId, itemId, action));
    }

    private Mono<Void> changeLocked(String sessionId, long itemId, CartAction action) {
        return items.card(itemId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(item -> carts.findBySessionIdForUpdate(sessionId)
                        .switchIfEmpty(Mono.defer(() -> action == CartAction.PLUS
                                ? carts.save(new Cart(sessionId))
                                : Mono.empty()))
                        .flatMap(cart -> apply(cart, item, action)))
                .then();
    }

    private Mono<Void> apply(Cart cart, Item item, CartAction action) {
        return cartItems.findByCartIdAndItemId(cart.getId(), item.getId())
                .flatMap(line -> update(line, action))
                .switchIfEmpty(Mono.defer(() -> action == CartAction.PLUS
                        ? cartItems.save(new CartItem(cart.getId(), item.getId(), 1)).thenReturn(true)
                        : Mono.empty()))
                .then();
    }

    private Mono<Boolean> update(CartItem line, CartAction action) {
        return switch (action) {
            case PLUS -> {
                line.setCount(line.getCount() + 1);
                yield cartItems.save(line).thenReturn(true);
            }
            case MINUS -> line.getCount() <= 1
                    ? cartItems.delete(line).thenReturn(true)
                    : decrement(line);
            case DELETE -> cartItems.delete(line).thenReturn(true);
        };
    }

    private Mono<Boolean> decrement(CartItem line) {
        line.setCount(line.getCount() - 1);
        return cartItems.save(line).thenReturn(true);
    }

    private Mono<List<ItemDto>> toLines(List<CartItem> rows) {
        if (rows.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Long> ids = rows.stream().map(CartItem::getItemId).toList();
        return items.cards(ids)
                .map(byId -> rows.stream()
                        .map(row -> ItemDto.from(byId.get(row.getItemId()), row.getCount()))
                        .toList());
    }
}
