package ru.yandex.practicum.mymarket;

import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    @DynamicPropertySource
    static void paymentService(DynamicPropertyRegistry registry) {
        registry.add("payment.base-url", StorefrontSpringTest::paymentBaseUrl);
    }

    @BeforeEach
    void paymentServiceAcceptsOrders() {
        acceptPayments();
    }

    static void acceptPayments() {
        payments.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
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

    static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String paymentBaseUrl() {
        String url = payments.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
