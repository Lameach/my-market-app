package ru.yandex.practicum.mymarket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
    public CatalogPage show(String search, String sort, int pageSize, int pageNumber, String sessionId) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedSort = normalizeSort(sort);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int number = pageNumber < 1 ? DEFAULT_PAGE_NUMBER : pageNumber;
        Page<Item> page = items.search(likePattern(normalizedSearch), pageRequest(number, size, normalizedSort));
        Map<Long, Integer> counts = cart.counts(sessionId);
        List<ItemDto> dtos = page.getContent().stream()
                .map(item -> ItemDto.from(item, counts.getOrDefault(item.getId(), 0)))
                .toList();
        return new CatalogPage(
                rows(dtos),
                new Paging(size, number, page.hasPrevious(), page.hasNext()),
                normalizedSearch,
                normalizedSort);
    }

    @Transactional(readOnly = true)
    public ItemDto item(long id, String sessionId) {
        Item item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int count = cart.counts(sessionId).getOrDefault(id, 0);
        return ItemDto.from(item, count);
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

    private static PageRequest pageRequest(int pageNumber, int pageSize, String sort) {
        return PageRequest.of(pageNumber - 1, pageSize, toSort(sort));
    }

    private static Sort toSort(String sort) {
        return switch (sort) {
            case SORT_ALPHA -> Sort.by(Sort.Order.asc("title").ignoreCase()).and(Sort.by("id"));
            case SORT_PRICE -> Sort.by(Sort.Order.asc("price")).and(Sort.by("id"));
            default -> Sort.by("id");
        };
    }

    static List<List<ItemDto>> rows(List<ItemDto> items) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int start = 0; start < items.size(); start += 3) {
            List<ItemDto> row = new ArrayList<>(3);
            for (int index = start; index < start + 3; index++) {
                row.add(index < items.size() ? items.get(index) : ItemDto.placeholder());
            }
            rows.add(row);
        }
        return rows;
    }
}
