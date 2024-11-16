package com.domus.populardirectors;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PageRepository extends CrudRepository<PageEntity, Integer> {
    @Query(nativeQuery = true, value = """
        SELECT
            distinct id
        FROM
            "PAGE_ENTITY"
        ORDER BY
            id
    """)
    List<Integer> findPageIds();

}
