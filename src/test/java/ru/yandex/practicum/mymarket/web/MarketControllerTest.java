package ru.yandex.practicum.mymarket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;

@WebMvcTest
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private CartService cart;

    @MockitoBean
    private OrderService orders;

    @Test
    void catalogDefaultsAndBothEntryPoints() throws Exception {
        ItemDto apple = new ItemDto(1, "Яблоки Гала", "Сладкие", "images/apples.png", 129, 2);
        CatalogPage page = new CatalogPage(
                List.of(List.of(apple, apple, ItemDto.placeholder())),
                new Paging(5, 1, false, true),
                "",
                "NO");
        when(catalog.show(anyString(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(page);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attribute("items", page.items()))
                .andExpect(model().attribute("search", ""))
                .andExpect(model().attribute("sort", "NO"))
                .andExpect(model().attribute("paging", page.paging()))
                .andExpect(content().string(containsString("Страница: 1")))
                .andExpect(content().string(containsString("src=\"/images/apples.png\"")))
                .andExpect(content().string(containsString(">2<")));
        verify(catalog).show(eq(""), eq("NO"), eq(5), eq(1), anyString());

        mockMvc.perform(get("/items")
                        .param("search", "чай")
                        .param("sort", "PRICE")
                        .param("pageSize", "10")
                        .param("pageNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"));
        verify(catalog).show(eq("чай"), eq("PRICE"), eq(10), eq(2), anyString());
    }

    @Test
    void postingFromTheCatalogRedirectsWithTheSameParams() throws Exception {
        String location = mockMvc.perform(post("/items")
                        .param("id", "3")
                        .param("action", "PLUS")
                        .param("search", "чёрный чай")
                        .param("sort", "ALPHA")
                        .param("pageSize", "2")
                        .param("pageNumber", "4"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
                .isEqualTo("/items?search=чёрный чай&sort=ALPHA&pageSize=2&pageNumber=4");
        verify(cart).change(anyString(), eq(3L), eq(CartAction.PLUS));
    }

    @Test
    void itemPageRendersTheSameRecordAfterAChange() throws Exception {
        ItemDto item = new ItemDto(4, "Сыр", "300 г", "images/cheese.png", 349, 1);
        when(catalog.item(eq(4L), anyString())).thenReturn(item);

        mockMvc.perform(get("/items/4"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", item))
                .andExpect(content().string(containsString("Сыр")));

        mockMvc.perform(post("/items/4").param("action", "MINUS"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", item));
        verify(cart).change(anyString(), eq(4L), eq(CartAction.MINUS));
    }

    @Test
    void unknownCatalogActionIsRejected() throws Exception {
        when(catalog.item(eq(9L), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/items/4").param("action", "DELETE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/items/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyCartRendersAnEmptyListAndHidesBuy() throws Exception {
        when(cart.lines(anyString())).thenReturn(List.of());
        when(cart.total(anyString())).thenReturn(0);

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", List.of()))
                .andExpect(model().attribute("total", 0))
                .andExpect(content().string(not(containsString("Купить"))));
    }

    @Test
    void cartPostRendersTheCartAndShowsBuyWhenItHasItems() throws Exception {
        ItemDto milk = new ItemDto(2, "Молоко", "1 л", "images/milk.png", 89, 1);
        when(cart.lines(anyString())).thenReturn(List.of(milk));
        when(cart.total(anyString())).thenReturn(89);

        mockMvc.perform(post("/cart/items").param("id", "2").param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", List.of(milk)))
                .andExpect(model().attribute("total", 89))
                .andExpect(content().string(containsString("Купить")))
                .andExpect(content().string(containsString("Итого: 89 руб.")));
        verify(cart).change(anyString(), eq(2L), eq(CartAction.DELETE));
    }

    @Test
    void ordersAndNewOrderFlag() throws Exception {
        OrderItemDto line = new OrderItemDto(8, "Молоко", 89, 2);
        OrderDto order = OrderDto.of(5, List.of(line));
        when(orders.list(anyString())).thenReturn(List.of(order));
        when(orders.get(eq(5L), anyString())).thenReturn(order);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(containsString("Заказ №5")))
                .andExpect(content().string(containsString("Молоко (2 шт.) 178 руб.")))
                .andExpect(content().string(containsString("Сумма: 178 руб.")));

        mockMvc.perform(get("/orders/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(model().attribute("newOrder", false))
                .andExpect(content().string(not(containsString("Поздравляем"))));

        mockMvc.perform(get("/orders/5").param("newOrder", "true"))
                .andExpect(model().attribute("newOrder", true))
                .andExpect(content().string(containsString("Поздравляем")));
    }

    @Test
    void buyRedirectsToTheNewOrder() throws Exception {
        when(orders.buy(anyString())).thenReturn(Optional.of(7L));
        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/7?newOrder=true"));

        when(orders.buy(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/buy"))
                .andExpect(redirectedUrl("/cart/items"));

        when(orders.get(eq(4L), anyString())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mockMvc.perform(get("/orders/4")).andExpect(status().isNotFound());
    }
}
