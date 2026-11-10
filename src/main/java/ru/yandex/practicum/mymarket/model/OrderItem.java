package ru.yandex.practicum.mymarket.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public class OrderItem {

    @Id
    private Long id;

    @Column("order_id")
    private Long orderId;

    @NotBlank
    private String title;

    @NotBlank
    @Column("img_path")
    private String imgPath;

    @PositiveOrZero
    private int price;

    @Positive
    @Column("item_count")
    private int count;

    public OrderItem(Long orderId, String title, String imgPath, int price, int count) {
        this(null, orderId, title, imgPath, price, count);
    }

    @PersistenceCreator
    public OrderItem(Long id, Long orderId, String title, String imgPath, int price, int count) {
        this.id = id;
        this.orderId = orderId;
        this.title = title;
        this.imgPath = imgPath;
        this.price = price;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getTitle() {
        return title;
    }

    public String getImgPath() {
        return imgPath;
    }

    public int getPrice() {
        return price;
    }

    public int getCount() {
        return count;
    }
}
