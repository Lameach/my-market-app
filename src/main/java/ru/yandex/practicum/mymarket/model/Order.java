package ru.yandex.practicum.mymarket.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
public class Order {

    @Id
    private Long id;

    @NotBlank
    @Column("session_id")
    private String sessionId;

    public Order(String sessionId) {
        this(null, sessionId);
    }

    @PersistenceCreator
    public Order(Long id, String sessionId) {
        this.id = id;
        this.sessionId = sessionId;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }
}
