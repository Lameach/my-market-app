package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository items;

    @Test
    void catalogOpensWithSeededProducts() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Яблоки Гала")))
                .andExpect(content().string(containsString("Страница: 1")))
                .andExpect(content().string(containsString("src=\"/images/apples.png\"")))
                .andExpect(content().string(containsString("name=\"pageNumber\" value=\"2\"")));

        mockMvc.perform(get("/items").param("search", "яблок"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Яблоки Гала")))
                .andExpect(content().string(not(containsString("Молоко"))));

        mockMvc.perform(get("/items").param("sort", "PRICE").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вода минеральная")));

        mockMvc.perform(get("/images/apples.png"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Заказ №"))));

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Купить"))));
    }

    @Test
    void countDoesNotDropBelowZero() throws Exception {
        Item apples = item("Яблоки Гала");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "MINUS").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">0<")));

        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "PLUS").session(session))
                .andExpect(content().string(containsString(">1<")));
        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "MINUS").session(session))
                .andExpect(content().string(containsString(">0<")));
        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "MINUS").session(session))
                .andExpect(content().string(containsString(">0<")));
    }

    @Test
    void buyingSnapshotsTheCartClearsItAndOpensTheOrder() throws Exception {
        Item apples = item("Яблоки Гала");
        Item milk = item("Молоко 3,2%");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/buy").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart/items"));

        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "PLUS").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/items/{id}", apples.getId()).param("action", "PLUS").session(session))
                .andExpect(status().isOk());
        String backToCatalog = mockMvc.perform(post("/items")
                        .param("id", milk.getId().toString())
                        .param("action", "PLUS")
                        .param("search", "моло")
                        .param("sort", "ALPHA")
                        .param("pageSize", "10")
                        .param("pageNumber", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(URLDecoder.decode(backToCatalog, StandardCharsets.UTF_8))
                .isEqualTo("/items?search=моло&sort=ALPHA&pageSize=10&pageNumber=1");

        String location = mockMvc.perform(post("/buy").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(location).startsWith("/orders/").endsWith("?newOrder=true");

        mockMvc.perform(get(location).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Поздравляем")))
                .andExpect(content().string(containsString("Яблоки Гала")))
                .andExpect(content().string(containsString("Молоко 3,2%")))
                .andExpect(content().string(containsString("2 шт.")))
                .andExpect(content().string(containsString("Сумма: " + (129 * 2 + 89))));

        String orderPath = location.substring(0, location.indexOf('?'));
        mockMvc.perform(get(orderPath).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Поздравляем"))));

        mockMvc.perform(get("/orders").session(session))
                .andExpect(content().string(containsString("Заказ №")))
                .andExpect(content().string(containsString("Яблоки Гала (2 шт.) " + (129 * 2) + " руб.")))
                .andExpect(content().string(containsString("Молоко 3,2% (1 шт.) 89 руб.")));

        mockMvc.perform(get("/cart/items").session(session))
                .andExpect(content().string(not(containsString("Купить"))))
                .andExpect(content().string(not(containsString("Яблоки Гала"))));

        mockMvc.perform(get("/orders").session(new MockHttpSession()))
                .andExpect(content().string(not(containsString("Заказ №"))));

        mockMvc.perform(get("/items").param("search", "яблок").session(session))
                .andExpect(content().string(containsString(">0<")));
    }

    private Item item(String title) {
        return items.findAll().stream()
                .filter(item -> item.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
