package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record OrderDto(long id, List<OrderItemDto> items, int totalSum) {

    public static OrderDto of(long id, List<OrderItemDto> items) {
        int totalSum = items.stream().mapToInt(item -> item.price() * item.count()).sum();
        return new OrderDto(id, List.copyOf(items), totalSum);
    }
}
