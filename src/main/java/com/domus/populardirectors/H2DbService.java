package com.domus.populardirectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class H2DbService {
    @PersistenceContext
    private final EntityManager entityManager;

    final PageRepository pageRepository;

    @Transactional
    public PageDTO savePage(PageEntity pageEntity) {
        pageEntity.getMovieEntitySet().forEach(movie->movie.setPage(pageEntity));
        PageEntity savedPage = pageRepository.save(pageEntity);
        return new PageDTO(savedPage);
    }

    @Transactional(readOnly = true)
    public PageDTO getPage(int pageId) {
        PageEntity pageEntity = pageRepository.findById(pageId).orElse(null);
        if (pageEntity == null) {
            return null;
        } else {
            return new PageDTO(pageEntity);
        }
    }

    @Transactional(readOnly = true)
    public List<Integer> getPageIDs() {
        return pageRepository.findPageIds();
    }

    @Transactional
    public void truncateAll() {
        truncateTable("MOVIE_ENTITY");
        deleteTable("PAGE_ENTITY"); // oddly, H2 does not allow truncate of PAGE_ENTITY, even whem no movies
    }

    @Transactional
    public void deleteTable(String tableName) {
        String sql = "DELETE FROM " + tableName;
        Query query = entityManager.createNativeQuery(sql);
        query.executeUpdate();
    }
    @Transactional
    public void truncateTable(String tableName) {
        String sql = "TRUNCATE TABLE " + tableName;
        Query query = entityManager.createNativeQuery(sql);
        query.executeUpdate();
    }

    /**
     * NEVEr USED: an example of (slow) H2 DB query
     * @param threshold
     * @return
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getMovieDirectorCountAboveThreshold(int threshold) {
        if (true) {
            throw new RuntimeException("Not using H@ database to get above-threshold directors");
        }

        Date before = new Date();
        String strQuery = String.format("""
    select
       p.director as directorName,
       count(p) as movieCount
    from
       MovieEntity p
    group by
       p.director
    having
        count(p) > %d            
                """, threshold);

        List list = entityManager.createNativeQuery(String.format("""
            select m.director, count(1)
            from MOVIE_ENtITY m
            group by m.director
            having count(1) > %d
            group by m.director
            """, threshold),
            Tuple.class).getResultList();

        Date after = new Date();
        log.info("getMovieDirectorCountAboveThreshold(threshold={}), before={}, after={}",
                threshold, before, after);

        Date beforeMap = new Date();
        final Map<String, Integer> map = new HashMap<>();
        list.forEach(obj-> {
            Tuple tuple = (Tuple) obj;
            String directorName = tuple.get(0, String.class);
            Integer movieCount = tuple.get(0, Integer.class);
            map.put(directorName, movieCount);
        });
        Date afterMap = new Date();
        log.info("getMovieDirectorCountAboveThreshold(threshold={}), beforeMap={}, afterMap={}",
                threshold, beforeMap, afterMap);

        return map;
    }
}
