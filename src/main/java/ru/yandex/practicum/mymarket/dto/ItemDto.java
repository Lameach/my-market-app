package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.model.Item;

public record ItemDto(long id, String title, String description, String imgPath, int price, int count) {

    public static ItemDto from(Item item, int count) {
        return new ItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getImgPath(),
                item.getPrice(),
                count);
    }

    public long sum() {
        return (long) price * count;
    }
}
