package com.domus.populardirectors;

import lombok.Getter;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class DataInfo {
    private final ConcurrentLinkedDeque<Integer> pagesNotFetched = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean fetchesAllDone = new AtomicBoolean(false);
    private final AtomicReference<Exception> nonRecoverableException = new AtomicReference<>();

    /**
     * Holding thread-safe Collection of all fetched directors, instead of relying on H2 MOVIE_ENTITY to query them
     */
    private final ConcurrentLinkedQueue<String> allFetchedDirectors = new ConcurrentLinkedQueue<>();
}
