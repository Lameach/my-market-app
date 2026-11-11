package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.yandex.practicum.mymarket.cache.RedisItemLookup;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

class ItemCacheTest extends StorefrontSpringTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ItemRepository items;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Test
    void catalogCardAndCartKeepTheCachedPriceUntilTheKeysAreRemoved() {
        Item apples = item("Яблоки Гала");
        int original = apples.getPrice();
        String cached = original + " руб.";
        String changed = "7 руб.";
        WebTestClient client = login(webTestClient, "buyer", "buyer");
        try {
            client.get().uri("/items/{id}", apples.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(cached));
            client.get()
                    .uri(builder -> builder.path("/items").queryParam("search", "яблок").queryParam("pageSize", "20").build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(cached));

            client.post().uri("/items/{id}", apples.getId())
                    .body(BodyInserters.fromFormData("action", "PLUS"))
                    .exchange()
                    .expectStatus().is3xxRedirection();

            apples.setPrice(7);
            items.save(apples).block(TIMEOUT);

            client.get().uri("/items/{id}", apples.getId())
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(cached).doesNotContain(changed));
            client.get()
                    .uri(builder -> builder.path("/items").queryParam("search", "яблок").queryParam("pageSize", "20").build())
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(cached).doesNotContain(changed));
            client.get().uri("/cart/items")
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(cached).doesNotContain(changed));

            redis.delete(RedisItemLookup.CATALOG_KEY, RedisItemLookup.ITEM_PREFIX + apples.getId()).block(TIMEOUT);

            client.get().uri("/items/{id}", apples.getId())
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(changed));
            client.get()
                    .uri(builder -> builder.path("/items").queryParam("search", "яблок").queryParam("pageSize", "20").build())
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(changed));
            client.get().uri("/cart/items")
                    .exchange()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains(changed));
        } finally {
            apples.setPrice(original);
            items.save(apples).block(TIMEOUT);
            redis.delete(RedisItemLookup.CATALOG_KEY, RedisItemLookup.ITEM_PREFIX + apples.getId()).block(TIMEOUT);
        }
    }

    private Item item(String title) {
        Item found = items.findAll()
                .filter(row -> row.getTitle().equals(title))
                .blockFirst(TIMEOUT);
        assertThat(found).isNotNull();
        return found;
    }
}
