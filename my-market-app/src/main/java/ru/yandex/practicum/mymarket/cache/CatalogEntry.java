package ru.yandex.practicum.mymarket.cache;

import ru.yandex.practicum.mymarket.model.Item;

public record CatalogEntry(long id, String title, String description, int price) {

    public static CatalogEntry from(Item item) {
        return new CatalogEntry(item.getId(), item.getTitle(), item.getDescription(), item.getPrice());
    }
}
