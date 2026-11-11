package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@SpringBootTest
@AutoConfigureWebTestClient
abstract class StorefrontSpringTest {

    static final MockWebServer payments = new MockWebServer();

    static {
        acceptPayments();
        try {
            payments.start();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private OrderItemRepository orderItems;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private CartItemRepository cartItems;

    @Autowired
    private CartRepository carts;

    @DynamicPropertySource
    static void paymentService(DynamicPropertyRegistry registry) {
        registry.add("payment.base-url", StorefrontSpringTest::paymentBaseUrl);
        registry.add("spring.security.oauth2.client.provider.auth.token-uri",
                () -> paymentBaseUrl() + "/oauth2/token");
        registry.add("spring.data.redis.database", () -> "1");
    }

    @BeforeEach
    void paymentServiceAcceptsOrders() {
        acceptPayments();
        drainPayments();
        orderItems.deleteAll()
                .then(orders.deleteAll())
                .then(cartItems.deleteAll())
                .then(carts.deleteAll())
                .block(Duration.ofSeconds(5));
        List<String> keys = redis.keys("market:*").collectList().block(Duration.ofSeconds(5));
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys.toArray(String[]::new)).block(Duration.ofSeconds(5));
        }
    }

    static void acceptPayments() {
        payments.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/oauth2/token")) {
                    return accessToken();
                }
                if (path.startsWith("/balance")) {
                    return json(200, "{\"amount\":100000}");
                }
                if (path.startsWith("/payments")) {
                    return json(200, "{\"success\":true,\"balance\":100000}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    static MockResponse accessToken() {
        return json(200, "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    static void drainPayments() {
        try {
            while (payments.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    static RecordedRequest takePayment() {
        try {
            RecordedRequest payment = null;
            for (int i = 0; i < 30; i++) {
                RecordedRequest request = payments.takeRequest(200, TimeUnit.MILLISECONDS);
                if (request == null) {
                    break;
                }
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/payments")) {
                    payment = request;
                }
            }
            return payment;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    protected WebTestClient login(WebTestClient base, String username, String password) {
        WebTestClient posting = base.mutateWith(csrf());
        List<ResponseCookie> cookies = posting.post().uri("/login")
                .body(BodyInserters.fromFormData("username", username).with("password", password))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location -> assertThat(location).doesNotContain("error"))
                .returnResult(Void.class)
                .getResponseCookies()
                .get("SESSION");
        assertThat(cookies).isNotEmpty();
        ResponseCookie cookie = cookies.stream()
                .filter(candidate -> candidate.getValue() != null && !candidate.getValue().isBlank())
                .filter(candidate -> candidate.getMaxAge().isNegative() || !candidate.getMaxAge().isZero())
                .reduce((ignored, latest) -> latest)
                .orElseThrow();
        return posting.mutate().defaultCookie("SESSION", cookie.getValue()).build();
    }

    private static String paymentBaseUrl() {
        String url = payments.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
