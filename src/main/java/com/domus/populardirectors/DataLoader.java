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
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationListener<ApplicationReadyEvent> {
    private final DataInfo dataInfo;
    private final H2DbService h2DbService;

    @Value("${populardirectors.enable_persistence}")
    private boolean enablePersistence;

    @Value("${populardirectors.maxthreads}")
    private int maxThreads;

    @Value("${populardirectors.maxmovies_inmemory}")
    private int maxMoviesInMemory;

    @Value("${populardirectors.base_movie_url}")
    private String baseMovieUrl;

    @Value("${populardirectors.abort_on_pagefetch_timeout}")
    private boolean abortOnPageFetchTimeout;

    private final Thread t = new Thread(() -> {
        log.info("Will call method fetchData()");
        fetchData();
        log.info("Did call method fetchData()");
    });
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // TODO - any need to check THReAD.isAlive() HEre ???
        if (!t.isAlive()) {
            t.start();
        }
    }

    public void fetchData() {
        PageDTO firstPage = fetchPage(1, -1);
        if (firstPage == null) {
            setNonRecoverableError("Could not fetch the first page - nothing to do");
            return;
        }
        if (firstPage.getTotalPages() == 1) {
            if (enablePersistence) {
                h2DbService.truncateAll();
                PageEntity pageEntity = new PageEntity(firstPage);
                h2DbService.savePage(pageEntity);
            }
            dataInfo.getFetchesAllDone().set(true); // DONE set to TRUE even when errors/incompletion found
            return;
        }

        if (enablePersistence) {
            PageDTO firstPageEntity = h2DbService.getPage(1);
            if (firstPageEntity != null) {
                if (firstPageEntity.getTotalPages() != firstPage.getTotalPages() ||
                        firstPageEntity.getPerPage() != firstPage.getPerPage()) {
                    log.info("external endpoint JSON structure changed, need to refetch it all");
                    h2DbService.truncateAll();
                    PageEntity rebuiltPageEntity = new PageEntity(firstPage);
                    h2DbService.savePage(rebuiltPageEntity);
                }
            } else {
                PageEntity rebuiltFirstPageEntity = new PageEntity(firstPage);
                // DB-save the previosçy non-existing first page
                h2DbService.savePage(rebuiltFirstPageEntity);
            }
        }
        List<Integer> persistedPages = enablePersistence ? h2DbService.getPageIDs() : List.of(1);
        dataInfo.getPagesNotFetched().clear();

        for (int p = 2; p <= firstPage.getTotalPages(); p++) {
            if (! persistedPages.contains(p)) {
                dataInfo.getPagesNotFetched().add(p); // has to be mutable List ! ;
            } else {
                // SHORTCUT: add directors of pages already_previously_successfully_fetched
                final PageDTO pageDTO = h2DbService.getPage(p);
                dataInfo.getAllFetchedDirectors().addAll(pageDTO.getData().stream().
                        map(MovieDTO::getDirector).toList());
                log.info("Added {} DB-fetched directors to allFetchedDirectors (from page {}} ",
                        pageDTO.getData().size(), p);
            }
        }
        if (dataInfo.getPagesNotFetched().isEmpty()) {
            // all pages have already been successfully fetched and saved previously, JUST RETURN, we have it all
            log.info("dataInfo.getPagesNotFetched() is empty, all is already loaded, returning early");
            dataInfo.getFetchesAllDone().set(true);
            return;
        }

        int nofThreadsToUse = Math.min(dataInfo.getPagesNotFetched().size(), maxThreads);
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
                        myPage = getUnfetchedPage(finalCurrThreadNum);
                        log.info("thread {} of {} running, myPage={}", finalCurrThreadNum, finalNofThreadsToUse, myPage);
                        if (myPage == -1) {
                            // -1 means "it seems there's no page for current thread, we're close to done,
                            break;
                        }

                        PageDTO pageFetched = fetchPage(myPage, finalCurrThreadNum);
                        if (enablePersistence) {
                            h2DbService.savePage(new PageEntity(pageFetched));
                        }
                    }
                });
            }
            //executor.shutdown();
            try {
                executor.shutdown();

                log.info("Now is {}, WILL do executor.awaitTermination", new Date());
                boolean terminated = executor.awaitTermination((firstPage.getTotalPages()-1)*3, TimeUnit.SECONDS);
                log.info("Now is {}, DID do executor.awaitTermination", new Date());

                if (! terminated) {
                    setNonRecoverableError("ExecutorService.awaitTermination",
                            new RuntimeException("ExecutorService.awaitTermination - finished before terminating, took too long"));
                }

            } catch (InterruptedException e) {
                setNonRecoverableError("InterruptedException awaiting executorService termination", e);
                executor.shutdown(); // TODO: OUCH, another shutdown here is innocuous or harmful, as it seems
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

    private PageDTO fetchPage(int pageNum, int threadNum) {
        /*
            TODO - strenghten, maybe more exception checking/throwing
        */

        String requestURL = getMovieURL(pageNum);
        log.info("threadNum {} fetching pageNum, requestURL={}", threadNum, requestURL);

        int maxAttempts = 10; // maximum attempts, retried when TimedOut
        int millisWaitBetweenAttempts = 2000; // could maxAttempts and millis depend on other stats?
        for (int attempt=1; attempt <= maxAttempts && dataInfo.getNonRecoverableException().get()==null; attempt++) {
            log.info("threadNum {} Will do fetching pageNum {}, attempt {} of {}", threadNum, pageNum, attempt, maxAttempts);
            try(HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(requestURL))
                        .GET()
                        .timeout(Duration.of(5, ChronoUnit.SECONDS))
                        .build();
                HttpResponse<String> response = httpClient
                        .send(request, HttpResponse.BodyHandlers.ofString());
                ObjectMapper objectMapper = new ObjectMapper();
                String responseBody = response.body();
                PageDTO pageDTO = objectMapper.readValue(responseBody, PageDTO.class);
                dataInfo.getAllFetchedDirectors().addAll(pageDTO.getData().stream().
                        map(MovieDTO::getDirector).toList());
                log.info("Added {} remote-url-fetched directors to allFetchedDirectors (from page {}} ",
                        pageDTO.getData().size(), pageNum);
                log.info("threadNum {} OK fetching pageNum {}, attempt {} of {}, duely got PageDTO", threadNum, pageNum, attempt, maxAttempts);
                return pageDTO;
            } catch (URISyntaxException e) {
                setNonRecoverableError("URI Syntax Exception", e);
            } catch (HttpTimeoutException e) {
                if (attempt < maxAttempts) {
                    log.error("HttpTimeoutException fetching pageNum {}, attempt {} of {},will sleep {} milliseconds and retry", pageNum, attempt, maxAttempts, millisWaitBetweenAttempts);
                    try {
                        Thread.sleep(millisWaitBetweenAttempts);
                    } catch (InterruptedException interruptedEx) {

                        setNonRecoverableError(String.format("InterruptedException sleeping to retry fetching pageNum %d attempt %d of %d, aborting...",
                                pageNum, attempt, maxAttempts), interruptedEx);
                    }
                } else {
                    if (abortOnPageFetchTimeout || pageNum == 1) {
                        setNonRecoverableError(String.format("HttpTimeoutException fetching pageNum %d, LAST attempt %d of %d, aborting...",
                                pageNum, attempt, maxAttempts), e);
                    } else {
                        log.error("HttpTimeoutException fetching pageNum {}, attempt {} of {}, permanently ignoring pageNum...",
                                pageNum, attempt, maxAttempts, e);
                    }
                }
            } catch (IOException e) {
                setNonRecoverableError(String.format("IOException fetching pageNum %d", pageNum), e);
            } catch (InterruptedException e) {
                setNonRecoverableError(String.format("InterruptedException fetching pageNum %d", pageNum), e);
            } catch (Exception e) {
                setNonRecoverableError(String.format("Exception fetching pageNum %d", pageNum), e);
            }

        }

        return null;
    }

    private String getMovieURL(int page) {
        return baseMovieUrl + page;
    }

    private void setNonRecoverableError(String errorMessage) {
        log.error(errorMessage);
        dataInfo.getNonRecoverableException().compareAndSet(null, new RuntimeException(errorMessage));
    }
    private void setNonRecoverableError(String errorMessage, Exception e) {
        log.error(errorMessage, e);
        dataInfo.getNonRecoverableException().compareAndSet(null, new RuntimeException(errorMessage, e));
    }

}
