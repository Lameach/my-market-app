package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.model.Item;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query(
            value = """
                    SELECT i FROM Item i
                    WHERE LOWER(i.title) LIKE :pattern ESCAPE '!'
                       OR LOWER(i.description) LIKE :pattern ESCAPE '!'
                    """,
            countQuery = """
                    SELECT COUNT(i) FROM Item i
                    WHERE LOWER(i.title) LIKE :pattern ESCAPE '!'
                       OR LOWER(i.description) LIKE :pattern ESCAPE '!'
                    """)
    Page<Item> search(@Param("pattern") String pattern, Pageable pageable);
}
