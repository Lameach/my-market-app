package ru.yandex.practicum.mymarket.dto;

public record OrderItemDto(long id, String title, String imgPath, int price, int count) {

    public long sum() {
        return (long) price * count;
    }
}
