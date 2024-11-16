package com.domus.populardirectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationListener<ApplicationReadyEvent> {
    private final DataInfo dataInfo;

    @Value("${populardirectors.maxthreads}")
    private int maxThreads;

    @Value("${populardirectors.maxmovies_inmemory}")
    private int maxMoviesInMemory;

    @Value("${populardirectors.base_movie_url}")
    private String baseMovieUrl;

    @Value("${populardirectors.abort_on_pagefetch_timeout}")
    private boolean abortOnPageFetchTimeout;

/*
    @Override
    public void run(String... args) {
        log.info("BeforeAcceptTraffic.run(...) started");

        fetchData();
    }
*/
    private Thread t = new Thread(() -> {
        fetchData();
    });
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // ??? RBATTAGLIA - why check THReAD.isAlive() HEre ???
        if (!t.isAlive()) {
            t.start();
        }
    }

    public void fetchData() {
        PageDTO firstPage = fetchPage(1, -1);
        if (firstPage == null) {
            log.error("Could not fetch the first page - nothing to do");
            return;
        }
        if (firstPage.getTotalPages() == 1) {
            // TODO rbattaglia now: set "done=true" somehow
            return;
        }
        for (int p = 2; p <= firstPage.getTotalPages(); p++) {
            dataInfo.getPagesNotFetched().add(p); // has to be mutable List ! ;
        }
        int nofThreadsToUse = Math.min(firstPage.getTotalPages(), maxThreads);
        if (nofThreadsToUse * firstPage.getPerPage() > maxMoviesInMemory) {
            nofThreadsToUse = maxMoviesInMemory / firstPage.getPerPage();
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int currThreadNum = 1; currThreadNum <= nofThreadsToUse; currThreadNum++) {
                int finalCurrThreadNum = currThreadNum;
                int finalNofThreadsToUse = nofThreadsToUse;
                executor.submit(() -> {
                    log.info("thread {} of {} started", finalCurrThreadNum, finalNofThreadsToUse);
                    int myPage;
                    while (true) {
                        // TODO rbattaglia - break_all IF we got FORMAT_CHANGED page? nofPages, nofMovies DIFFERENT?
                        // TODO rbattaglia - fastest way to kill all running threads? Send an INTERRUPT signal?
                        // TODO rbattaglia - how would Controller class tell STILL_FETCHING threads/loops that it needs to reinitialize itself?
                        myPage = getUnfetchedPage(finalCurrThreadNum);
                        log.info("thread {} of {} running, myPage={}", finalCurrThreadNum, finalNofThreadsToUse, myPage);
                        if (myPage == -1) {
                            break;
                        }

                        // -1 means "it seems there's no page for you, we're close to done,
                        // maybe other threads already doint all remaining pages"
                        fetchPage(myPage, finalCurrThreadNum);
                    }
                });
            }
            //executor.shutdown();
            try {
                boolean terminated = executor.awaitTermination(1, TimeUnit.HOURS); // TODO rbattaglia - TOtAL REVAMP of the WAITING logic here
                if (! terminated) {
                    setNonRecoverableError("ExecutorService.awaitTermination",
                            new RuntimeException("ExecutorService.awaitTermination - finished before terminating, took too long"));
                }

            } catch (InterruptedException e) {
                setNonRecoverableError("InterruptedException awaiting executorService termination", e);
                executor.shutdown();
                // TODO rbattaglia - how to deal with too-long-fetching-data-from-external-HTTP-endpoint
            }
            dataInfo.getFetchesAllDone().set(true); // DONE set to TRUE even when errors/incompletion found
        }

    }

    private int getUnfetchedPage(int threadNum) {
        Integer unfetchedPage = null;
        log.info("getUnfetchedPage , threadNum={}, will do workData.pagesNotFetched.remove()", threadNum);
        try {
            unfetchedPage = dataInfo.getPagesNotFetched().remove();
        } catch (NoSuchElementException e) {
            log.info("getUnfetchedPage , threadNum={}, NoSuchElementException (ok, no problem)", threadNum);
        }
        int retVal = (unfetchedPage == null) ? -1 : unfetchedPage;
        log.info("getUnfetchedPage , threadNum={}, will return retVal={}", threadNum, retVal);
        return retVal;
    }

    private PageDTO fetchPage(int page, int threadNum) {
        /*
            TODO - needs to throw Exception(s) telling what went wrong, must be caught and shut down ExecutorService
        */

        String requestURL = getMovieURL(page);
        log.info("threadNum {} fetching page, requestURL={}", threadNum, requestURL);

        int maxAttempts = 10; // maximum attempts, retried when TimedOut
        int millisWaitBetweenAttempts = 2000; // could maxAttempts and millis depend on other stats?
        for (int attempt=1; attempt <= maxAttempts && dataInfo.getNonRecoverableException().get()==null; attempt++) {
            log.info("threadNum {} Will do fetching page {}, attempt {} of {}", threadNum, page, attempt, maxAttempts);
            try(HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(requestURL))
                        .GET()
                        .timeout(Duration.of(5, ChronoUnit.SECONDS))
                        .build();
                HttpResponse<String> response = httpClient
                        .send(request, HttpResponse.BodyHandlers.ofString());
                ObjectMapper objectMapper = new ObjectMapper();
                PageDTO pageDTO = objectMapper.readValue(response.body(), PageDTO.class);
                for (MovieDTO movieDTO: pageDTO.getData()) {
                    dataInfo.getAllFetchedDirectors().add(movieDTO.getDirector()); // TODO - one-step thread-safe "computeIfSomething WITH Adder"
                }
                log.info("threadNum {} OK fetching page {}, attempt {} of {}, duely got PageDTO and added its directors to allFetchedDirectors", threadNum, page, attempt, maxAttempts);
                return pageDTO;
            } catch (URISyntaxException e) {
                setNonRecoverableError("URI Syntax Exception", e);
            } catch (HttpTimeoutException e) {
                if (attempt < maxAttempts) {
                    log.error("HttpTimeoutException fetching page {}, attempt {} of {},will sleep {} milliseconds and retry", page, attempt, maxAttempts, millisWaitBetweenAttempts);
                    try {
                        Thread.sleep(millisWaitBetweenAttempts);
                    } catch (InterruptedException interruptedEx) {

                        setNonRecoverableError(String.format("InterruptedException sleeping to retry fetching page %d attempt %d of %d, aborting...",
                                page, attempt, maxAttempts), interruptedEx);
                    }
                } else {
                    if (abortOnPageFetchTimeout || page == 1) {
                        setNonRecoverableError(String.format("HttpTimeoutException fetching page %d, LAST attempt %d of %d, aborting...",
                                page, attempt, maxAttempts), e);
                    } else {
                        log.error("HttpTimeoutException fetching page {}, attempt {} of {}, permanently ignoring page...",
                                page, attempt, maxAttempts, e);
                    }
                }
            } catch (IOException e) {
                setNonRecoverableError(String.format("IOException fetching page %d", page), e);
            } catch (InterruptedException e) {
                setNonRecoverableError(String.format("InterruptedException fetching page %d", page), e);
            } catch (Exception e) {
                setNonRecoverableError(String.format("Exception fetching page %d", page), e);
            }

        }

        return null;
    }

    private String getMovieURL(int page) {
        return baseMovieUrl + page;
    }

    private void setNonRecoverableError(String errorMessage, Exception e) {
        log.error(errorMessage, e);
        dataInfo.getNonRecoverableException().compareAndSet(null, new Exception(errorMessage, e));
    }

}
