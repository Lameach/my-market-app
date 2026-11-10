package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.config.ValidateBeforeConvert;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;

@DataR2dbcTest(properties = {
        "spring.r2dbc.url=r2dbc:h2:mem:///market-test?options=DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:no-seed.sql"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        CatalogService.class,
        CartService.class,
        OrderService.class,
        ValidateBeforeConvert.class,
        ValidationAutoConfiguration.class,
        MarketPersistenceTest.FailSecondOrderItem.class
})
class MarketPersistenceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ItemRepository items;

    @Autowired
    private CartRepository carts;

    @Autowired
    private CartItemRepository cartItems;

    @Autowired
    private OrderRepository ordersRepo;

    @Autowired
    private OrderItemRepository orderItems;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private CartService cart;

    @Autowired
    private OrderService orders;

    @Autowired
    private FailSecondOrderItem orderItemGate;

    @BeforeEach
    void clean() {
        orderItemGate.reset();
        orderItems.deleteAll()
                .then(ordersRepo.deleteAll())
                .then(cartItems.deleteAll())
                .then(carts.deleteAll())
                .then(items.deleteAll())
                .block(TIMEOUT);
    }

    @Test
    void searchMatchesTitleOrDescriptionAndSorts() {
        Item bread = save("Хлеб", "ржаной", 64);
        Item milk = save("Молоко", "пастеризованное", 89);
        save("Вода", "негазированная", 55);

        CatalogPage byTitle = await(catalog.show("МОЛО", "NO", 5, 1, "s"));
        assertThat(byTitle.items()).extracting(ItemDto::title).containsExactly("Молоко");

        CatalogPage byDescription = await(catalog.show("ржаной", "ALPHA", 5, 1, "s"));
        assertThat(byDescription.items()).extracting(ItemDto::id).containsExactly(bread.getId());

        CatalogPage byPrice = await(catalog.show("", "PRICE", 10, 1, "s"));
        assertThat(byPrice.items()).extracting(ItemDto::title).containsExactly("Вода", "Хлеб", "Молоко");

        CatalogPage byAlpha = await(catalog.show("  ", "alpha", 10, 1, "s"));
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

        assertThat(await(catalog.show("%", "NO", 5, 1, "s")).items())
                .extracting(ItemDto::title)
                .containsExactly("100%");
        assertThat(await(catalog.show("_", "NO", 5, 1, "s")).items()).isEmpty();
    }

    @Test
    void pagingFollowsPageSizeAndNumber() {
        save("Один", "a", 1);
        save("Два", "b", 2);
        save("Три", "c", 3);
        save("Четыре", "d", 4);

        CatalogPage page = await(catalog.show("", "NO", 5, 1, "s"));
        assertThat(page.paging().pageSize()).isEqualTo(5);
        assertThat(page.paging().pageNumber()).isEqualTo(1);
        assertThat(page.paging().hasPrevious()).isFalse();
        assertThat(page.paging().hasNext()).isFalse();
        assertThat(page.items()).extracting(ItemDto::title).containsExactly("Один", "Два", "Три", "Четыре");

        CatalogPage second = await(catalog.show("", "NO", 3, 2, "s"));
        assertThat(second.paging().hasPrevious()).isTrue();
        assertThat(second.paging().hasNext()).isFalse();
        assertThat(second.items()).extracting(ItemDto::title).containsExactly("Четыре");
    }

    @Test
    void cartCountNeverDropsBelowZeroAndStaysInsideTheSession() {
        Item apples = save("Яблоки", "сладкие", 129);
        Item milk = save("Молоко", "литр", 89);

        await(cart.change("one", apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines("one"))).isEmpty();
        assertThat(sum("one")).isZero();

        await(cart.change("one", apples.getId(), CartAction.PLUS));
        await(cart.change("one", apples.getId(), CartAction.PLUS));
        await(cart.change("one", milk.getId(), CartAction.PLUS));
        assertThat(sum("one")).isEqualTo(129 * 2 + 89);
        assertThat(await(catalog.item(apples.getId(), "one")).count()).isEqualTo(2);
        assertThat(await(catalog.item(apples.getId(), "two")).count()).isZero();

        await(cart.change("one", apples.getId(), CartAction.MINUS));
        await(cart.change("one", milk.getId(), CartAction.DELETE));
        await(cart.change("one", apples.getId(), CartAction.MINUS));
        await(cart.change("one", apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines("one"))).isEmpty();
        assertThat(await(cart.lines("two"))).isEmpty();

        assertThatThrownBy(() -> await(cart.change("one", 999, CartAction.PLUS)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buySnapshotsPricesAndClearsTheCart() {
        Item apples = save("Яблоки", "сладкие", 129);
        save("Молоко", "литр", 89);
        long milkId = idOf("Молоко");

        assertThat(await(orders.buy("buyer"))).isNull();

        await(cart.change("buyer", apples.getId(), CartAction.PLUS));
        await(cart.change("buyer", apples.getId(), CartAction.PLUS));
        await(cart.change("buyer", milkId, CartAction.PLUS));

        long orderId = await(orders.buy("buyer"));
        apples.setPrice(1);
        await(items.save(apples));

        OrderDto order = await(orders.get(orderId, "buyer"));
        assertThat(order.items()).extracting(item -> item.title()).containsExactly("Яблоки", "Молоко");
        assertThat(order.items()).extracting(item -> item.count()).containsExactly(2, 1);
        assertThat(order.items().get(0).price()).isEqualTo(129);
        assertThat(order.items().get(0).imgPath()).isEqualTo("images/Яблоки.png");
        assertThat(order.totalSum()).isEqualTo(129L * 2 + 89);
        assertThat(await(cart.lines("buyer"))).isEmpty();
        assertThat(await(orders.buy("buyer"))).isNull();
        assertThat(await(orders.list("buyer"))).extracting(OrderDto::id).containsExactly(orderId);
        assertThat(await(orders.list("other"))).isEmpty();
        assertThatThrownBy(() -> await(orders.get(orderId, "other"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void twoConcurrentPlusOnAnExistingLineBothCount() {
        Item apples = save("Яблоки", "сладкие", 129);
        await(cart.change("one", apples.getId(), CartAction.PLUS));

        Mono.when(
                        cart.change("one", apples.getId(), CartAction.PLUS),
                        cart.change("one", apples.getId(), CartAction.PLUS))
                .block(TIMEOUT);

        assertThat(await(cartItems.count())).isEqualTo(1);
        assertThat(await(cart.lines("one"))).extracting(ItemDto::count).containsExactly(3);

        await(cart.change("one", apples.getId(), CartAction.PLUS));
        assertThat(await(cart.lines("one"))).extracting(ItemDto::count).containsExactly(4);
    }

    @Test
    void twoConcurrentFirstPlusCreateOneLine() {
        Item apples = save("Яблоки", "сладкие", 129);

        Mono.when(
                        cart.change("fresh", apples.getId(), CartAction.PLUS),
                        cart.change("fresh", apples.getId(), CartAction.PLUS))
                .block(TIMEOUT);

        assertThat(await(carts.count())).isEqualTo(1);
        assertThat(await(cartItems.count())).isEqualTo(1);
        assertThat(await(cart.lines("fresh"))).extracting(ItemDto::count).containsExactly(2);

        await(cart.change("fresh", apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines("fresh"))).extracting(ItemDto::count).containsExactly(1);
    }

    @Test
    void twoConcurrentBuysCreateOneOrder() {
        Item apples = save("Яблоки", "сладкие", 129);
        Item milk = save("Молоко", "литр", 89);
        await(cart.change("buyer", apples.getId(), CartAction.PLUS));
        await(cart.change("buyer", apples.getId(), CartAction.PLUS));
        await(cart.change("buyer", milk.getId(), CartAction.PLUS));

        Mono.when(orders.buy("buyer"), orders.buy("buyer")).block(TIMEOUT);

        assertThat(await(ordersRepo.count())).isEqualTo(1);
        assertThat(await(orders.list("buyer"))).singleElement().satisfies(order -> assertThat(order.items())
                .extracting(OrderItemDto::title, OrderItemDto::count)
                .containsExactly(tuple("Яблоки", 2), tuple("Молоко", 1)));
        assertThat(await(cart.lines("buyer"))).isEmpty();
    }

    @Test
    void buyRollsBackWhenTheSecondLineIsNotSaved() {
        Item first = save("Товар 1", "a", 10);
        Item second = save("Товар 2", "b", 20);
        await(cart.change("buyer", first.getId(), CartAction.PLUS));
        await(cart.change("buyer", second.getId(), CartAction.PLUS));
        await(cart.change("buyer", second.getId(), CartAction.PLUS));
        orderItemGate.failOnSecondSave();

        assertThatThrownBy(() -> await(orders.buy("buyer")))
                .satisfies(thrown -> assertThat(NestedExceptionUtils.getMostSpecificCause(thrown))
                        .isInstanceOf(IllegalStateException.class));

        assertThat(await(ordersRepo.count())).isZero();
        assertThat(await(orderItems.count())).isZero();
        assertThat(await(cart.lines("buyer")))
                .extracting(ItemDto::title, ItemDto::count)
                .containsExactly(tuple("Товар 1", 1), tuple("Товар 2", 2));
    }

    @Test
    void blankTitleIsRejected() {
        Item item = new Item("  ", "описание", "images/x.png", 10);
        assertThatThrownBy(() -> await(items.save(item))).isInstanceOf(ConstraintViolationException.class);
    }

    private Item save(String title, String description, int price) {
        return await(items.save(new Item(title, description, "images/" + title + ".png", price)));
    }

    private long idOf(String title) {
        return items.findAll()
                .filter(item -> item.getTitle().equals(title))
                .map(Item::getId)
                .blockFirst(TIMEOUT);
    }

    private long sum(String sessionId) {
        return await(cart.lines(sessionId)).stream().mapToLong(ItemDto::sum).sum();
    }

    private static <T> T await(Mono<T> mono) {
        return mono.block(TIMEOUT);
    }

    public static class FailSecondOrderItem implements BeforeConvertCallback<OrderItem> {

        private int seen;
        private boolean failSecond;

        void reset() {
            seen = 0;
            failSecond = false;
        }

        void failOnSecondSave() {
            seen = 0;
            failSecond = true;
        }

        @Override
        public Publisher<OrderItem> onBeforeConvert(OrderItem entity, SqlIdentifier table) {
            seen++;
            if (failSecond && seen >= 2) {
                return Mono.error(new IllegalStateException("second line"));
            }
            return Mono.just(entity);
        }
    }
}
