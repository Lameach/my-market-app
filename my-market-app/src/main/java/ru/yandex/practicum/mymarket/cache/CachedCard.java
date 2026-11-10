package ru.yandex.practicum.mymarket.cache;

import ru.yandex.practicum.mymarket.model.Item;

public record CachedCard(long id, String imgPath, String title, int price, String description) {

    public static CachedCard from(Item item) {
        return new CachedCard(item.getId(), item.getImgPath(), item.getTitle(), item.getPrice(), item.getDescription());
    }

    public Item toItem() {
        return new Item(id, title, description, imgPath, price);
    }
}
