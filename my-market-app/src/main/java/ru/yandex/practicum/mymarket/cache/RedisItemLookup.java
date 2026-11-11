package ru.yandex.practicum.mymarket.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
public class RedisItemLookup implements ItemLookup {

    public static final String CATALOG_KEY = "market:catalog";
    public static final String ITEM_PREFIX = "market:item:";

    private final ReactiveStringRedisTemplate redis;
    private final ItemRepository items;
    private final Duration ttl;
    private final ObjectMapper json = new ObjectMapper();

    public RedisItemLookup(
            ReactiveStringRedisTemplate redis,
            ItemRepository items,
            @Value("${market.cache.ttl}") Duration ttl) {
        this.redis = redis;
        this.items = items;
        this.ttl = ttl;
    }

    @Override
    public Mono<List<CatalogEntry>> catalog() {
        return redis.opsForValue().get(CATALOG_KEY)
                .flatMap(body -> Mono.fromCallable(() -> readCatalog(body)).onErrorResume(ex -> loadCatalog()))
                .switchIfEmpty(Mono.defer(this::loadCatalog));
    }

    @Override
    public Mono<Item> card(long id) {
        String key = ITEM_PREFIX + id;
        return redis.opsForValue().get(key)
                .flatMap(body -> Mono.fromCallable(() -> readCard(body).toItem()).onErrorResume(ex -> loadCard(id)))
                .switchIfEmpty(Mono.defer(() -> loadCard(id)));
    }

    @Override
    public Mono<Map<Long, Item>> cards(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> ordered = List.copyOf(ids);
        List<String> keys = ordered.stream().map(id -> ITEM_PREFIX + id).toList();
        return redis.opsForValue().multiGet(keys).flatMap(values -> {
            Map<Long, Item> found = new HashMap<>();
            List<Long> missing = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                String body = values.get(i);
                if (body == null) {
                    missing.add(ordered.get(i));
                    continue;
                }
                try {
                    found.put(ordered.get(i), readCard(body).toItem());
                } catch (IllegalStateException ex) {
                    missing.add(ordered.get(i));
                }
            }
            if (missing.isEmpty()) {
                return Mono.just(Map.copyOf(found));
            }
            return items.findAllById(missing)
                    .concatMap(item -> cacheCard(item).thenReturn(item))
                    .doOnNext(item -> found.put(item.getId(), item))
                    .then(Mono.fromSupplier(() -> Map.copyOf(found)));
        });
    }

    private Mono<List<CatalogEntry>> loadCatalog() {
        return items.findAll().collectList().flatMap(rows -> {
            List<CatalogEntry> catalog = rows.stream().map(CatalogEntry::from).toList();
            return redis.opsForValue().set(CATALOG_KEY, write(catalog), ttl)
                    .then(Flux.fromIterable(rows).concatMap(this::cacheCard).then())
                    .thenReturn(catalog);
        });
    }

    private Mono<Item> loadCard(long id) {
        return items.findById(id).flatMap(item -> cacheCard(item).thenReturn(item));
    }

    private Mono<Boolean> cacheCard(Item item) {
        return redis.opsForValue().set(ITEM_PREFIX + item.getId(), write(CachedCard.from(item)), ttl);
    }

    private List<CatalogEntry> readCatalog(String body) {
        try {
            return json.readValue(body, new TypeReference<List<CatalogEntry>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private CachedCard readCard(String body) {
        try {
            return json.readValue(body, CachedCard.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
