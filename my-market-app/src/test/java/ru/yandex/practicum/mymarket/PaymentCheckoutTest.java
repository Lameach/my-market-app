package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

class PaymentCheckoutTest extends StorefrontSpringTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ItemRepository items;

    @Test
    void buyStaysDisabledWhenTheBalanceDoesNotCoverTheCart() {
        payments.setDispatcher(balanceThenPay("{\"amount\":1}", 402, "{\"message\":\"Недостаточно средств\"}"));
        Item apples = item("Яблоки Гала");
        WebTestClient client = browser();
        postItem(client, apples.getId());

        client.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Недостаточно средств для оплаты заказа");
                    assertThat(body).contains("disabled=\"disabled\"");
                });

        assertThat(pathAndQuery(locationOf(client.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");
        client.get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Заказ №"));
    }

    @Test
    void buyStaysDisabledWhenThePaymentServiceIsDown() {
        payments.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
            }
        });
        Item apples = item("Яблоки Гала");
        WebTestClient client = browser();
        postItem(client, apples.getId());

        client.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Сервис платежей недоступен");
                    assertThat(body).contains("disabled=\"disabled\"");
                });

        assertThat(pathAndQuery(locationOf(client.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");
        client.get().uri("/orders")
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Заказ №"));
    }

    private static Dispatcher balanceThenPay(String balance, int payStatus, String payBody) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/balance")) {
                    return json(200, balance);
                }
                if (path.startsWith("/payments")) {
                    return json(payStatus, payBody);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private WebTestClient browser() {
        ResponseCookie cookie = webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseCookies()
                .getFirst("SESSION");
        assertThat(cookie).isNotNull();
        return webTestClient.mutate().defaultCookie("SESSION", cookie.getValue()).build();
    }

    private Item item(String title) {
        Item found = items.findAll()
                .filter(row -> row.getTitle().equals(title))
                .blockFirst(TIMEOUT);
        assertThat(found).isNotNull();
        return found;
    }

    private static void postItem(WebTestClient client, long id) {
        client.post().uri("/items/{id}", id)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().is3xxRedirection();
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
