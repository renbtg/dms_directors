package com.domus.populardirectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


@RestController
@RequestMapping("api")
@Slf4j
@RequiredArgsConstructor
public class PopularDirectorsController {
    private final DataInfo dataInfo;

    @Value("${populardirectors.allow_incomplete_data}")
    private boolean allowIncompleteData;

    @Value("${populardirectors.endpoint_max_fetch_wait}")
    private int endpointMaxFetchWait;
    @Value("${populardirectors.endpoint_millis_sleep_awaiting_done}")
    private int endpointMillisSleepAwaitingDone;

    @GetMapping(value = "/directors", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getDirectorsAboveThreshold(@RequestParam int threshold) throws Exception {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold parameter must be 1 or greater");
        }

        /*
            TODO rbattaglia - call WhateverClass.checkDataStructure()

                * Method WhateverClass.getDataStructure() will remote-fetch 1st page and first.totalPages()
            PLUS first.totalMovies().
                * If any of the 2 values different than OurDataInfo.(totalPages PLUS totalMovies) THEN we'll have
            to trigger a FETCH_IT_ALL_AGAIN.
                Customizably, we may...
                    A) Preserve AND USE original underlying_already_fetched_data WHILE fetching new content
                    OR
                    B) Clean underlying_fetched_data AND start over
                * ANYWAY, we still need to think about PERSISTENT PAGE DATA... configurable to...
                    A) YES PERSISTENT
                        * advantage: on application restart, we don't need to fetch_external_pages
                    B) NO NOT_PERSISTENT
                        * advantage: faster each_thread_run while fetching, since we'll not db_persist each page

         */

        Exception nonRecoverable = dataInfo.getNonRecoverableException().get();
        if (nonRecoverable != null) {
            // TODO - improve... right now, throwing the exception is poor-man's version
            throw nonRecoverable;
        }
        boolean done = dataInfo.getFetchesAllDone().get();
        HttpStatus okStatus = HttpStatus.OK;
        if (! done && endpointMaxFetchWait > 0) {
            long initialMillis = System.currentTimeMillis();
            long maxMillis = initialMillis + endpointMaxFetchWait;
            while (!(done = dataInfo.getFetchesAllDone().get()) && System.currentTimeMillis() < maxMillis) {
                try {
                    Thread.sleep(endpointMillisSleepAwaitingDone);
                } catch (InterruptedException e) {
                    log.error("InterruptedException while controller endpoint waiting for FETCH DONE", e);
                    break;
                }
            }
        }
        if (! done) {
            if (! allowIncompleteData) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            okStatus = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(okStatus).body(getDirectorListAboveThreshold(threshold, dataInfo.getAllFetchedDirectors()));
    }

    private List<String> getDirectorListAboveThreshold(int threshold, ConcurrentLinkedQueue<String> fetchedDirectors) {
        // TODO - improve and simplify counting
        Map<String, Integer> map = new HashMap<>();
        List<String> directors = new ArrayList<>(fetchedDirectors);
        for (String directorName : directors) {
            if (map.containsKey(directorName)) {
                map.put(directorName, map.get(directorName) + 1);
            } else {
                map.put(directorName, 1);
            }
        }
        List<String> aboveThreshold = map.entrySet().stream()
                .filter(es->es.getValue() > threshold)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return aboveThreshold;
    }

}


