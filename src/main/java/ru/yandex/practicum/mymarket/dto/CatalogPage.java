package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record CatalogPage(List<ItemDto> items, Paging paging, String search, String sort) {
}
