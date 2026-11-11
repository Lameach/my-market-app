package ru.yandex.practicum.mymarket.model;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cart_items")
public class CartItem {

    @Id
    private Long id;

    @Column("cart_id")
    private Long cartId;

    @Column("item_id")
    private Long itemId;

    @PositiveOrZero
    @Column("item_count")
    private int count;

    public CartItem(Long cartId, Long itemId, int count) {
        this(null, cartId, itemId, count);
    }

    @PersistenceCreator
    public CartItem(Long id, Long cartId, Long itemId, int count) {
        this.id = id;
        this.cartId = cartId;
        this.itemId = itemId;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
