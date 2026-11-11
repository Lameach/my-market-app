package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;

class PurchaseFlowTest extends StorefrontSpringTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ItemRepository items;

    @Autowired
    private UserRepository users;

    @Test
    void catalogOpensWithSeededProducts() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Яблоки Гала");
                    assertThat(body).contains("Страница: 1");
                    assertThat(body).contains("src=\"/images/apples.png\"");
                    assertThat(body).contains("name=\"pageNumber\" value=\"2\"");
                });

        webTestClient.get()
                .uri(builder -> builder.path("/items").queryParam("search", "яблок").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Яблоки Гала");
                    assertThat(body).doesNotContain("Молоко");
                });

        webTestClient.get()
                .uri(builder -> builder.path("/items")
                        .queryParam("sort", "PRICE")
                        .queryParam("pageSize", "20")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Вода минеральная"));

        webTestClient.get().uri("/images/apples.png")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/login"));

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/login"));
    }

    @Test
    void countDoesNotDropBelowZero() {
        Item apples = item("Яблоки Гала");
        WebTestClient client = login(webTestClient, "buyer", "buyer");

        postItem(client, apples.getId(), "MINUS");
        expectCount(client, apples.getId(), ">0<");
        postItem(client, apples.getId(), "PLUS");
        expectCount(client, apples.getId(), ">1<");
        postItem(client, apples.getId(), "MINUS");
        expectCount(client, apples.getId(), ">0<");
        postItem(client, apples.getId(), "MINUS");
        expectCount(client, apples.getId(), ">0<");
    }

    @Test
    void buyingSnapshotsTheCartClearsItAndOpensTheOrder() {
        Item apples = item("Яблоки Гала");
        Item milk = item("Молоко 3,2%");
        WebTestClient client = login(webTestClient, "buyer", "buyer");

        assertThat(pathAndQuery(locationOf(client.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");

        postItem(client, apples.getId(), "PLUS");
        postItem(client, apples.getId(), "PLUS");

        String backToCatalog = locationOf(client.post().uri("/items")
                .body(BodyInserters.fromFormData("id", milk.getId().toString())
                        .with("action", "PLUS")
                        .with("search", "моло")
                        .with("sort", "ALPHA")
                        .with("pageSize", "10")
                        .with("pageNumber", "1"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(URLDecoder.decode(pathAndQuery(backToCatalog), StandardCharsets.UTF_8))
                .isEqualTo("/items?search=моло&sort=ALPHA&pageSize=10&pageNumber=1");

        long buyerId = users.findByUsername("buyer").block(TIMEOUT).getId();
        drainPayments();
        String location = locationOf(client.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).startsWith("/orders/").endsWith("?newOrder=true");
        RecordedRequest payment = takePayment();
        assertThat(payment).isNotNull();
        assertThat(payment.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(payment.getBody().readUtf8()).contains("\"userId\":" + buyerId);

        client.get().uri(pathAndQuery(location))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Поздравляем");
                    assertThat(body).contains("Яблоки Гала");
                    assertThat(body).contains("Молоко 3,2%");
                    assertThat(body).contains("2 шт.");
                    assertThat(body).contains("src=\"/images/apples.png\"");
                    assertThat(body).contains("src=\"/images/milk.png\"");
                    assertThat(body).contains("Сумма: " + (129 * 2 + 89));
                });

        String orderPath = pathAndQuery(location);
        orderPath = orderPath.substring(0, orderPath.indexOf('?'));
        client.get().uri(orderPath)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Поздравляем"));

        client.get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Заказ №");
                    assertThat(body).contains("Яблоки Гала (2 шт.) " + (129 * 2) + " руб.");
                    assertThat(body).contains("Молоко 3,2% (1 шт.) 89 руб.");
                });

        client.get().uri("/cart/items")
                .exchange()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).doesNotContain("Купить");
                    assertThat(body).doesNotContain("Яблоки Гала");
                });

        login(webTestClient, "second", "second").get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Заказ №"));

        client.get()
                .uri(builder -> builder.path("/items").queryParam("search", "яблок").build())
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains(">0<"));
    }

    private Item item(String title) {
        Item found = items.findAll()
                .filter(row -> row.getTitle().equals(title))
                .blockFirst(TIMEOUT);
        assertThat(found).isNotNull();
        return found;
    }

    private static void postItem(WebTestClient client, long id, String action) {
        String location = locationOf(client.post().uri("/items/{id}", id)
                .body(BodyInserters.fromFormData("action", action))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).isEqualTo("/items/" + id);
    }

    private static void expectCount(WebTestClient client, long id, String marker) {
        client.get().uri("/items/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains(marker));
    }

    private static String locationOf(WebTestClient.ResponseSpec response) {
        return response.returnResult(Void.class).getResponseHeaders().getLocation().toString();
    }

    private static String pathAndQuery(String location) {
        if (location.startsWith("http")) {
            URI uri = URI.create(location);
            String query = uri.getRawQuery();
            return uri.getRawPath() + (query == null ? "" : "?" + query);
        }
        return location;
    }
}
