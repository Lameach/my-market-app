package ru.yandex.practicum.mymarket.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
public class CatalogService {

    static final int DEFAULT_PAGE_SIZE = 5;
    static final int DEFAULT_PAGE_NUMBER = 1;
    static final String SORT_NO = "NO";
    static final String SORT_ALPHA = "ALPHA";
    static final String SORT_PRICE = "PRICE";

    private final ItemRepository items;
    private final CartService cart;

    public CatalogService(ItemRepository items, CartService cart) {
        this.items = items;
        this.cart = cart;
    }

    @Transactional(readOnly = true)
    public Mono<CatalogPage> show(String search, String sort, int pageSize, int pageNumber, String sessionId) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedSort = normalizeSort(sort);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int number = pageNumber < 1 ? DEFAULT_PAGE_NUMBER : pageNumber;
        String pattern = likePattern(normalizedSearch);
        long offset = (long) (number - 1) * size;
        return Mono.zip(
                        items.search(pattern, normalizedSort, size, offset).collectList(),
                        items.countSearch(pattern),
                        cart.counts(sessionId))
                .map(tuple -> toPage(tuple.getT1(), tuple.getT2(), tuple.getT3(), size, number, normalizedSearch, normalizedSort));
    }

    @Transactional(readOnly = true)
    public Mono<ItemDto> item(long id, String sessionId) {
        return items.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .zipWith(cart.counts(sessionId))
                .map(tuple -> ItemDto.from(tuple.getT1(), tuple.getT2().getOrDefault(id, 0)));
    }

    private static CatalogPage toPage(
            List<Item> content,
            long total,
            Map<Long, Integer> counts,
            int size,
            int number,
            String search,
            String sort) {
        List<ItemDto> dtos = content.stream()
                .map(item -> ItemDto.from(item, counts.getOrDefault(item.getId(), 0)))
                .toList();
        boolean hasPrevious = number > 1;
        boolean hasNext = (long) number * size < total;
        return new CatalogPage(dtos, new Paging(size, number, hasPrevious, hasNext), search, sort);
    }

    static String normalizeSort(String sort) {
        if (sort == null) {
            return SORT_NO;
        }
        return switch (sort.trim().toUpperCase(Locale.ROOT)) {
            case SORT_ALPHA -> SORT_ALPHA;
            case SORT_PRICE -> SORT_PRICE;
            default -> SORT_NO;
        };
    }

    static String likePattern(String search) {
        String escaped = search.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

}
