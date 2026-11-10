package ru.yandex.practicum.mymarket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentGateway;

@WebFluxTest
class MarketControllerTest {

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

    @BeforeEach
    void emptyPublishers() {
        lenient().when(cart.change(anyString(), anyLong(), any())).thenReturn(Mono.empty());
        lenient().when(cart.lines(anyString())).thenReturn(Mono.just(List.of()));
        lenient().when(orders.list(anyString())).thenReturn(Mono.just(List.of()));
        lenient().when(orders.buy(anyString())).thenReturn(Mono.empty());
        lenient().when(payments.balance()).thenReturn(Mono.just(1_000_000L));
        lenient().when(payments.pay(anyLong())).thenReturn(Mono.empty());
    }

    @Test
    void catalogDefaultsAndBothEntryPoints() {
        ItemDto apple = new ItemDto(1, "Яблоки Гала", "Сладкие", "images/apples.png", 129, 2);
        CatalogPage page = new CatalogPage(List.of(apple), new Paging(5, 1, false, true), "", "NO");
        when(catalog.show(anyString(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(Mono.just(page));

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Страница: 1");
                    assertThat(body).contains("src=\"/images/apples.png\"");
                    assertThat(body).contains(">2<");
                    assertThat(body).contains("<div>&nbsp;</div>");
                });
        verify(catalog).show(eq(""), eq("NO"), eq(5), eq(1), anyString());

        when(catalog.show(eq("чай"), eq("PRICE"), eq(10), eq(2), anyString()))
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
        verify(catalog).show(eq("чай"), eq("PRICE"), eq(10), eq(2), anyString());
    }

    @Test
    void postingFromTheCatalogRedirectsWithTheSameParams() {
        String location = locationOf(webTestClient.post()
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
        verify(cart).change(anyString(), eq(3L), eq(CartAction.PLUS));
    }

    @Test
    void itemPageRendersTheSameRecordAfterAChange() {
        ItemDto item = new ItemDto(4, "Сыр", "300 г", "images/cheese.png", 349, 1);
        when(catalog.item(eq(4L), anyString())).thenReturn(Mono.just(item));

        webTestClient.get().uri("/items/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Сыр"));

        String location = locationOf(webTestClient.post().uri("/items/4")
                .body(BodyInserters.fromFormData("action", "MINUS"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).isEqualTo("/items/4");

        webTestClient.get().uri("/items/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Сыр"));
        verify(cart).change(anyString(), eq(4L), eq(CartAction.MINUS));
    }

    @Test
    void unknownCatalogActionIsRejected() {
        when(catalog.item(eq(9L), anyString()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        webTestClient.post().uri("/items/4")
                .body(BodyInserters.fromFormData("action", "DELETE"))
                .exchange()
                .expectStatus().isBadRequest();
        webTestClient.get().uri("/items/9")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void emptyCartRendersAnEmptyListAndHidesBuy() {
        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("Купить"));
    }

    @Test
    void cartPostRedirectsSoRefreshDoesNotRepeatTheAction() {
        String location = locationOf(webTestClient.post()
                .uri("/cart/items")
                .body(BodyInserters.fromFormData("id", "2")
                        .with("action", "DELETE"))
                .exchange()
                .expectStatus().is3xxRedirection());
        assertThat(pathAndQuery(location)).isEqualTo("/cart/items");
        verify(cart).change(anyString(), eq(2L), eq(CartAction.DELETE));
    }

    @Test
    void cartWithItemsShowsBuy() {
        ItemDto milk = new ItemDto(2, "Молоко", "1 л", "images/milk.png", 89, 1);
        when(cart.lines(anyString())).thenReturn(Mono.just(List.of(milk)));

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Купить");
                    assertThat(body).contains("Итого: 89 руб.");
                });
    }

    @Test
    void ordersAndNewOrderFlag() {
        OrderItemDto line = new OrderItemDto(8, "Молоко", "images/milk.png", 89, 2);
        OrderDto order = OrderDto.of(5, List.of(line));
        when(orders.list(anyString())).thenReturn(Mono.just(List.of(order)));
        when(orders.get(eq(5L), anyString())).thenReturn(Mono.just(order));

        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Заказ №5");
                    assertThat(body).contains("Молоко (2 шт.) 178 руб.");
                    assertThat(body).contains("Сумма: 178 руб.");
                });

        webTestClient.get().uri("/orders/5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).doesNotContain("Поздравляем");
                    assertThat(body).contains("src=\"/images/milk.png\"");
                    assertThat(body).contains("Молоко");
                    assertThat(body).contains("89 руб.");
                });

        webTestClient.get().uri(builder -> builder.path("/orders/5").queryParam("newOrder", "true").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Поздравляем"));
    }

    @Test
    void buyRedirectsToTheNewOrder() {
        when(orders.buy(anyString())).thenReturn(Mono.just(7L));
        assertThat(pathAndQuery(locationOf(webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/orders/7?newOrder=true");

        when(orders.buy(anyString())).thenReturn(Mono.empty());
        assertThat(pathAndQuery(locationOf(webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection())))
                .isEqualTo("/cart/items");

        when(orders.get(eq(4L), anyString()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
        webTestClient.get().uri("/orders/4")
                .exchange()
                .expectStatus().isNotFound();
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
