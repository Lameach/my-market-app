package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record OrderDto(long id, List<OrderItemDto> items, long totalSum) {

    public static OrderDto of(long id, List<OrderItemDto> items) {
        long totalSum = items.stream().mapToLong(OrderItemDto::sum).sum();
        return new OrderDto(id, List.copyOf(items), totalSum);
    }
}
