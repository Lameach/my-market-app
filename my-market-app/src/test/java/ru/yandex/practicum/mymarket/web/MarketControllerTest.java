package ru.yandex.practicum.mymarket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.config.SecurityConfig;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.security.Buyer;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.service.PaymentGateway;
import ru.yandex.practicum.mymarket.service.PaymentRejectedException;

@WebFluxTest
@Import(SecurityConfig.class)
class MarketControllerTest {

    private static final long BUYER_ID = 7L;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private CartService cart;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private PaymentGateway payments;

    @MockitoBean
    private ReactiveUserDetailsService buyers;

    @BeforeEach
    void emptyPublishers() {
        lenient().when(cart.change(anyLong(), anyLong(), any())).thenReturn(Mono.empty());
        lenient().when(cart.lines(anyLong())).thenReturn(Mono.just(List.of()));
        lenient().when(orders.list(anyLong())).thenReturn(Mono.just(List.of()));
        lenient().when(orders.buy(anyLong())).thenReturn(Mono.empty());
        lenient().when(payments.balance(anyLong())).thenReturn(Mono.just(1_000_000L));
        lenient().when(payments.pay(anyLong(), anyLong())).thenReturn(Mono.empty());
    }

    @Test
    void catalogDefaultsAndBothEntryPoints() {
        ItemDto apple = new ItemDto(1, "Яблоки Гала", "Сладкие", "images/apples.png", 129, 2);
        CatalogPage page = new CatalogPage(List.of(apple), new Paging(5, 1, false, true), "", "NO");
        when(catalog.show(any(), any(), anyInt(), anyInt(), nullable(Long.class))).thenReturn(Mono.just(page));

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Страница: 1");
                    assertThat(body).contains("src=\"/images/apples.png\"");
                    assertThat(body).contains("Войти");
                    assertThat(body).doesNotContain("Корзина");
                    assertThat(body).doesNotContain("name=\"action\"");
                    assertThat(body).contains("<div>&nbsp;</div>");
                });
        verify(catalog).show(eq(""), eq("NO"), eq(5), eq(1), isNull());

        buyer().get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains(">2<");
                    assertThat(body).contains("Корзина");
                    assertThat(body).contains("Заказы");
                });

        when(catalog.show(eq("чай"), eq("PRICE"), eq(10), eq(2), nullable(Long.class)))
                .thenReturn(Mono.just(new CatalogPage(List.of(), new Paging(10, 2, true, false), "чай", "PRICE")));
        webTestClient.get()
                .uri(builder -> builder.path("/items")
                        .queryParam("search", "чай")
                        .queryParam("sort", "PRICE")
                        .queryParam("pageSize", "10")
                        .queryParam("pageNumber", "2")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Страница: 2"));
        verify(catalog).show(eq("чай"), eq("PRICE"), eq(10), eq(2), isNull());
    }

    @Test
    void postingFromTheCatalogRedirectsWithTheSameParams() {
        String location = locationOf(buyer().post()
                .uri("/items")
                .body(BodyInserters.fromFormData("id", "3")
                        .with("action", "PLUS")
                        .with("search", "чёрный чай")
                        .with("sort", "ALPHA")
                        .with("pageSize", "2")
                        .with("pageNumber", "4"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(URLDecoder.decode(pathAndQuery(location), StandardCharsets.UTF_8))
                .isEqualTo("/items?search=чёрный чай&sort=ALPHA&pageSize=2&pageNumber=4");
        verify(cart).change(eq(BUYER_ID), eq(3L), eq(CartAction.PLUS));
    }

    @Test
    void itemPageRendersTheSameRecordAfterAChange() {
        ItemDto item = new ItemDto(4, "Сыр", "300 г", "images/cheese.png", 349, 1);
        when(catalog.item(eq(4L), nullable(Long.class))).thenReturn(Mono.just(item));

        webTestClient.get().uri("/items/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Сыр");
                    assertThat(body).doesNotContain("name=\"action\"");
                });

        String location = locationOf(buyer().post().uri("/items/4")
                .body(BodyInserters.fromFormData("action", "MINUS"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).isEqualTo("/items/4");

        buyer().get().uri("/items/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Сыр");
                    assertThat(body).contains("name=\"action\"");
                });
        verify(cart).change(eq(BUYER_ID), eq(4L), eq(CartAction.MINUS));
    }

    @Test
    void unknownCatalogActionIsRejected() {
        when(catalog.item(eq(9L), nullable(Long.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        buyer().post().uri("/items/4")
                .body(BodyInserters.fromFormData("action", "DELETE"))
                .exchange()
                .expectStatus().isBadRequest();
        webTestClient.get().uri("/items/9")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void anonymousBuyerIsSentToLogin() {
        WebTestClient posting = webTestClient.mutateWith(csrf());
        expectLogin(webTestClient.get().uri("/cart/items"));
        expectLogin(webTestClient.get().uri("/orders"));
        expectLogin(webTestClient.get().uri("/orders/5"));
        expectLogin(posting.post().uri("/items").body(BodyInserters.fromFormData("id", "1").with("action", "PLUS")));
        expectLogin(posting.post().uri("/buy"));
    }

    @Test
    void emptyCartRendersAnEmptyListAndHidesBuy() {
        buyer().get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Купить"));
    }

    @Test
    void cartPostRedirectsSoRefreshDoesNotRepeatTheAction() {
        String location = locationOf(buyer().post()
                .uri("/cart/items")
                .body(BodyInserters.fromFormData("id", "2")
                        .with("action", "DELETE"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).isEqualTo("/cart/items");
        verify(cart).change(eq(BUYER_ID), eq(2L), eq(CartAction.DELETE));
    }

    @Test
    void cartWithItemsShowsBuy() {
        ItemDto milk = new ItemDto(2, "Молоко", "1 л", "images/milk.png", 89, 1);
        when(cart.lines(anyLong())).thenReturn(Mono.just(List.of(milk)));

        buyer().get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Купить");
                    assertThat(body).contains("Итого: 89 руб.");
                    assertThat(body).doesNotContain("disabled");
                });
    }

    @Test
    void cartDisablesBuyWhenTheBalanceIsTooSmallOrPaymentsAreDown() {
        ItemDto milk = new ItemDto(2, "Молоко", "1 л", "images/milk.png", 89, 1);
        when(cart.lines(anyLong())).thenReturn(Mono.just(List.of(milk)));
        when(payments.balance(anyLong())).thenReturn(Mono.just(10L));

        buyer().get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Недостаточно средств для оплаты заказа");
                    assertThat(body).contains("disabled=\"disabled\"");
                });

        when(payments.balance(anyLong())).thenReturn(Mono.error(new PaymentRejectedException(new IllegalStateException("down"))));
        buyer().get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Сервис платежей недоступен"));

        when(payments.balance(anyLong())).thenReturn(Mono.error(new PaymentAuthorizationException(new IllegalStateException("denied"))));
        buyer().get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Сервис платежей отклонил авторизацию клиента"));
    }

    @Test
    void ordersAndNewOrderFlag() {
        OrderItemDto line = new OrderItemDto(8, "Молоко", "images/milk.png", 89, 2);
        OrderDto order = OrderDto.of(5, List.of(line));
        when(orders.list(anyLong())).thenReturn(Mono.just(List.of(order)));
        when(orders.get(eq(5L), anyLong())).thenReturn(Mono.just(order));

        buyer().get().uri("/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Заказ №5");
                    assertThat(body).contains("Молоко (2 шт.) 178 руб.");
                    assertThat(body).contains("Сумма: 178 руб.");
                });

        buyer().get().uri("/orders/5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).doesNotContain("Поздравляем");
                    assertThat(body).contains("src=\"/images/milk.png\"");
                    assertThat(body).contains("Молоко");
                    assertThat(body).contains("89 руб.");
                });

        buyer().get().uri(builder -> builder.path("/orders/5").queryParam("newOrder", "true").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Поздравляем"));
    }

    @Test
    void buyRedirectsToTheNewOrder() {
        when(orders.buy(anyLong())).thenReturn(Mono.just(7L));
        assertThat(pathAndQuery(locationOf(buyer().post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/orders/7?newOrder=true");

        when(orders.buy(anyLong())).thenReturn(Mono.empty());
        assertThat(pathAndQuery(locationOf(buyer().post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");

        when(orders.buy(anyLong())).thenReturn(Mono.error(new PaymentRejectedException(new IllegalStateException("down"))));
        assertThat(pathAndQuery(locationOf(buyer().post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");

        when(orders.get(eq(4L), anyLong()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
        buyer().get().uri("/orders/4")
                .exchange()
                .expectStatus().isNotFound();
    }

    private WebTestClient buyer() {
        Buyer buyer = new Buyer(BUYER_ID, "buyer", "secret", true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                buyer, buyer.getPassword(), buyer.getAuthorities());
        return webTestClient.mutateWith(mockAuthentication(authentication)).mutateWith(csrf());
    }

    private static void expectLogin(WebTestClient.RequestHeadersSpec<?> request) {
        request.exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/login"));
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
