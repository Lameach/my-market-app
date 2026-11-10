package ru.yandex.practicum.mymarket.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Item;

public interface ItemLookup {

    Mono<List<CatalogEntry>> catalog();

    Mono<Item> card(long id);

    Mono<Map<Long, Item>> cards(Collection<Long> ids);
}
