package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

class BuyerAccessTest extends StorefrontSpringTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ItemRepository items;

    @Test
    void anonymousVisitorSeesTheCatalogAndItemWithoutCartControls() {
        Item apples = item("Яблоки Гала");

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Яблоки Гала");
                    assertThat(body).contains("Войти");
                    assertThat(body).doesNotContain("Корзина");
                    assertThat(body).doesNotContain("Заказы");
                    assertThat(body).doesNotContain("name=\"action\"");
                });

        webTestClient.get().uri("/items/{id}", apples.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Яблоки Гала");
                    assertThat(body).doesNotContain("name=\"action\"");
                    assertThat(body).doesNotContain("Корзина");
                });
    }

    @Test
    void anonymousPostsAndPrivatePagesGoToLogin() {
        Item apples = item("Яблоки Гала");
        WebTestClient posting = webTestClient.mutateWith(csrf());

        expectLogin(webTestClient.get().uri("/cart/items"));
        expectLogin(webTestClient.get().uri("/orders"));
        expectLogin(posting.post().uri("/items/{id}", apples.getId())
                .body(BodyInserters.fromFormData("action", "PLUS")));
        expectLogin(posting.post().uri("/buy"));
    }

    @Test
    void wrongPasswordStaysOnTheLoginPage() {
        webTestClient.mutateWith(csrf()).post().uri("/login")
                .body(BodyInserters.fromFormData("username", "buyer").with("password", "nope"))
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).contains("error"));
    }

    @Test
    void logoutDropsTheSessionCookieAndTheCart() {
        WebTestClient buyer = login(webTestClient, "buyer", "buyer");
        buyer.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk();

        List<ResponseCookie> cookies = buyer.post().uri("/logout")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).contains("logout"))
                .returnResult(Void.class)
                .getResponseCookies()
                .get("SESSION");
        assertThat(cookies).isNotEmpty();
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie.getMaxAge()).isZero());

        buyer.get().uri("/cart/items")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/login"));
    }

    @Test
    void eachBuyerKeepsASeparateCartAndOrderList() {
        Item apples = item("Яблоки Гала");
        WebTestClient buyer = login(webTestClient, "buyer", "buyer");
        WebTestClient second = login(webTestClient, "second", "second");

        buyer.post().uri("/items/{id}", apples.getId())
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().is3xxRedirection();

        buyer.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Яблоки Гала"));
        second.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Яблоки Гала"));

        String location = buyer.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation()
                .toString();
        String orderPath = pathOf(location);

        buyer.get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Заказ №"));
        second.get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Заказ №"));
        second.get().uri(orderPath)
                .exchange()
                .expectStatus().isNotFound();
    }

    private static void expectLogin(WebTestClient.RequestHeadersSpec<?> request) {
        request.exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/login"));
    }

    private Item item(String title) {
        Item found = items.findAll()
                .filter(row -> row.getTitle().equals(title))
                .blockFirst(TIMEOUT);
        assertThat(found).isNotNull();
        return found;
    }

    private static String pathOf(String location) {
        String withoutQuery = location.contains("?") ? location.substring(0, location.indexOf('?')) : location;
        if (!withoutQuery.startsWith("http")) {
            return withoutQuery;
        }
        java.net.URI uri = java.net.URI.create(withoutQuery);
        return uri.getRawPath();
    }
}
