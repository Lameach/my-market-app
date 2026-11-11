package ru.yandex.practicum.mymarket.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.CatalogEntry;
import ru.yandex.practicum.mymarket.cache.ItemLookup;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.Item;

@Service
public class CatalogService {

    static final int DEFAULT_PAGE_SIZE = 5;
    static final int DEFAULT_PAGE_NUMBER = 1;
    static final String SORT_NO = "NO";
    static final String SORT_ALPHA = "ALPHA";
    static final String SORT_PRICE = "PRICE";

    private final ItemLookup items;
    private final CartService cart;

    public CatalogService(ItemLookup items, CartService cart) {
        this.items = items;
        this.cart = cart;
    }

    @Transactional(readOnly = true)
    public Mono<CatalogPage> show(String search, String sort, int pageSize, int pageNumber, Long userId) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedSort = normalizeSort(sort);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int number = pageNumber < 1 ? DEFAULT_PAGE_NUMBER : pageNumber;
        String needle = normalizedSearch.toLowerCase(Locale.ROOT);
        Mono<Map<Long, Integer>> counts = userId == null ? Mono.just(Map.of()) : cart.counts(userId);
        return items.catalog()
                .map(all -> select(all, needle, normalizedSort, size, number))
                .flatMap(selection -> Mono.zip(
                        items.cards(selection.entries().stream().map(CatalogEntry::id).toList()),
                        counts,
                        (cards, countMap) -> toPage(
                                selection, cards, countMap, size, number, normalizedSearch, normalizedSort)));
    }

    @Transactional(readOnly = true)
    public Mono<ItemDto> item(long id, Long userId) {
        Mono<Map<Long, Integer>> counts = userId == null ? Mono.just(Map.of()) : cart.counts(userId);
        return items.card(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .zipWith(counts)
                .map(tuple -> ItemDto.from(tuple.getT1(), tuple.getT2().getOrDefault(id, 0)));
    }

    private static Selection select(List<CatalogEntry> all, String needle, String sort, int size, int number) {
        List<CatalogEntry> matched = all.stream()
                .filter(item -> needle.isEmpty()
                        || item.title().toLowerCase(Locale.ROOT).contains(needle)
                        || item.description().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(comparator(sort))
                .toList();
        int from = (int) Math.min((long) (number - 1) * size, matched.size());
        int to = (int) Math.min((long) from + size, matched.size());
        return new Selection(matched.subList(from, to), matched.size());
    }

    private static Comparator<CatalogEntry> comparator(String sort) {
        Comparator<CatalogEntry> byId = Comparator.comparingLong(CatalogEntry::id);
        return switch (sort) {
            case SORT_ALPHA -> Comparator.comparing((CatalogEntry item) -> item.title().toLowerCase(Locale.ROOT))
                    .thenComparing(byId);
            case SORT_PRICE -> Comparator.comparingInt(CatalogEntry::price).thenComparing(byId);
            default -> byId;
        };
    }

    private static CatalogPage toPage(
            Selection selection,
            Map<Long, Item> cards,
            Map<Long, Integer> counts,
            int size,
            int number,
            String search,
            String sort) {
        List<ItemDto> dtos = selection.entries().stream()
                .map(entry -> ItemDto.from(cards.get(entry.id()), counts.getOrDefault(entry.id(), 0)))
                .toList();
        boolean hasPrevious = number > 1;
        boolean hasNext = (long) number * size < selection.total();
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

    private record Selection(List<CatalogEntry> entries, long total) {
    }
}
