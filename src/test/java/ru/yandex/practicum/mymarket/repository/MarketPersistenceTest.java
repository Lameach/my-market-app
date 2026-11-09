package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;

@DataJpaTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CatalogService.class, CartService.class, OrderService.class})
class MarketPersistenceTest {

    @Autowired
    private ItemRepository items;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private CartService cart;

    @Autowired
    private OrderService orders;

    @Autowired
    private EntityManager entityManager;

    @Test
    void searchMatchesTitleOrDescriptionAndSorts() {
        Item bread = save("Хлеб", "ржаной", 64);
        Item milk = save("Молоко", "пастеризованное", 89);
        save("Вода", "негазированная", 55);

        CatalogPage byTitle = catalog.show("МОЛО", "NO", 5, 1, "s");
        assertThat(byTitle.items()).extracting(ItemDto::title).containsExactly("Молоко");

        CatalogPage byDescription = catalog.show("ржаной", "ALPHA", 5, 1, "s");
        assertThat(byDescription.items()).extracting(ItemDto::id).containsExactly(bread.getId());

        CatalogPage byPrice = catalog.show("", "PRICE", 10, 1, "s");
        assertThat(byPrice.items()).extracting(ItemDto::title).containsExactly("Вода", "Хлеб", "Молоко");

        CatalogPage byAlpha = catalog.show("  ", "alpha", 10, 1, "s");
        assertThat(byAlpha.search()).isEmpty();
        assertThat(byAlpha.sort()).isEqualTo("ALPHA");
        assertThat(byAlpha.items()).extracting(ItemDto::title).containsExactly("Вода", "Молоко", "Хлеб");

        assertThat(byPrice.items()).allSatisfy(item -> assertThat(item.imgPath()).doesNotStartWith("/"));
        assertThat(milk.getId()).isNotNull();
    }

    @Test
    void wildcardInTheSearchIsLiteral() {
        save("100%", "скидка", 10);
        save("Молоко", "обычное", 20);

        assertThat(catalog.show("%", "NO", 5, 1, "s").items())
                .extracting(ItemDto::title)
                .containsExactly("100%");
        assertThat(catalog.show("_", "NO", 5, 1, "s").items()).isEmpty();
    }

    @Test
    void pagingFollowsPageSizeAndNumber() {
        save("Один", "a", 1);
        save("Два", "b", 2);
        save("Три", "c", 3);
        save("Четыре", "d", 4);

        CatalogPage page = catalog.show("", "NO", 5, 1, "s");
        assertThat(page.paging().pageSize()).isEqualTo(5);
        assertThat(page.paging().pageNumber()).isEqualTo(1);
        assertThat(page.paging().hasPrevious()).isFalse();
        assertThat(page.paging().hasNext()).isFalse();
        assertThat(page.items()).extracting(ItemDto::title).containsExactly("Один", "Два", "Три", "Четыре");

        CatalogPage second = catalog.show("", "NO", 3, 2, "s");
        assertThat(second.paging().hasPrevious()).isTrue();
        assertThat(second.paging().hasNext()).isFalse();
        assertThat(second.items()).extracting(ItemDto::title).containsExactly("Четыре");
    }

    @Test
    void cartCountNeverDropsBelowZeroAndStaysInsideTheSession() {
        Item apples = save("Яблоки", "сладкие", 129);
        Item milk = save("Молоко", "литр", 89);

        cart.change("one", apples.getId(), CartAction.MINUS);
        assertThat(cart.lines("one")).isEmpty();
        assertThat(cart.total("one")).isZero();

        cart.change("one", apples.getId(), CartAction.PLUS);
        cart.change("one", apples.getId(), CartAction.PLUS);
        cart.change("one", milk.getId(), CartAction.PLUS);
        assertThat(cart.total("one")).isEqualTo(129 * 2 + 89);
        assertThat(catalog.item(apples.getId(), "one").count()).isEqualTo(2);
        assertThat(catalog.item(apples.getId(), "two").count()).isZero();

        cart.change("one", apples.getId(), CartAction.MINUS);
        cart.change("one", milk.getId(), CartAction.DELETE);
        cart.change("one", apples.getId(), CartAction.MINUS);
        cart.change("one", apples.getId(), CartAction.MINUS);
        entityManager.flush();
        entityManager.clear();
        assertThat(cart.lines("one")).isEmpty();
        assertThat(cart.lines("two")).isEmpty();

        assertThatThrownBy(() -> cart.change("one", 999, CartAction.PLUS))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buySnapshotsPricesAndClearsTheCart() {
        Item apples = save("Яблоки", "сладкие", 129);
        save("Молоко", "литр", 89);
        long milkId = items.findAll().stream()
                .filter(item -> item.getTitle().equals("Молоко"))
                .findFirst()
                .orElseThrow()
                .getId();

        assertThat(orders.buy("buyer")).isEmpty();

        cart.change("buyer", apples.getId(), CartAction.PLUS);
        cart.change("buyer", apples.getId(), CartAction.PLUS);
        cart.change("buyer", milkId, CartAction.PLUS);

        long orderId = orders.buy("buyer").orElseThrow();
        apples.setPrice(1);
        items.save(apples);
        entityManager.flush();
        entityManager.clear();

        OrderDto order = orders.get(orderId, "buyer");
        assertThat(order.items()).extracting(item -> item.title()).containsExactly("Яблоки", "Молоко");
        assertThat(order.items()).extracting(item -> item.count()).containsExactly(2, 1);
        assertThat(order.items().get(0).price()).isEqualTo(129);
        assertThat(order.totalSum()).isEqualTo(129 * 2 + 89);
        assertThat(cart.lines("buyer")).isEmpty();
        assertThat(orders.buy("buyer")).isEmpty();
        assertThat(orders.list("buyer")).extracting(OrderDto::id).containsExactly(orderId);
        assertThat(orders.list("other")).isEmpty();
        assertThatThrownBy(() -> orders.get(orderId, "other")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void blankTitleIsRejected() {
        Item item = new Item("  ", "описание", "images/x.png", 10);
        assertThatThrownBy(() -> items.saveAndFlush(item)).isInstanceOf(ConstraintViolationException.class);
    }

    private Item save(String title, String description, int price) {
        return items.save(new Item(title, description, "images/" + title + ".png", price));
    }
}
