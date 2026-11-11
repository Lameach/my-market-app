package ru.yandex.practicum.mymarket.dto;

public record Paging(int pageSize, int pageNumber, boolean hasPrevious, boolean hasNext) {
}
