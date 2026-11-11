package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import jakarta.validation.ConstraintViolationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.connection.DelegatingConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.cache.CatalogEntry;
import ru.yandex.practicum.mymarket.cache.ItemLookup;
import ru.yandex.practicum.mymarket.config.ValidateBeforeConvert;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentGateway;

@DataR2dbcTest(properties = {
        "spring.r2dbc.url=r2dbc:h2:mem:///market-test?options=DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
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
        MarketPersistenceTest.FailSecondOrderItem.class,
        MarketPersistenceTest.PauseFirstWrite.class,
        MarketPersistenceTest.CartStatementTracker.class,
        MarketPersistenceTest.SuccessfulPayments.class,
        MarketPersistenceTest.RepositoryItems.class
})
class MarketPersistenceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long ONE = 1;
    private static final long TWO = 2;
    private static final long BUYER = 3;
    private static final long OTHER = 4;
    private static final long FRESH = 5;

    @Autowired
    private ItemRepository items;

    @Autowired
    private CartRepository carts;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatabaseClient database;

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

    @Autowired
    private PauseFirstWrite pauseFirstWrite;

    @Autowired
    private CartStatementTracker cartStatements;

    @BeforeEach
    void clean() {
        pauseFirstWrite.reset();
        orderItemGate.reset();
        orderItems.deleteAll()
                .then(ordersRepo.deleteAll())
                .then(cartItems.deleteAll())
                .then(carts.deleteAll())
                .then(users.deleteAll())
                .then(items.deleteAll())
                .then(insertUsers())
                .block(TIMEOUT);
    }

    private Mono<Void> insertUsers() {
        return Flux.just(ONE, TWO, BUYER, OTHER, FRESH)
                .concatMap(id -> database.sql(
                                "INSERT INTO users (id, username, password, enabled) VALUES (:id, :username, 'x', TRUE)")
                        .bind("id", id)
                        .bind("username", "user-" + id)
                        .fetch()
                        .rowsUpdated())
                .then();
    }

    @Test
    void searchMatchesTitleOrDescriptionAndSorts() {
        Item bread = save("Хлеб", "ржаной", 64);
        Item milk = save("Молоко", "пастеризованное", 89);
        save("Вода", "негазированная", 55);

        CatalogPage byTitle = await(catalog.show("МОЛО", "NO", 5, 1, null));
        assertThat(byTitle.items()).extracting(ItemDto::title).containsExactly("Молоко");

        CatalogPage byDescription = await(catalog.show("ржаной", "ALPHA", 5, 1, null));
        assertThat(byDescription.items()).extracting(ItemDto::id).containsExactly(bread.getId());

        CatalogPage byPrice = await(catalog.show("", "PRICE", 10, 1, null));
        assertThat(byPrice.items()).extracting(ItemDto::title).containsExactly("Вода", "Хлеб", "Молоко");

        CatalogPage byAlpha = await(catalog.show("  ", "alpha", 10, 1, null));
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

        assertThat(await(catalog.show("%", "NO", 5, 1, null)).items())
                .extracting(ItemDto::title)
                .containsExactly("100%");
        assertThat(await(catalog.show("_", "NO", 5, 1, null)).items()).isEmpty();
    }

    @Test
    void pagingFollowsPageSizeAndNumber() {
        save("Один", "a", 1);
        save("Два", "b", 2);
        save("Три", "c", 3);
        save("Четыре", "d", 4);

        CatalogPage page = await(catalog.show("", "NO", 5, 1, null));
        assertThat(page.paging().pageSize()).isEqualTo(5);
        assertThat(page.paging().pageNumber()).isEqualTo(1);
        assertThat(page.paging().hasPrevious()).isFalse();
        assertThat(page.paging().hasNext()).isFalse();
        assertThat(page.items()).extracting(ItemDto::title).containsExactly("Один", "Два", "Три", "Четыре");

        CatalogPage second = await(catalog.show("", "NO", 3, 2, null));
        assertThat(second.paging().hasPrevious()).isTrue();
        assertThat(second.paging().hasNext()).isFalse();
        assertThat(second.items()).extracting(ItemDto::title).containsExactly("Четыре");
    }

    @Test
    void cartCountNeverDropsBelowZeroAndStaysInsideTheSession() {
        Item apples = save("Яблоки", "сладкие", 129);
        Item milk = save("Молоко", "литр", 89);

        await(cart.change(ONE, apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines(ONE))).isEmpty();
        assertThat(sum(ONE)).isZero();

        await(cart.change(ONE, apples.getId(), CartAction.PLUS));
        await(cart.change(ONE, apples.getId(), CartAction.PLUS));
        await(cart.change(ONE, milk.getId(), CartAction.PLUS));
        assertThat(sum(ONE)).isEqualTo(129 * 2 + 89);
        assertThat(await(catalog.item(apples.getId(), ONE)).count()).isEqualTo(2);
        assertThat(await(catalog.item(apples.getId(), TWO)).count()).isZero();

        await(cart.change(ONE, apples.getId(), CartAction.MINUS));
        await(cart.change(ONE, milk.getId(), CartAction.DELETE));
        await(cart.change(ONE, apples.getId(), CartAction.MINUS));
        await(cart.change(ONE, apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines(ONE))).isEmpty();
        assertThat(await(cart.lines(TWO))).isEmpty();

        assertThatThrownBy(() -> await(cart.change(ONE, 999, CartAction.PLUS)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void buySnapshotsPricesAndClearsTheCart() {
        Item apples = save("Яблоки", "сладкие", 129);
        save("Молоко", "литр", 89);
        long milkId = idOf("Молоко");

        assertThat(await(orders.buy(BUYER))).isNull();

        await(cart.change(BUYER, apples.getId(), CartAction.PLUS));
        await(cart.change(BUYER, apples.getId(), CartAction.PLUS));
        await(cart.change(BUYER, milkId, CartAction.PLUS));

        long orderId = await(orders.buy(BUYER));
        apples.setPrice(1);
        await(items.save(apples));

        OrderDto order = await(orders.get(orderId, BUYER));
        assertThat(order.items()).extracting(item -> item.title()).containsExactly("Яблоки", "Молоко");
        assertThat(order.items()).extracting(item -> item.count()).containsExactly(2, 1);
        assertThat(order.items().get(0).price()).isEqualTo(129);
        assertThat(order.items().get(0).imgPath()).isEqualTo("images/Яблоки.png");
        assertThat(order.totalSum()).isEqualTo(129L * 2 + 89);
        assertThat(await(cart.lines(BUYER))).isEmpty();
        assertThat(await(orders.buy(BUYER))).isNull();
        assertThat(await(orders.list(BUYER))).extracting(OrderDto::id).containsExactly(orderId);
        assertThat(await(orders.list(OTHER))).isEmpty();
        assertThatThrownBy(() -> await(orders.get(orderId, OTHER))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void twoConcurrentPlusOnAnExistingLineBothCount() {
        Item apples = save("Яблоки", "сладкие", 129);
        await(cart.change(ONE, apples.getId(), CartAction.PLUS));

        overlap(
                cart.change(ONE, apples.getId(), CartAction.PLUS),
                cart.change(ONE, apples.getId(), CartAction.PLUS),
                CartItem.class);

        assertThat(await(cartItems.count())).isEqualTo(1);
        assertThat(await(cart.lines(ONE))).extracting(ItemDto::count).containsExactly(3);

        await(cart.change(ONE, apples.getId(), CartAction.PLUS));
        assertThat(await(cart.lines(ONE))).extracting(ItemDto::count).containsExactly(4);
    }

    @Test
    void twoConcurrentFirstPlusCreateOneLine() {
        Item apples = save("Яблоки", "сладкие", 129);

        overlap(
                cart.change(FRESH, apples.getId(), CartAction.PLUS),
                cart.change(FRESH, apples.getId(), CartAction.PLUS),
                CartItem.class);

        assertThat(await(carts.count())).isEqualTo(1);
        assertThat(await(cartItems.count())).isEqualTo(1);
        assertThat(await(cart.lines(FRESH))).extracting(ItemDto::count).containsExactly(2);

        await(cart.change(FRESH, apples.getId(), CartAction.MINUS));
        assertThat(await(cart.lines(FRESH))).extracting(ItemDto::count).containsExactly(1);
    }

    @Test
    void twoConcurrentBuysCreateOneOrder() {
        Item apples = save("Яблоки", "сладкие", 129);
        Item milk = save("Молоко", "литр", 89);
        await(cart.change(BUYER, apples.getId(), CartAction.PLUS));
        await(cart.change(BUYER, apples.getId(), CartAction.PLUS));
        await(cart.change(BUYER, milk.getId(), CartAction.PLUS));

        overlap(orders.buy(BUYER).then(), orders.buy(BUYER).then(), Order.class);

        assertThat(await(ordersRepo.count())).isEqualTo(1);
        assertThat(await(orders.list(BUYER))).singleElement().satisfies(order -> assertThat(order.items())
                .extracting(OrderItemDto::title, OrderItemDto::count)
                .containsExactly(tuple("Яблоки", 2), tuple("Молоко", 1)));
        assertThat(await(cart.lines(BUYER))).isEmpty();
    }

    @Test
    void buyRollsBackWhenTheSecondLineIsNotSaved() {
        Item first = save("Товар 1", "a", 10);
        Item second = save("Товар 2", "b", 20);
        await(cart.change(BUYER, first.getId(), CartAction.PLUS));
        await(cart.change(BUYER, second.getId(), CartAction.PLUS));
        await(cart.change(BUYER, second.getId(), CartAction.PLUS));
        orderItemGate.failOnSecondSave();

        assertThatThrownBy(() -> await(orders.buy(BUYER)))
                .satisfies(thrown -> assertThat(NestedExceptionUtils.getMostSpecificCause(thrown))
                        .isInstanceOf(IllegalStateException.class));

        assertThat(await(ordersRepo.count())).isZero();
        assertThat(await(orderItems.count())).isZero();
        assertThat(await(cart.lines(BUYER)))
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

    private long sum(long userId) {
        return await(cart.lines(userId)).stream().mapToLong(ItemDto::sum).sum();
    }

    // The second call is subscribed only after the first is parked inside its transaction.
    // An in-flight cart statement shows that the second has reached the database before the first commits.
    private void overlap(Mono<Void> first, Mono<Void> second, Class<?> pausedWrite) {
        pauseFirstWrite.arm(pausedWrite);
        CountDownLatch firstDone = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        first.subscribeOn(Schedulers.boundedElastic()).subscribe(
                value -> { },
                error -> {
                    firstError.set(error);
                    firstDone.countDown();
                },
                firstDone::countDown);
        try {
            assertThat(pauseFirstWrite.awaitEntered())
                    .as("first operation did not reach its write inside the transaction")
                    .isTrue();

            CountDownLatch secondDone = new CountDownLatch(1);
            AtomicReference<Throwable> secondError = new AtomicReference<>();
            second.subscribeOn(Schedulers.boundedElastic()).subscribe(
                    value -> { },
                    error -> {
                        secondError.set(error);
                        secondDone.countDown();
                    },
                    secondDone::countDown);

            assertSecondIsWaitingOnTheCart(firstDone, secondDone, secondError);
            assertThat(firstDone.getCount()).isEqualTo(1);
            assertThat(secondDone.getCount()).isEqualTo(1);
            assertThat(cartStatements.cartStatementInFlight()).isTrue();
            pauseFirstWrite.release();

            assertThat(arrived(firstDone)).isTrue();
            assertThat(arrived(secondDone)).isTrue();
            assertThat(firstError.get()).isNull();
            assertThat(secondError.get()).isNull();
        } finally {
            pauseFirstWrite.release();
        }
    }

    private void assertSecondIsWaitingOnTheCart(
            CountDownLatch firstDone,
            CountDownLatch secondDone,
            AtomicReference<Throwable> secondError) {
        int seenAtPause = cartStatements.seen();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (firstDone.getCount() == 0) {
                fail("first operation finished before the second reached the cart");
            }
            if (secondDone.getCount() == 0) {
                fail("second operation finished while the first transaction was still open: " + secondError.get());
            }
            if (cartStatements.seen() > seenAtPause && cartStatements.cartStatementInFlight()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        fail("second operation did not start work on the cart before the first finished; cart statements seen "
                + cartStatements.seen()
                + ", in flight " + cartStatements.cartStatementInFlight());
    }

    private static boolean arrived(CountDownLatch latch) {
        try {
            return latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
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

    public static class PauseFirstWrite implements BeforeConvertCallback<Object> {

        private final AtomicInteger seen = new AtomicInteger();
        private volatile Class<?> target;
        private volatile Sinks.One<Void> release = Sinks.one();
        private volatile CountDownLatch entered = new CountDownLatch(1);

        void arm(Class<?> type) {
            seen.set(0);
            release = Sinks.one();
            entered = new CountDownLatch(1);
            target = type;
        }

        void release() {
            release.tryEmitEmpty();
        }

        boolean awaitEntered() {
            return arrived(entered);
        }

        void reset() {
            target = null;
            release.tryEmitEmpty();
        }

        @Override
        public Publisher<Object> onBeforeConvert(Object entity, SqlIdentifier table) {
            Class<?> expected = target;
            if (expected == null || !expected.isInstance(entity)) {
                return Mono.just(entity);
            }
            if (seen.incrementAndGet() != 1) {
                return Mono.just(entity);
            }
            Sinks.One<Void> gate = release;
            entered.countDown();
            return gate.asMono().thenReturn(entity);
        }
    }

    public static class CartStatementTracker implements BeanPostProcessor, Ordered {

        private final AtomicInteger seen = new AtomicInteger();
        private final AtomicInteger inFlight = new AtomicInteger();

        int seen() {
            return seen.get();
        }

        boolean cartStatementInFlight() {
            return inFlight.get() > 0;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof ConnectionFactory factory && !(bean instanceof TrackingConnectionFactory)) {
                return new TrackingConnectionFactory(factory, this);
            }
            return bean;
        }

        private Connection trackConnection(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return invoke(method, delegate, args);
                        }
                        if ("createStatement".equals(method.getName()) && args != null && args.length > 0) {
                            Statement statement = (Statement) invoke(method, delegate, args);
                            return trackStatement(statement, String.valueOf(args[0]));
                        }
                        return invoke(method, delegate, args);
                    });
        }

        private Statement trackStatement(Statement delegate, String sql) {
            boolean cart = sql.toLowerCase(Locale.ROOT).contains("cart");
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[] {Statement.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return invoke(method, delegate, args);
                        }
                        if ("execute".equals(method.getName())) {
                            Publisher<?> execution = (Publisher<?>) invoke(method, delegate, args);
                            return Flux.defer(() -> {
                                if (cart) {
                                    seen.incrementAndGet();
                                    inFlight.incrementAndGet();
                                }
                                return Flux.from(execution).doFinally(signal -> {
                                    if (cart) {
                                        inFlight.decrementAndGet();
                                    }
                                });
                            });
                        }
                        Object result = invoke(method, delegate, args);
                        return result == delegate ? proxy : result;
                    });
        }

        private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args == null ? new Object[0] : args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause() == null ? ex : ex.getCause();
            }
        }

        private static final class TrackingConnectionFactory extends DelegatingConnectionFactory {

            private final CartStatementTracker tracker;

            private TrackingConnectionFactory(ConnectionFactory target, CartStatementTracker tracker) {
                super(target);
                this.tracker = tracker;
            }

            @Override
            public Mono<? extends Connection> create() {
                return super.create().map(tracker::trackConnection);
            }
        }
    }

    static class RepositoryItems implements ItemLookup {

        private final ItemRepository items;

        RepositoryItems(ItemRepository items) {
            this.items = items;
        }

        @Override
        public Mono<List<CatalogEntry>> catalog() {
            return items.findAll().map(CatalogEntry::from).collectList();
        }

        @Override
        public Mono<Item> card(long id) {
            return items.findById(id);
        }

        @Override
        public Mono<Map<Long, Item>> cards(Collection<Long> ids) {
            if (ids.isEmpty()) {
                return Mono.just(Map.of());
            }
            return items.findAllById(ids).collectMap(Item::getId);
        }
    }

    static class SuccessfulPayments implements PaymentGateway {

        @Override
        public Mono<Long> balance(long userId) {
            return Mono.just(Long.MAX_VALUE);
        }

        @Override
        public Mono<Void> pay(long userId, long amount) {
            return Mono.empty();
        }
    }
}
