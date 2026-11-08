package ru.yandex.practicum.mymarket.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.Cart;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
public class CartService {

    private final CartRepository carts;
    private final ItemRepository items;
    private final EntityManager entityManager;

    public CartService(CartRepository carts, ItemRepository items, EntityManager entityManager) {
        this.carts = carts;
        this.items = items;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> counts(String sessionId) {
        return carts.findBySessionId(sessionId)
                .map(cart -> cart.getItems().stream()
                        .collect(Collectors.toMap(line -> line.getItem().getId(), CartItem::getCount)))
                .orElse(Map.of());
    }

    @Transactional(readOnly = true)
    public List<ItemDto> lines(String sessionId) {
        return carts.findBySessionId(sessionId)
                .map(cart -> cart.getItems().stream()
                        .map(line -> ItemDto.from(line.getItem(), line.getCount()))
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public int total(String sessionId) {
        return lines(sessionId).stream().mapToInt(line -> line.price() * line.count()).sum();
    }

    @Transactional
    public void change(String sessionId, long itemId, CartAction action) {
        Item item = items.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Cart cart = carts.findBySessionId(sessionId).orElse(null);
        if (cart == null) {
            if (action != CartAction.PLUS) {
                return;
            }
            cart = carts.save(new Cart(sessionId));
        }
        CartItem line = findLine(cart, item.getId());
        switch (action) {
            case PLUS -> plus(cart, line, item);
            case MINUS -> minus(cart, line);
            case DELETE -> delete(cart, line);
        }
    }

    private static void plus(Cart cart, CartItem line, Item item) {
        if (line == null) {
            cart.add(new CartItem(cart, item, 1));
        } else {
            line.setCount(line.getCount() + 1);
        }
    }

    private void minus(Cart cart, CartItem line) {
        if (line == null) {
            return;
        }
        if (line.getCount() <= 1) {
            delete(cart, line);
        } else {
            line.setCount(line.getCount() - 1);
        }
    }

    private void delete(Cart cart, CartItem line) {
        if (line == null) {
            return;
        }
        cart.remove(line);
        entityManager.remove(line);
    }

    private static CartItem findLine(Cart cart, long itemId) {
        for (CartItem line : cart.getItems()) {
            if (line.getItem().getId().equals(itemId)) {
                return line;
            }
        }
        return null;
    }
}
