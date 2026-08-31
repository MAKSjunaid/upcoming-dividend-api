package com.example.upcomingdividendapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendService {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    /*
     * Number of parallel Yahoo requests.
     *
     * Yahoo is called once per symbol.
     */
    private static final int THREAD_COUNT = 8;

    /*
     * HTTP connection timeout.
     */
    private static final int CONNECT_TIMEOUT = 10000;

    /*
     * HTTP read timeout.
     */
    private static final int READ_TIMEOUT = 15000;

    /*
     * Cache directory.
     */
    private static final String CACHE_DIRECTORY = "cache";

    /*
     * Cache file.
     */
    private static final String CACHE_FILE_NAME =
            "dividend-cache.json";

    /*
     * Active price window.
     *
     * Only dividend shares whose ex-date is from tomorrow
     * through the next 30 days are normally refreshed.
     */
    private static final int ACTIVE_PRICE_WINDOW_DAYS = 15;

    /*
     * Yahoo price refresh interval.
     *
     * A share will not be sent to Yahoo again if it was already
     * checked less than 1 minute ago.
     */
    private static final int PRICE_REFRESH_INTERVAL_MINUTES = 1;

    /*
     * Dividend API refresh interval.
     *
     * NSE + Groww are not called on every request.
     *
     * Once the cache has been created, a background refresh
     * is allowed once every 1 minute.
     *
     * NOTE:
     * This is currently 1 minute for testing.
     */
    private static final int DIVIDEND_REFRESH_INTERVAL_MINUTES = 1;

    /*
     * If Yahoo cannot find a symbol, don't retry it on every
     * frontend request.
     */
    private static final int YAHOO_NOT_FOUND_RETRY_DAYS = 2;

    /*
     * Trading hours:
     *
     * 09:00 AM to 03:35 PM IST
     *
     * Monday to Friday.
     */
    private static final LocalTime TRADING_START_TIME =
            LocalTime.of(9, 0);

    private static final LocalTime TRADING_END_TIME =
            LocalTime.of(15, 35);

    /*
     * India Standard Time.
     */
    private static final ZoneId INDIA_ZONE =
            ZoneId.of("Asia/Kolkata");

    /*
     * Synchronizes cache file access.
     */
    private static final Object CACHE_LOCK =
            new Object();

    /*
     * Prevents multiple background dividend refreshes from
     * running at the same time.
     */
    private static final AtomicBoolean
            BACKGROUND_REFRESH_RUNNING =
            new AtomicBoolean(false);

    /*
     * Gson used for reading/writing cache JSON.
     */
    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    // ============================================================
    // DATE FORMATTERS
    // ============================================================

    private static final DateTimeFormatter API_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final DateTimeFormatter NSE_RESPONSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd-MMM-yyyy",
                    Locale.ENGLISH
            );

    // ============================================================
    // REGEX
    // ============================================================

    private static final Pattern DIVIDEND_PATTERN =
            Pattern.compile(
                    "(?i)\\bR(?:s|e)\\.?\\s*"
                            + "([0-9]+(?:\\.[0-9]+)?)"
            );

    private static final Pattern RUPEE_PATTERN =
            Pattern.compile(
                    "₹\\s*([0-9]+(?:\\.[0-9]+)?)"
            );

    // ============================================================
    // MAIN METHOD
    // ============================================================

    public List<DividendResponse> getUpcomingDividends(
            DividendRequest request
    ) throws Exception {

        // ========================================================
        // 1. READ REQUEST DATES
        // ========================================================

        String requestedFromDate =
                request != null
                        ? request.getFromDate()
                        : null;

        String requestedToDate =
                request != null
                        ? request.getToDate()
                        : null;

        // ========================================================
        // 2. FROM DATE
        // ========================================================

        LocalDate fromDateValue;

        if (isEmpty(requestedFromDate)) {

            fromDateValue =
                    LocalDate.now(INDIA_ZONE)
                            .plusDays(1);

        } else {

            fromDateValue =
                    parseDate(
                            requestedFromDate,
                            "from_date"
                    );
        }

        // ========================================================
        // 3. TO DATE
        // ========================================================

        LocalDate toDateValue;

        if (isEmpty(requestedToDate)) {

            toDateValue =
                    fromDateValue.plusDays(
                            ACTIVE_PRICE_WINDOW_DAYS
                    );

        } else {

            toDateValue =
                    parseDate(
                            requestedToDate,
                            "to_date"
                    );
        }

        // ========================================================
        // 4. VALIDATE DATE RANGE
        // ========================================================

        if (toDateValue.isBefore(fromDateValue)) {

            throw new IllegalArgumentException(
                    "to_date cannot be earlier than from_date"
            );
        }

        // ========================================================
        // 5. CURRENT IST DATE
        // ========================================================

        LocalDate today =
                LocalDate.now(INDIA_ZONE);

        // ========================================================
        // 6. CHECK TRADING TIME
        // ========================================================

        boolean tradingHours =
                isTradingHours();

        System.out.println(
                "================================================"
        );

        System.out.println(
                "Dividend request received"
        );

        System.out.println(
                "From date: " + fromDateValue
        );

        System.out.println(
                "To date: " + toDateValue
        );

        System.out.println(
                "IST time: "
                        + LocalDateTime.now(INDIA_ZONE)
        );

        System.out.println(
                "Trading hours: "
                        + tradingHours
        );

        System.out.println(
                "================================================"
        );

        // ========================================================
        // 7. LOAD CACHE
        // ========================================================

        CacheFile cache =
                loadCache();

        boolean cacheChanged =
                removeExpiredCacheRecords(
                        cache,
                        today
                );

        // ========================================================
        // 8. DETERMINE CACHE COVERAGE
        // ========================================================

        boolean requestInsideNormalWindow =
                isInsideActiveWindow(
                        fromDateValue,
                        toDateValue,
                        today
                );

        // ========================================================
        // 9. CACHE DOES NOT EXIST / REQUEST OUTSIDE CACHE WINDOW
        // ========================================================

        if (!requestInsideNormalWindow
                || cache.dividends.isEmpty()) {

            System.out.println(
                    "Cache does not fully cover request."
            );

            System.out.println(
                    "Fetching NSE + Groww dividend data..."
            );

            List<DividendData> freshDividendData =
                    fetchDividendDataFromApis(
                            fromDateValue,
                            toDateValue
                    );

            if (!freshDividendData.isEmpty()) {

                for (DividendData fresh :
                        freshDividendData) {

                    if (fresh == null
                            || isEmpty(fresh.symbol)) {

                        continue;
                    }

                    boolean changed =
                            addOrUpdateDividendInCache(
                                    cache,
                                    fresh
                            );

                    if (changed) {

                        cacheChanged = true;
                    }
                }
            }
        }

        // ========================================================
        // 10. MOCK DATA
        // ========================================================

        /*
         * IMPORTANT:
         *
         * MockData is now handled in two ways:
         *
         * 1. If MockData.hasMockData() == true:
         *      Existing mock records are merged into cache.
         *
         * 2. If MockData.hasMockData() == false:
         *      Any previously cached MOCK records are removed.
         *
         * This allows old mock records to be automatically
         * removed from dividend-cache.json after MockData.java
         * is cleared.
         *
         * If MOCK records are removed, cacheChanged becomes true.
         * Later saveCache(cache, true) will increment the version.
         */

        if (MockData.hasMockData()) {

            System.out.println(
                    "MockData detected."
            );

            List<DividendData> mockDividendShares =
                    convertMockDataToDividendData();

            for (DividendData mock :
                    mockDividendShares) {

                if (mock == null) {

                    continue;
                }

                boolean changed =
                        mergeSingleDividendIntoCache(
                                cache,
                                mock
                        );

                if (changed) {

                    cacheChanged = true;
                }
            }

        } else {

            /*
             * MockData.java currently contains no mock data.
             *
             * Remove any MOCK records that were previously
             * stored in the persistent cache.
             */
            boolean mockRecordsRemoved =
                    removeCachedMockRecords(
                            cache
                    );

            if (mockRecordsRemoved) {

                cacheChanged = true;
            }
        }

        // ========================================================
        // 11. SAVE CACHE AFTER SYNCHRONOUS DIVIDEND PROCESSING
        // ========================================================

        /*
         * IMPORTANT:
         *
         * cacheChanged means the actual frontend-visible
         * dividend data changed.
         *
         * Therefore saveCache(..., true) increments the version.
         */
        if (cacheChanged) {

            saveCache(
                    cache,
                    true
            );
        }

        // ========================================================
        // 12. START BACKGROUND DIVIDEND REFRESH
        // ========================================================

        startBackgroundDividendRefreshIfNeeded(
                cache
        );

        // ========================================================
        // 13. FIND RECORDS FOR REQUEST
        // ========================================================

        List<DividendData> requestedShares =
                getSharesForRequestedDateRange(
                        cache.dividends,
                        fromDateValue,
                        toDateValue
                );

        // ========================================================
        // 14. PRICE UPDATE LOGIC
        // ========================================================

        boolean priceChanged = false;

        // ========================================================
        // NORMAL ACTIVE-WINDOW PRICE REFRESH
        // ========================================================

        if (tradingHours) {

            System.out.println(
                    "Inside trading hours."
            );

            System.out.println(
                    "Checking cached prices for active 30-day window..."
            );

            List<DividendData> sharesForPriceRefresh =
                    getSharesForActivePriceRefresh(
                            cache.dividends,
                            today
                    );

            if (!sharesForPriceRefresh.isEmpty()) {

                boolean changed =
                        fetchYahooPricesInParallel(
                                sharesForPriceRefresh
                        );

                if (changed) {

                    priceChanged = true;
                }
            }

        } else {

            System.out.println(
                    "Outside trading hours / weekend."
            );

            System.out.println(
                    "Existing cached prices will be reused."
            );

            List<DividendData> sharesWithoutPrice =
                    getSharesWithoutPrice(
                            requestedShares
                    );

            if (!sharesWithoutPrice.isEmpty()) {

                System.out.println(
                        "Found "
                                + sharesWithoutPrice.size()
                                + " share(s) without cached price."
                );

                boolean changed =
                        fetchYahooPricesInParallel(
                                sharesWithoutPrice
                        );

                if (changed) {

                    priceChanged = true;
                }
            }
        }

        // ========================================================
        // 15. SAVE UPDATED PRICE DATA
        // ========================================================

        /*
         * Yahoo price changes are frontend-visible changes.
         *
         * Therefore the version must also increment here.
         */
        if (priceChanged) {

            saveCache(
                    cache,
                    true
            );
        }

        // ========================================================
        // 16. READ REQUEST DATA AGAIN FROM CACHE
        // ========================================================

        requestedShares =
                getSharesForRequestedDateRange(
                        cache.dividends,
                        fromDateValue,
                        toDateValue
                );

        // ========================================================
        // 17. REMOVE INVALID NORMAL RECORDS
        // ========================================================

        requestedShares.removeIf(
                share -> {

                    if (share == null) {

                        return true;
                    }

                    if ("MOCK".equalsIgnoreCase(
                            share.source
                    )) {

                        return false;
                    }

                    return isEmpty(
                            share.currentSharePrice
                    )
                            || "N/A".equalsIgnoreCase(
                            share.currentSharePrice
                    );
                }
        );

        // ========================================================
        // 18. CREATE RESPONSE
        // ========================================================

        List<DividendResponse> response =
                new ArrayList<>(
                        requestedShares.size()
                );

        for (DividendData share :
                requestedShares) {

            if (share == null) {

                continue;
            }

            Double dividendAmount =
                    parseDoubleOrNull(
                            share.dividendAmount
                    );

            Double currentPrice =
                    parseDoubleOrNull(
                            share.currentSharePrice
                    );

            Double previousPrice =
                    parseDoubleOrNull(
                            share.chartPreviousClose
                    );

            if (currentPrice == null
                    && !"MOCK".equalsIgnoreCase(
                    share.source
            )) {

                continue;
            }

            response.add(
                    new DividendResponse(
                            share.shareName,
                            share.symbol,
                            share.exDate,
                            dividendAmount,
                            currentPrice,
                            previousPrice
                    )
            );
        }

        System.out.println(
                "Returning "
                        + response.size()
                        + " dividend record(s) to frontend."
        );

        System.out.println(
                "================================================"
        );

        return response;
    }

    // ============================================================
    // REMOVE CACHED MOCK RECORDS
    // ============================================================

    /*
     * Removes all previously cached records whose source is MOCK.
     *
     * This is intentionally separate from removeExpiredCacheRecords().
     *
     * A MOCK record should be removed when MockData.java no longer
     * contains mock data, even if its ex-date is still in the future.
     *
     * Example:
     *
     * Old MockData:
     *
     *     TIM
     *     31-Aug-2026
     *
     * Current date:
     *
     *     29-Aug-2026
     *
     * The record is NOT expired yet, but it should still be removed
     * because MockData has been cleared.
     */
    private boolean removeCachedMockRecords(
            CacheFile cache
    ) {

        if (cache == null
                || cache.dividends == null
                || cache.dividends.isEmpty()) {

            return false;
        }

        int oldSize =
                cache.dividends.size();

        cache.dividends.removeIf(
                share -> {

                    if (share == null) {

                        return false;
                    }

                    return "MOCK".equalsIgnoreCase(
                            share.source
                    );
                }
        );

        int removedCount =
                oldSize - cache.dividends.size();

        if (removedCount > 0) {

            System.out.println(
                    "================================================"
            );

            System.out.println(
                    "Removed "
                            + removedCount
                            + " old MOCK record(s) from cache."
            );

            System.out.println(
                    "MockData.java contains no active mock data."
            );

            System.out.println(
                    "Cache will be saved and version will be incremented."
            );

            System.out.println(
                    "================================================"
            );

            return true;
        }

        return false;
    }

    // ============================================================
    // CACHE VERSION
    // ============================================================

    /**
     * Returns the current cache version.
     *
     * This method is intentionally lightweight.
     *
     * It only reads the version from dividend-cache.json.
     *
     * It does NOT:
     * - call NSE
     * - call Groww
     * - call Yahoo
     * - refresh dividend data
     * - refresh prices
     * - modify the cache
     *
     * The frontend can safely call this every 1 minute.
     */
    public String getCacheVersion() {

        CacheFile cache =
                loadCache();

        /*
         * Old cache files created before the version feature
         * may have version = 0.
         *
         * Treat them as version 1.
         */
        if (cache.version <= 0) {

            return "1";
        }

        return String.valueOf(
                cache.version
        );
    }

    // ============================================================
    // BACKGROUND DIVIDEND REFRESH
    // ============================================================

    private void startBackgroundDividendRefreshIfNeeded(
            CacheFile currentCache
    ) {

        if (currentCache == null) {

            return;
        }

        if (!shouldRefreshDividendApis(
                currentCache
        )) {

            return;
        }

        if (!BACKGROUND_REFRESH_RUNNING.compareAndSet(
                false,
                true
        )) {

            System.out.println(
                    "Dividend background refresh is already running."
            );

            return;
        }

        /*
         * Mark the API refresh attempt immediately.
         *
         * IMPORTANT:
         *
         * This changes only cache metadata.
         *
         * Therefore saveCache(..., false) is used.
         *
         * The frontend version does NOT change merely because
         * NSE/Groww was checked.
         */
        synchronized (CACHE_LOCK) {

            CacheFile latestCache =
                    loadCache();

            latestCache.lastDividendApiRefresh =
                    LocalDateTime.now(
                            INDIA_ZONE
                    ).toString();

            saveCache(
                    latestCache,
                    false
            );
        }

        CompletableFuture.runAsync(
                () -> {

                    try {

                        performBackgroundDividendRefresh();

                    } catch (Exception e) {

                        System.out.println(
                                "Background dividend refresh failed: "
                                        + e.getMessage()
                        );

                    } finally {

                        BACKGROUND_REFRESH_RUNNING.set(
                                false
                        );
                    }
                }
        );
    }

    // ============================================================
    // CHECK DIVIDEND REFRESH TIME
    // ============================================================

    private boolean shouldRefreshDividendApis(
            CacheFile cache
    ) {

        if (cache == null) {

            return true;
        }

        if (isEmpty(
                cache.lastDividendApiRefresh
        )) {

            return true;
        }

        try {

            LocalDateTime lastRefresh =
                    LocalDateTime.parse(
                            cache.lastDividendApiRefresh
                    );

            LocalDateTime nextRefresh =
                    lastRefresh.plusMinutes(
                            DIVIDEND_REFRESH_INTERVAL_MINUTES
                    );

            return !LocalDateTime.now(
                    INDIA_ZONE
            ).isBefore(
                    nextRefresh
            );

        } catch (Exception e) {

            return true;
        }
    }

    // ============================================================
    // PERFORM BACKGROUND DIVIDEND REFRESH
    // ============================================================

    private void performBackgroundDividendRefresh() {

        LocalDate today =
                LocalDate.now(
                        INDIA_ZONE
                );

        LocalDate refreshFromDate =
                today.plusDays(1);

        LocalDate refreshToDate =
                refreshFromDate.plusDays(
                        ACTIVE_PRICE_WINDOW_DAYS
                );

        System.out.println(
                "------------------------------------------------"
        );

        System.out.println(
                "Background dividend refresh started."
        );

        System.out.println(
                "Refreshing dividend window: "
                        + refreshFromDate
                        + " -> "
                        + refreshToDate
        );

        List<DividendData> freshDividendData =
                fetchDividendDataFromApis(
                        refreshFromDate,
                        refreshToDate
                );

        if (freshDividendData == null
                || freshDividendData.isEmpty()) {

            System.out.println(
                    "Background dividend refresh found no new data."
            );

            System.out.println(
                    "------------------------------------------------"
            );

            return;
        }

        CacheFile cache =
                loadCache();

        boolean cacheChanged = false;

        List<DividendData> newlyAddedDividends =
                new ArrayList<>();

        for (DividendData fresh :
                freshDividendData) {

            if (fresh == null
                    || isEmpty(fresh.symbol)) {

                continue;
            }

            /*
             * Check whether the record already existed BEFORE
             * merging it.
             *
             * This allows us to distinguish:
             *
             * new dividend
             * vs
             * updated existing dividend.
             */
            DividendData existingBeforeMerge =
                    findCachedDividend(
                            cache,
                            fresh
                    );

            boolean wasNewRecord =
                    existingBeforeMerge == null;

            boolean changed =
                    addOrUpdateDividendInCache(
                            cache,
                            fresh
                    );

            if (changed) {

                cacheChanged = true;

                if (wasNewRecord) {

                    DividendData cached =
                            findCachedDividend(
                                    cache,
                                    fresh
                            );

                    if (cached != null) {

                        newlyAddedDividends.add(
                                cached
                        );
                    }
                }
            }
        }

        // ========================================================
        // SAVE CHANGED DIVIDEND DATA
        // ========================================================

        if (cacheChanged) {

            /*
             * Dividend data changed.
             *
             * Therefore increment the version.
             */
            saveCache(
                    cache,
                    true
            );
        }

        // ========================================================
        // NEW DIVIDEND PRICE LOOKUP
        // ========================================================

        if (!newlyAddedDividends.isEmpty()) {

            System.out.println(
                    "New dividend records discovered: "
                            + newlyAddedDividends.size()
            );

            if (isTradingHours()) {

                /*
                 * During trading hours the normal price refresh
                 * will handle active shares.
                 */
                System.out.println(
                        "Trading hours active. Normal Yahoo refresh will handle prices."
                );

            } else {

                /*
                 * Outside trading hours:
                 *
                 * Only new shares without a cached price are
                 * sent to Yahoo.
                 */
                List<DividendData> newSharesWithoutPrice =
                        new ArrayList<>();

                for (DividendData share :
                        newlyAddedDividends) {

                    if (share == null
                            || isEmpty(
                            share.symbol
                    )) {

                        continue;
                    }

                    if ("MOCK".equalsIgnoreCase(
                            share.source
                    )) {

                        continue;
                    }

                    if (isEmpty(
                            share.currentSharePrice
                    )
                            || "N/A".equalsIgnoreCase(
                            share.currentSharePrice
                    )) {

                        newSharesWithoutPrice.add(
                                share
                        );
                    }
                }

                if (!newSharesWithoutPrice.isEmpty()) {

                    System.out.println(
                            "Outside trading hours."
                    );

                    System.out.println(
                            "Yahoo will be called only for newly discovered shares without price."
                    );

                    boolean priceChanged =
                            fetchYahooPricesInParallel(
                                    newSharesWithoutPrice
                            );

                    if (priceChanged) {

                        /*
                         * Yahoo price changed.
                         *
                         * Increment version because the frontend
                         * consumes the current price.
                         */
                        saveCache(
                                cache,
                                true
                        );
                    }
                }
            }
        }

        System.out.println(
                "Background dividend refresh completed."
        );

        System.out.println(
                "------------------------------------------------"
        );
    }

    // ============================================================
    // FETCH DIVIDEND APIS
    // ============================================================

    private List<DividendData>
    fetchDividendDataFromApis(
            LocalDate fromDateValue,
            LocalDate toDateValue
    ) {

        List<DividendData> result =
                new ArrayList<>();

        String nseFromDate =
                fromDateValue.format(
                        API_DATE_FORMAT
                );

        String nseToDate =
                toDateValue.format(
                        API_DATE_FORMAT
                );

        String growwFromDate =
                fromDateValue.toString();

        String growwToDate =
                toDateValue.toString();

        String nseApiUrl =
                "https://www.nseindia.com/api/"
                        + "corporates-corporateActions"
                        + "?index=equities"
                        + "&from_date=" + nseFromDate
                        + "&to_date=" + nseToDate
                        + "&category=dividend";

        String growwApiUrl =
                "https://groww.in/v1/api/"
                        + "stocks_data/equity_feature/v2/"
                        + "corporate_action/event"
                        + "?from=" + growwFromDate
                        + "&to=" + growwToDate;

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        try {

            CompletableFuture<String> nseFuture =
                    CompletableFuture.supplyAsync(
                            () -> {

                                try {

                                    return fetchNseResponse(
                                            nseApiUrl
                                    );

                                } catch (Exception e) {

                                    System.out.println(
                                            "NSE API failed: "
                                                    + e.getMessage()
                                    );

                                    return null;
                                }

                            },
                            executor
                    );

            CompletableFuture<String> growwFuture =
                    CompletableFuture.supplyAsync(
                            () -> {

                                try {

                                    return fetchGrowwResponse(
                                            growwApiUrl
                                    );

                                } catch (Exception e) {

                                    System.out.println(
                                            "Groww API failed: "
                                                    + e.getMessage()
                                    );

                                    return null;
                                }

                            },
                            executor
                    );

            CompletableFuture.allOf(
                    nseFuture,
                    growwFuture
            ).join();

            String nseResponse =
                    nseFuture.join();

            String growwResponse =
                    growwFuture.join();

            List<DividendData> nseDividendShares =
                    nseResponse == null
                            ? new ArrayList<>()
                            : extractNseDividendShares(
                            nseResponse,
                            fromDateValue,
                            toDateValue
                    );

            List<DividendData> growwDividendShares =
                    growwResponse == null
                            ? new ArrayList<>()
                            : extractGrowwDividendShares(
                            growwResponse,
                            fromDateValue,
                            toDateValue
                    );

            result =
                    mergeAndRemoveDuplicates(
                            nseDividendShares,
                            growwDividendShares
                    );

        } finally {

            executor.shutdown();
        }

        return result;
    }

    // ============================================================
    // CACHE
    // ============================================================

    private CacheFile loadCache() {

        synchronized (CACHE_LOCK) {

            File cacheFile =
                    getCacheFile();

            if (!cacheFile.exists()) {

                System.out.println(
                        "Cache file does not exist."
                );

                System.out.println(
                        "A new cache will be created."
                );

                return new CacheFile();
            }

            try (
                    FileReader reader =
                            new FileReader(cacheFile)
            ) {

                CacheFile cache =
                        GSON.fromJson(
                                reader,
                                CacheFile.class
                        );

                if (cache == null) {

                    return new CacheFile();
                }

                if (cache.dividends == null) {

                    cache.dividends =
                            new ArrayList<>();
                }

                /*
                 * Backward compatibility:
                 *
                 * Older dividend-cache.json files do not have
                 * a version field.
                 *
                 * Gson therefore gives version = 0.
                 *
                 * We treat those files as version 1.
                 */
                if (cache.version <= 0) {

                    cache.version = 1;
                }

                return cache;

            } catch (Exception e) {

                System.out.println(
                        "Cache file could not be read: "
                                + e.getMessage()
                );

                return new CacheFile();
            }
        }
    }

    // ============================================================
    // SAVE CACHE
    // ============================================================

    /**
     * Saves the cache.
     *
     * @param cache cache object
     * @param dataChanged true when frontend-visible data changed
     */
    private void saveCache(
            CacheFile cache,
            boolean dataChanged
    ) {

        synchronized (CACHE_LOCK) {

            try {

                if (cache == null) {

                    return;
                }

                /*
                 * Make sure version always has a valid value.
                 */
                if (cache.version <= 0) {

                    cache.version = 1;
                }

                /*
                 * Only increment the version when actual
                 * frontend-visible data has changed.
                 *
                 * Examples:
                 *
                 * - new dividend
                 * - dividend amount changed
                 * - expired dividend removed
                 * - current price changed
                 * - previous close changed
                 *
                 * Metadata-only changes do NOT increment it.
                 */
                if (dataChanged) {

                    cache.version++;
                }

                /*
                 * lastUpdated is only metadata.
                 *
                 * It does NOT itself cause a version increment.
                 */
                cache.lastUpdated =
                        LocalDateTime.now(
                                INDIA_ZONE
                        ).toString();

                File directory =
                        new File(
                                CACHE_DIRECTORY
                        );

                if (!directory.exists()) {

                    boolean created =
                            directory.mkdirs();

                    if (!created
                            && !directory.exists()) {

                        System.out.println(
                                "Could not create cache directory."
                        );

                        return;
                    }
                }

                File cacheFile =
                        getCacheFile();

                File temporaryFile =
                        new File(
                                CACHE_DIRECTORY,
                                CACHE_FILE_NAME
                                        + ".tmp"
                        );

                try (
                        FileWriter writer =
                                new FileWriter(
                                        temporaryFile
                                )
                ) {

                    GSON.toJson(
                            cache,
                            writer
                    );
                }

                if (cacheFile.exists()) {

                    if (!cacheFile.delete()) {

                        System.out.println(
                                "Could not delete old cache file."
                        );

                        return;
                    }
                }

                if (!temporaryFile.renameTo(
                        cacheFile
                )) {

                    System.out.println(
                            "Could not rename temporary cache file."
                    );

                    return;
                }

                System.out.println(
                        "Cache saved successfully."
                );

                System.out.println(
                        "Cache version: "
                                + cache.version
                );

                if (dataChanged) {

                    System.out.println(
                            "Frontend-visible cache data changed."
                    );

                    System.out.println(
                            "Cache version incremented."
                    );

                } else {

                    System.out.println(
                            "Only cache metadata changed."
                    );

                    System.out.println(
                            "Cache version was not incremented."
                    );
                }

            } catch (Exception e) {

                System.out.println(
                        "Cache save failed: "
                                + e.getMessage()
                );
            }
        }
    }

    // ============================================================
    // CACHE FILE PATH
    // ============================================================

    private File getCacheFile() {

        return new File(
                CACHE_DIRECTORY,
                CACHE_FILE_NAME
        );
    }

    // ============================================================
    // REMOVE EXPIRED DATA
    // ============================================================

    private boolean removeExpiredCacheRecords(
            CacheFile cache,
            LocalDate today
    ) {

        if (cache == null
                || cache.dividends == null
                || cache.dividends.isEmpty()) {

            return false;
        }

        int oldSize =
                cache.dividends.size();

        cache.dividends.removeIf(
                share -> {

                    if (share == null
                            || isEmpty(share.exDate)) {

                        return true;
                    }

                    LocalDate exDate =
                            parseCachedExDate(
                                    share.exDate
                            );

                    if (exDate == null) {

                        return false;
                    }

                    return exDate.isBefore(today);
                }
        );

        int newSize =
                cache.dividends.size();

        if (oldSize != newSize) {

            System.out.println(
                    "Removed "
                            + (oldSize - newSize)
                            + " expired dividend record(s) from cache."
            );

            /*
             * This is a frontend-visible data change.
             *
             * The caller will save with dataChanged = true,
             * which increments the version.
             */
            return true;
        }

        return false;
    }

    // ============================================================
    // CHECK ACTIVE 30-DAY WINDOW
    // ============================================================

    private boolean isInsideActiveWindow(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate today
    ) {

        LocalDate activeStart =
                today.plusDays(1);

        LocalDate activeEnd =
                today.plusDays(
                        ACTIVE_PRICE_WINDOW_DAYS
                );

        return !fromDate.isBefore(activeStart)
                && !toDate.isAfter(activeEnd);
    }

    // ============================================================
    // GET REQUESTED CACHE DATA
    // ============================================================

    private List<DividendData>
    getSharesForRequestedDateRange(
            List<DividendData> allShares,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        List<DividendData> result =
                new ArrayList<>();

        if (allShares == null
                || allShares.isEmpty()) {

            return result;
        }

        for (DividendData share :
                allShares) {

            if (share == null
                    || isEmpty(share.exDate)) {

                continue;
            }

            LocalDate exDate =
                    parseCachedExDate(
                            share.exDate
                    );

            if (exDate == null) {

                continue;
            }

            if (exDate.isBefore(fromDate)
                    || exDate.isAfter(toDate)) {

                continue;
            }

            result.add(share);
        }

        return result;
    }

    // ============================================================
    // ACTIVE PRICE REFRESH
    // ============================================================

    private List<DividendData>
    getSharesForActivePriceRefresh(
            List<DividendData> allShares,
            LocalDate today
    ) {

        List<DividendData> result =
                new ArrayList<>();

        if (allShares == null
                || allShares.isEmpty()) {

            return result;
        }

        LocalDate activeStart =
                today.plusDays(1);

        LocalDate activeEnd =
                today.plusDays(
                        ACTIVE_PRICE_WINDOW_DAYS
                );

        for (DividendData share :
                allShares) {

            if (share == null
                    || isEmpty(share.exDate)
                    || isEmpty(share.symbol)) {

                continue;
            }

            LocalDate exDate =
                    parseCachedExDate(
                            share.exDate
                    );

            if (exDate == null) {

                continue;
            }

            if (exDate.isBefore(activeStart)
                    || exDate.isAfter(activeEnd)) {

                continue;
            }

            if ("MOCK".equalsIgnoreCase(
                    share.source
            )
                    && !isEmpty(
                    share.currentSharePrice
            )
                    && !"N/A".equalsIgnoreCase(
                    share.currentSharePrice
            )) {

                continue;
            }

            if (!shouldAttemptYahoo(
                    share
            )) {

                continue;
            }

            result.add(share);
        }

        return result;
    }

    // ============================================================
    // SHARES WITHOUT PRICE
    // ============================================================

    private List<DividendData>
    getSharesWithoutPrice(
            List<DividendData> requestedShares
    ) {

        List<DividendData> result =
                new ArrayList<>();

        if (requestedShares == null
                || requestedShares.isEmpty()) {

            return result;
        }

        for (DividendData share :
                requestedShares) {

            if (share == null) {

                continue;
            }

            if ("MOCK".equalsIgnoreCase(
                    share.source
            )) {

                continue;
            }

            if (!isEmpty(
                    share.currentSharePrice
            )
                    && !"N/A".equalsIgnoreCase(
                    share.currentSharePrice
            )) {

                continue;
            }

            if (!shouldAttemptYahoo(
                    share
            )) {

                continue;
            }

            result.add(share);
        }

        return result;
    }

    // ============================================================
    // BACKWARD COMPATIBILITY
    // ============================================================

    private List<DividendData>
    getNewSharesWithoutPrice(
            List<DividendData> requestedShares
    ) {

        return getSharesWithoutPrice(
                requestedShares
        );
    }

    // ============================================================
    // CHECK WHETHER YAHOO SHOULD BE CALLED
    // ============================================================

    private boolean shouldAttemptYahoo(
            DividendData share
    ) {

        if (share == null
                || isEmpty(share.symbol)) {

            return false;
        }

        if ("MOCK".equalsIgnoreCase(
                share.source
        )) {

            return false;
        }

        if ("NOT_FOUND".equalsIgnoreCase(
                share.yahooStatus
        )) {

            if (isEmpty(
                    share.lastYahooAttempt
            )) {

                return true;
            }

            try {

                LocalDateTime lastAttempt =
                        LocalDateTime.parse(
                                share.lastYahooAttempt
                        );

                LocalDateTime retryAfter =
                        lastAttempt.plusDays(
                                YAHOO_NOT_FOUND_RETRY_DAYS
                        );

                boolean retry =
                        !LocalDateTime.now(
                                INDIA_ZONE
                        ).isBefore(
                                retryAfter
                        );

                if (!retry) {

                    System.out.println(
                            "Skipping Yahoo for "
                                    + share.symbol
                                    + " - previously NOT_FOUND."
                    );
                }

                return retry;

            } catch (Exception e) {

                return true;
            }
        }

        // ========================================================
        // IMPORTANT 1-MINUTE PRICE CACHE
        // ========================================================

        if (!isEmpty(
                share.lastYahooAttempt
        )) {

            try {

                LocalDateTime lastAttempt =
                        LocalDateTime.parse(
                                share.lastYahooAttempt
                        );

                LocalDateTime nextAllowedAttempt =
                        lastAttempt.plusMinutes(
                                PRICE_REFRESH_INTERVAL_MINUTES
                        );

                boolean allowed =
                        !LocalDateTime.now(
                                INDIA_ZONE
                        ).isBefore(
                                nextAllowedAttempt
                        );

                if (!allowed) {

                    System.out.println(
                            "Skipping Yahoo for "
                                    + share.symbol
                                    + " - refreshed less than "
                                    + PRICE_REFRESH_INTERVAL_MINUTES
                                    + " minute ago."
                    );
                }

                return allowed;

            } catch (Exception e) {

                return true;
            }
        }

        return true;
    }

    // ============================================================
    // MERGE FRESH DATA INTO CACHE
    // ============================================================

    private boolean mergeFreshDataIntoCache(
            CacheFile cache,
            List<DividendData> freshData
    ) {

        boolean changed = false;

        if (cache == null) {

            return false;
        }

        if (cache.dividends == null) {

            cache.dividends =
                    new ArrayList<>();
        }

        if (freshData == null) {

            return false;
        }

        for (DividendData fresh :
                freshData) {

            if (fresh == null
                    || isEmpty(fresh.symbol)) {

                continue;
            }

            boolean merged =
                    mergeSingleDividendIntoCache(
                            cache,
                            fresh
                    );

            if (merged) {

                changed = true;
            }
        }

        return changed;
    }

    // ============================================================
    // ADD OR UPDATE DIVIDEND
    // ============================================================

    private boolean addOrUpdateDividendInCache(
            CacheFile cache,
            DividendData incoming
    ) {

        if (cache == null
                || incoming == null
                || isEmpty(incoming.symbol)) {

            return false;
        }

        if (cache.dividends == null) {

            cache.dividends =
                    new ArrayList<>();
        }

        String incomingKey =
                createCacheKey(
                        incoming
                );

        for (DividendData existing :
                cache.dividends) {

            if (existing == null) {

                continue;
            }

            String existingKey =
                    createCacheKey(
                            existing
                    );

            if (incomingKey.equals(
                    existingKey
            )) {

                return mergeExistingDividendFields(
                        existing,
                        incoming
                );
            }
        }

        cache.dividends.add(
                incoming
        );

        System.out.println(
                "New dividend added to cache: "
                        + incoming.symbol
                        + " / "
                        + incoming.exDate
        );

        return true;
    }

    // ============================================================
    // MERGE ONE DIVIDEND
    // ============================================================

    private boolean mergeSingleDividendIntoCache(
            CacheFile cache,
            DividendData incoming
    ) {

        if (cache == null
                || incoming == null
                || isEmpty(incoming.symbol)) {

            return false;
        }

        if (cache.dividends == null) {

            cache.dividends =
                    new ArrayList<>();
        }

        String incomingKey =
                createCacheKey(
                        incoming
                );

        for (DividendData existing :
                cache.dividends) {

            if (existing == null) {

                continue;
            }

            String existingKey =
                    createCacheKey(
                            existing
                    );

            if (incomingKey.equals(
                    existingKey
            )) {

                return mergeExistingDividendFields(
                        existing,
                        incoming
                );
            }
        }

        cache.dividends.add(
                incoming
        );

        System.out.println(
                "New dividend added to cache: "
                        + incoming.symbol
                        + " / "
                        + incoming.exDate
        );

        return true;
    }

    // ============================================================
    // MERGE EXISTING DIVIDEND FIELDS
    // ============================================================

    private boolean mergeExistingDividendFields(
            DividendData existing,
            DividendData incoming
    ) {

        if (existing == null
                || incoming == null) {

            return false;
        }

        boolean changed = false;

        if (!isEmpty(
                incoming.shareName
        )
                && !safeEquals(
                existing.shareName,
                incoming.shareName
        )) {

            existing.shareName =
                    incoming.shareName;

            changed = true;
        }

        if (!isEmpty(
                incoming.dividendDetails
        )
                && !safeEquals(
                existing.dividendDetails,
                incoming.dividendDetails
        )) {

            existing.dividendDetails =
                    incoming.dividendDetails;

            changed = true;
        }

        if (!isEmpty(
                incoming.dividendAmount
        )
                && !safeEquals(
                existing.dividendAmount,
                incoming.dividendAmount
        )) {

            existing.dividendAmount =
                    incoming.dividendAmount;

            changed = true;
        }

        if ("MOCK".equalsIgnoreCase(
                incoming.source
        )
                && !isEmpty(
                incoming.currentSharePrice
        )
                && !safeEquals(
                existing.currentSharePrice,
                incoming.currentSharePrice
        )) {

            existing.currentSharePrice =
                    incoming.currentSharePrice;

            changed = true;
        }

        if ("MOCK".equalsIgnoreCase(
                incoming.source
        )
                && !isEmpty(
                incoming.chartPreviousClose
        )
                && !safeEquals(
                existing.chartPreviousClose,
                incoming.chartPreviousClose
        )) {

            existing.chartPreviousClose =
                    incoming.chartPreviousClose;

            changed = true;
        }

        if (isEmpty(existing.source)
                && !isEmpty(incoming.source)) {

            existing.source =
                    incoming.source;

            changed = true;
        }

        return changed;
    }

    // ============================================================
    // FIND CACHED DIVIDEND
    // ============================================================

    private DividendData findCachedDividend(
            CacheFile cache,
            DividendData incoming
    ) {

        if (cache == null
                || incoming == null
                || cache.dividends == null) {

            return null;
        }

        String key =
                createCacheKey(
                        incoming
                );

        for (DividendData share :
                cache.dividends) {

            if (share == null) {

                continue;
            }

            if (key.equals(
                    createCacheKey(
                            share
                    )
            )) {

                return share;
            }
        }

        return null;
    }

    // ============================================================
    // CACHE KEY
    // ============================================================

    private String createCacheKey(
            DividendData data
    ) {

        if (data == null) {

            return "";
        }

        String symbol =
                normalizeSymbol(
                        data.symbol
                );

        String exDate =
                data.exDate == null
                        ? ""
                        : data.exDate.trim();

        return symbol
                + "|"
                + exDate;
    }

    // ============================================================
    // CACHE EX DATE PARSER
    // ============================================================

    private LocalDate parseCachedExDate(
            String date
    ) {

        if (isEmpty(date)) {

            return null;
        }

        String value =
                date.trim();

        try {

            if (value.length() >= 10
                    && value.charAt(4) == '-'
                    && value.charAt(7) == '-') {

                return LocalDate.parse(
                        value.substring(0, 10)
                );
            }

        } catch (Exception ignored) {
        }

        try {

            return LocalDate.parse(
                    value,
                    NSE_RESPONSE_DATE_FORMAT
            );

        } catch (Exception ignored) {
        }

        try {

            return LocalDate.parse(
                    value,
                    API_DATE_FORMAT
            );

        } catch (Exception ignored) {
        }

        return null;
    }

    // ============================================================
    // CONVERT MOCK DATA
    // ============================================================

    private List<DividendData>
    convertMockDataToDividendData() {

        List<DividendData> result =
                new ArrayList<>();

        List<MockData.MockDividend> mockDividends =
                MockData.getMockDividends();

        if (mockDividends == null
                || mockDividends.isEmpty()) {

            return result;
        }

        for (MockData.MockDividend mock :
                mockDividends) {

            if (mock == null) {

                continue;
            }

            if (!hasAnyMockData(mock)) {

                continue;
            }

            DividendData data =
                    new DividendData();

            data.shareName =
                    mock.getShareName();

            data.symbol =
                    normalizeSymbol(
                            mock.getSymbol()
                    );

            data.exDate =
                    mock.getExDate();

            data.dividendAmount =
                    formatMockNumber(
                            mock.getDividendAmount()
                    );

            data.currentSharePrice =
                    formatMockNumber(
                            mock.getCurrentSharePrice()
                    );

            data.chartPreviousClose =
                    formatMockNumber(
                            mock.getPreviousSharePrice()
                    );

            data.source =
                    "MOCK";

            result.add(data);
        }

        return result;
    }

    // ============================================================
    // CHECK MOCK DATA
    // ============================================================

    private boolean hasAnyMockData(
            MockData.MockDividend mock
    ) {

        if (mock == null) {

            return false;
        }

        return !isEmpty(mock.getShareName())
                || !isEmpty(mock.getSymbol())
                || !isEmpty(mock.getExDate())
                || mock.getDividendAmount() != null
                || mock.getCurrentSharePrice() != null
                || mock.getPreviousSharePrice() != null;
    }

    // ============================================================
    // FORMAT MOCK NUMBER
    // ============================================================

    private String formatMockNumber(
            Double value
    ) {

        if (value == null) {

            return null;
        }

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            return null;
        }

        return formatPrice(value);
    }

    // ============================================================
    // PARSE DATE
    // ============================================================

    private LocalDate parseDate(
            String date,
            String fieldName
    ) {

        try {

            return LocalDate.parse(
                    date.trim(),
                    API_DATE_FORMAT
            );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Invalid "
                            + fieldName
                            + ". Expected format: dd-MM-yyyy"
            );
        }
    }

    // ============================================================
    // TRADING HOURS
    // ============================================================

    private boolean isTradingHours() {

        LocalDateTime now =
                LocalDateTime.now(
                        INDIA_ZONE
                );

        DayOfWeek day =
                now.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {

            return false;
        }

        LocalTime time =
                now.toLocalTime();

        return !time.isBefore(
                TRADING_START_TIME
        )
                && !time.isAfter(
                TRADING_END_TIME
        );
    }

    // ============================================================
    // NSE REQUEST
    // ============================================================

    private String fetchNseResponse(
            String apiUrl
    ) throws Exception {

        return fetchResponse(
                apiUrl,
                "https://www.nseindia.com/"
        );
    }

    // ============================================================
    // GROWW REQUEST
    // ============================================================

    private String fetchGrowwResponse(
            String apiUrl
    ) throws Exception {

        return fetchResponse(
                apiUrl,
                "https://groww.in/"
        );
    }

    // ============================================================
    // YAHOO REQUEST
    // ============================================================

    private String fetchYahooResponse(
            String apiUrl
    ) throws Exception {

        return fetchResponse(
                apiUrl,
                "https://finance.yahoo.com/"
        );
    }

    // ============================================================
    // COMMON HTTP REQUEST
    // ============================================================

    private String fetchResponse(
            String apiUrl,
            String referer
    ) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(apiUrl)
                                .openConnection();

        connection.setRequestMethod(
                "GET"
        );

        connection.setConnectTimeout(
                CONNECT_TIMEOUT
        );

        connection.setReadTimeout(
                READ_TIMEOUT
        );

        connection.setUseCaches(
                false
        );

        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) "
                        + "Chrome/139.0.0.0 Safari/537.36"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
        );

        connection.setRequestProperty(
                "Accept-Language",
                "en-US,en;q=0.9"
        );

        if (referer != null) {

            connection.setRequestProperty(
                    "Referer",
                    referer
            );
        }

        try {

            int responseCode =
                    connection.getResponseCode();

            if (responseCode
                    != HttpURLConnection.HTTP_OK) {

                throw new Exception(
                        "API failed. HTTP "
                                + responseCode
                );
            }

            StringBuilder response =
                    new StringBuilder();

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            connection.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while (
                        (line = reader.readLine())
                                != null
                ) {

                    response.append(line);
                }
            }

            return response.toString();

        } finally {

            connection.disconnect();
        }
    }

    // ============================================================
    // NSE PARSER
    // ============================================================

    private List<DividendData>
    extractNseDividendShares(
            String response,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        List<DividendData> dividendShares =
                new ArrayList<>();

        if (isEmpty(response)) {

            return dividendShares;
        }

        try {

            JsonElement root =
                    JsonParser.parseString(
                            response
                    );

            if (!root.isJsonArray()) {

                return dividendShares;
            }

            JsonArray jsonArray =
                    root.getAsJsonArray();

            for (JsonElement element :
                    jsonArray) {

                try {

                    if (element == null
                            || !element.isJsonObject()) {

                        continue;
                    }

                    JsonObject record =
                            element.getAsJsonObject();

                    String subject =
                            getStringValue(
                                    record,
                                    "subject"
                            );

                    String symbol =
                            getStringValue(
                                    record,
                                    "symbol"
                            );

                    String shareName =
                            getStringValue(
                                    record,
                                    "comp"
                            );

                    String exDate =
                            getStringValue(
                                    record,
                                    "exDate"
                            );

                    if (isEmpty(subject)
                            || isEmpty(symbol)
                            || isEmpty(exDate)) {

                        continue;
                    }

                    if (!subject
                            .toLowerCase(
                                    Locale.ENGLISH
                            )
                            .contains("dividend")) {

                        continue;
                    }

                    LocalDate parsedExDate;

                    try {

                        parsedExDate =
                                LocalDate.parse(
                                        exDate.trim(),
                                        NSE_RESPONSE_DATE_FORMAT
                                );

                    } catch (Exception e) {

                        continue;
                    }

                    if (parsedExDate.isBefore(fromDate)
                            || parsedExDate.isAfter(toDate)) {

                        continue;
                    }

                    String dividendAmount =
                            extractDividendAmount(
                                    subject
                            );

                    if ("N/A".equalsIgnoreCase(
                            dividendAmount
                    )) {

                        continue;
                    }

                    DividendData data =
                            new DividendData();

                    data.shareName =
                            shareName;

                    data.symbol =
                            normalizeSymbol(
                                    symbol
                            );

                    data.exDate =
                            exDate.trim();

                    data.dividendDetails =
                            subject;

                    data.dividendAmount =
                            dividendAmount;

                    data.source =
                            "NSE";

                    dividendShares.add(
                            data
                    );

                } catch (Exception e) {

                    System.out.println(
                            "NSE record parsing failed: "
                                    + e.getMessage()
                    );
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "NSE parsing failed: "
                            + e.getMessage()
            );
        }

        return dividendShares;
    }

    // ============================================================
    // GROWW PARSER
    // ============================================================

    private List<DividendData>
    extractGrowwDividendShares(
            String response,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        List<DividendData> dividendShares =
                new ArrayList<>();

        if (isEmpty(response)) {

            return dividendShares;
        }

        try {

            JsonObject root =
                    JsonParser.parseString(
                            response
                    ).getAsJsonObject();

            JsonArray exdateEvents =
                    root.getAsJsonArray(
                            "exdateEvents"
                    );

            if (exdateEvents == null) {

                return dividendShares;
            }

            for (JsonElement element :
                    exdateEvents) {

                try {

                    if (element == null
                            || !element.isJsonObject()) {

                        continue;
                    }

                    JsonObject event =
                            element.getAsJsonObject();

                    String type =
                            getStringValue(
                                    event,
                                    "type"
                            );

                    String corporateEventFilter =
                            getStringValue(
                                    event,
                                    "corporateEventFilter"
                            );

                    if (!"DIVIDEND".equalsIgnoreCase(type)
                            || !"DIVIDEND".equalsIgnoreCase(
                            corporateEventFilter
                    )) {

                        continue;
                    }

                    String shareName =
                            getStringValue(
                                    event,
                                    "companyShortName"
                            );

                    String symbol =
                            getStringValue(
                                    event,
                                    "nseSymbol"
                            );

                    String dividendDetails =
                            getStringValue(
                                    event,
                                    "details"
                            );

                    String exDate =
                            getGrowwExDate(
                                    event
                            );

                    if (isEmpty(shareName)
                            || isEmpty(symbol)
                            || isEmpty(dividendDetails)
                            || isEmpty(exDate)) {

                        continue;
                    }

                    LocalDate parsedExDate =
                            parseGrowwDate(
                                    exDate
                            );

                    if (parsedExDate == null) {

                        continue;
                    }

                    if (parsedExDate.isBefore(fromDate)
                            || parsedExDate.isAfter(toDate)) {

                        continue;
                    }

                    String dividendAmount =
                            extractDividendAmount(
                                    dividendDetails
                            );

                    if ("N/A".equalsIgnoreCase(
                            dividendAmount
                    )) {

                        continue;
                    }

                    DividendData data =
                            new DividendData();

                    data.shareName =
                            shareName;

                    data.symbol =
                            normalizeSymbol(
                                    symbol
                            );

                    data.exDate =
                            parsedExDate.format(
                                    NSE_RESPONSE_DATE_FORMAT
                            );

                    data.dividendDetails =
                            dividendDetails;

                    data.dividendAmount =
                            dividendAmount;

                    data.source =
                            "GROWW";

                    dividendShares.add(
                            data
                    );

                } catch (Exception e) {

                    System.out.println(
                            "Groww record parsing failed: "
                                    + e.getMessage()
                    );
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Groww parsing failed: "
                            + e.getMessage()
            );
        }

        return dividendShares;
    }

    // ============================================================
    // MERGE NSE + GROWW
    // ============================================================

    private List<DividendData>
    mergeAndRemoveDuplicates(
            List<DividendData> nseDividendShares,
            List<DividendData> growwDividendShares
    ) {

        Map<String, DividendData> uniqueShares =
                new LinkedHashMap<>();

        if (nseDividendShares != null) {

            for (DividendData share :
                    nseDividendShares) {

                if (!isValidDividendData(share)) {

                    continue;
                }

                String key =
                        createCacheKey(
                                share
                        );

                uniqueShares.putIfAbsent(
                        key,
                        share
                );
            }
        }

        if (growwDividendShares != null) {

            for (DividendData share :
                    growwDividendShares) {

                if (!isValidDividendData(share)) {

                    continue;
                }

                String key =
                        createCacheKey(
                                share
                        );

                uniqueShares.putIfAbsent(
                        key,
                        share
                );
            }
        }

        return new ArrayList<>(
                uniqueShares.values()
        );
    }

    // ============================================================
    // VALID DIVIDEND DATA
    // ============================================================

    private boolean isValidDividendData(
            DividendData share
    ) {

        return share != null
                && !isEmpty(share.symbol)
                && !isEmpty(share.dividendAmount)
                && !"N/A".equalsIgnoreCase(
                share.dividendAmount
        );
    }

    // ============================================================
    // YAHOO PARALLEL PROCESSING
    // ============================================================

    private boolean fetchYahooPricesInParallel(
            List<DividendData> dividendShares
    ) {

        if (dividendShares == null
                || dividendShares.isEmpty()) {

            return false;
        }

        List<DividendData> sharesForYahoo =
                new ArrayList<>();

        for (DividendData share :
                dividendShares) {

            if (share == null) {

                continue;
            }

            if ("MOCK".equalsIgnoreCase(
                    share.source
            )) {

                continue;
            }

            if (isEmpty(share.symbol)) {

                continue;
            }

            if (!shouldAttemptYahoo(
                    share
            )) {

                continue;
            }

            sharesForYahoo.add(
                    share
            );
        }

        if (sharesForYahoo.isEmpty()) {

            return false;
        }

        System.out.println(
                "Yahoo price requests: "
                        + sharesForYahoo.size()
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        try {

            List<CompletableFuture<Boolean>> futures =
                    new ArrayList<>();

            for (DividendData share :
                    sharesForYahoo) {

                CompletableFuture<Boolean> future =
                        CompletableFuture.supplyAsync(
                                () -> fetchYahooSingleSymbol(
                                        share
                                ),
                                executor
                        );

                futures.add(
                        future
                );
            }

            CompletableFuture.allOf(
                    futures.toArray(
                            new CompletableFuture[0]
                    )
            ).join();

            boolean changed = false;

            for (CompletableFuture<Boolean> future :
                    futures) {

                try {

                    if (Boolean.TRUE.equals(
                            future.join()
                    )) {

                        changed = true;
                    }

                } catch (Exception e) {

                    System.out.println(
                            "Yahoo future failed: "
                                    + e.getMessage()
                    );
                }
            }

            return changed;

        } finally {

            executor.shutdown();
        }
    }

    // ============================================================
    // YAHOO SINGLE SYMBOL
    // ============================================================

    private boolean fetchYahooSingleSymbol(
            DividendData share
    ) {

        if (share == null
                || isEmpty(share.symbol)) {

            return false;
        }

        String symbol =
                normalizeSymbol(
                        share.symbol
                );

        String yahooSymbol =
                symbol + ".NS";

        String encodedYahooSymbol;

        try {

            encodedYahooSymbol =
                    URLEncoder.encode(
                            yahooSymbol,
                            StandardCharsets.UTF_8.name()
                    );

        } catch (Exception e) {

            System.out.println(
                    "Could not encode Yahoo symbol: "
                            + yahooSymbol
            );

            return false;
        }

        String yahooApiUrl =
                "https://query1.finance.yahoo.com/"
                        + "v8/finance/chart/"
                        + encodedYahooSymbol
                        + "?range=1d"
                        + "&interval=1d";

        System.out.println(
                "Calling Yahoo for: "
                        + yahooSymbol
        );

        String attemptTime =
                LocalDateTime.now(
                        INDIA_ZONE
                ).toString();

        /*
         * Store the attempt time before the HTTP request.
         *
         * This prevents repeated requests if Yahoo is slow
         * or temporarily unavailable.
         */
        share.lastYahooAttempt =
                attemptTime;

        try {

            String response =
                    fetchYahooResponse(
                            yahooApiUrl
                    );

            YahooPriceData yahooData =
                    extractYahooChartPrice(
                            response
                    );

            if (yahooData == null
                    || isEmpty(
                    yahooData.currentSharePrice
            )
                    || "N/A".equalsIgnoreCase(
                    yahooData.currentSharePrice
            )) {

                return handleYahooNotFound(
                        share
                );
            }

            boolean changed = false;

            if (!safeEquals(
                    share.currentSharePrice,
                    yahooData.currentSharePrice
            )) {

                share.currentSharePrice =
                        yahooData.currentSharePrice;

                changed = true;
            }

            if (!safeEquals(
                    share.chartPreviousClose,
                    yahooData.chartPreviousClose
            )) {

                share.chartPreviousClose =
                        yahooData.chartPreviousClose;

                changed = true;
            }

            /*
             * Successful Yahoo response clears previous
             * NOT_FOUND state.
             *
             * This is also a cache data change.
             */
            if (!isEmpty(
                    share.yahooStatus
            )) {

                share.yahooStatus =
                        null;

                changed = true;
            }

            System.out.println(
                    "Yahoo price updated: "
                            + symbol
                            + " = "
                            + share.currentSharePrice
                            + " | Previous Close = "
                            + share.chartPreviousClose
            );

            return changed;

        } catch (Exception e) {

            /*
             * Network/API failure is NOT treated as NOT_FOUND.
             *
             * Existing cached prices are preserved.
             */
            System.out.println(
                    "Yahoo request failed for "
                            + yahooSymbol
                            + ": "
                            + e.getMessage()
            );

            return false;
        }
    }

    // ============================================================
    // HANDLE YAHOO NOT FOUND
    // ============================================================

    private boolean handleYahooNotFound(
            DividendData share
    ) {

        if (share == null) {

            return false;
        }

        boolean changed = false;

        /*
         * Only replace the price with N/A if there is no valid
         * cached price already.
         */
        if (isEmpty(
                share.currentSharePrice
        )) {

            share.currentSharePrice =
                    "N/A";

            changed = true;
        }

        if (isEmpty(
                share.chartPreviousClose
        )) {

            share.chartPreviousClose =
                    "N/A";

            changed = true;
        }

        if (!"NOT_FOUND".equalsIgnoreCase(
                share.yahooStatus
        )) {

            share.yahooStatus =
                    "NOT_FOUND";

            changed = true;
        }

        System.out.println(
                "Yahoo price NOT_FOUND for: "
                        + share.symbol
        );

        return changed;
    }

    // ============================================================
    // PARSE YAHOO CHART RESPONSE
    // ============================================================

    private YahooPriceData extractYahooChartPrice(
            String response
    ) {

        if (isEmpty(response)) {

            return null;
        }

        try {

            JsonObject root =
                    JsonParser.parseString(
                            response
                    ).getAsJsonObject();

            if (!root.has("chart")
                    || root.get("chart").isJsonNull()) {

                return null;
            }

            JsonObject chart =
                    root.getAsJsonObject(
                            "chart"
                    );

            if (!chart.has("result")
                    || chart.get("result").isJsonNull()) {

                return null;
            }

            JsonArray results =
                    chart.getAsJsonArray(
                            "result"
                    );

            if (results == null
                    || results.isEmpty()
                    || results.get(0).isJsonNull()) {

                return null;
            }

            JsonObject firstResult =
                    results
                            .get(0)
                            .getAsJsonObject();

            if (!firstResult.has("meta")
                    || firstResult.get("meta").isJsonNull()) {

                return null;
            }

            JsonObject meta =
                    firstResult.getAsJsonObject(
                            "meta"
                    );

            YahooPriceData price =
                    new YahooPriceData();

            price.currentSharePrice =
                    getFormattedNumber(
                            meta,
                            "regularMarketPrice"
                    );

            price.chartPreviousClose =
                    getFormattedNumber(
                            meta,
                            "chartPreviousClose"
                    );

            if ("N/A".equalsIgnoreCase(
                    price.chartPreviousClose
            )) {

                price.chartPreviousClose =
                        getFormattedNumber(
                                meta,
                                "previousClose"
                        );
            }

            if ("N/A".equalsIgnoreCase(
                    price.currentSharePrice
            )) {

                return null;
            }

            return price;

        } catch (Exception e) {

            System.out.println(
                    "Yahoo chart parsing failed: "
                            + e.getMessage()
            );

            return null;
        }
    }

    // ============================================================
    // FORMAT YAHOO NUMBER
    // ============================================================

    private String getFormattedNumber(
            JsonObject jsonObject,
            String key
    ) {

        if (jsonObject == null
                || key == null
                || !jsonObject.has(key)
                || jsonObject.get(key).isJsonNull()) {

            return "N/A";
        }

        try {

            double value =
                    jsonObject
                            .get(key)
                            .getAsDouble();

            if (value <= 0
                    || Double.isNaN(value)
                    || Double.isInfinite(value)) {

                return "N/A";
            }

            return formatPrice(
                    value
            );

        } catch (Exception e) {

            return "N/A";
        }
    }

    // ============================================================
    // GROWW EX DATE
    // ============================================================

    private String getGrowwExDate(
            JsonObject event
    ) {

        try {

            JsonObject pillDto =
                    event.getAsJsonObject(
                            "corporateEventPillDto"
                    );

            if (pillDto == null) {

                return null;
            }

            return getStringValue(
                    pillDto,
                    "primaryDate"
            );

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // PARSE GROWW DATE
    // ============================================================

    private LocalDate parseGrowwDate(
            String date
    ) {

        if (isEmpty(date)) {

            return null;
        }

        String value =
                date.trim();

        if (value.length() >= 10
                && value.charAt(4) == '-'
                && value.charAt(7) == '-') {

            try {

                return LocalDate.parse(
                        value.substring(
                                0,
                                10
                        )
                );

            } catch (Exception ignored) {
            }
        }

        try {

            return LocalDate.parse(
                    value,
                    NSE_RESPONSE_DATE_FORMAT
            );

        } catch (Exception ignored) {
        }

        return null;
    }

    // ============================================================
    // EXTRACT DIVIDEND AMOUNT
    // ============================================================

    private String extractDividendAmount(
            String text
    ) {

        if (isEmpty(text)) {

            return "N/A";
        }

        String normalizedText =
                text
                        .replace(
                                '\u00A0',
                                ' '
                        )
                        .trim();

        Matcher matcher =
                DIVIDEND_PATTERN.matcher(
                        normalizedText
                );

        if (matcher.find()) {

            return matcher.group(1);
        }

        Matcher rupeeMatcher =
                RUPEE_PATTERN.matcher(
                        normalizedText
                );

        if (rupeeMatcher.find()) {

            return rupeeMatcher.group(1);
        }

        return "N/A";
    }

    // ============================================================
    // FORMAT PRICE
    // ============================================================

    private String formatPrice(
            double value
    ) {

        return new DecimalFormat(
                "#,##0.00"
        ).format(value);
    }

    // ============================================================
    // GET JSON STRING
    // ============================================================

    private String getStringValue(
            JsonObject jsonObject,
            String key
    ) {

        if (jsonObject == null
                || key == null
                || !jsonObject.has(key)
                || jsonObject.get(key).isJsonNull()) {

            return null;
        }

        try {

            String value =
                    jsonObject
                            .get(key)
                            .getAsString();

            return isEmpty(value)
                    ? null
                    : value.trim();

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // STRING TO DOUBLE
    // ============================================================

    private Double parseDoubleOrNull(
            String value
    ) {

        if (isEmpty(value)
                || "N/A".equalsIgnoreCase(value)) {

            return null;
        }

        try {

            return Double.parseDouble(
                    value
                            .replace(
                                    ",",
                                    ""
                            )
                            .trim()
            );

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // NORMALIZE SYMBOL
    // ============================================================

    private String normalizeSymbol(
            String symbol
    ) {

        if (symbol == null) {

            return "";
        }

        return symbol
                .trim()
                .toUpperCase();
    }

    // ============================================================
    // SAFE STRING EQUALITY
    // ============================================================

    private boolean safeEquals(
            String first,
            String second
    ) {

        if (first == null
                && second == null) {

            return true;
        }

        if (first == null
                || second == null) {

            return false;
        }

        return first.equals(second);
    }

    // ============================================================
    // EMPTY CHECK
    // ============================================================

    private boolean isEmpty(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }

    // ============================================================
    // YAHOO PRICE DATA
    // ============================================================

    private static class YahooPriceData {

        private String currentSharePrice;

        private String chartPreviousClose;
    }

    // ============================================================
    // DIVIDEND DATA
    // ============================================================

    private static class DividendData {

        private String shareName;

        private String symbol;

        private String exDate;

        private String dividendDetails;

        private String dividendAmount;

        private String currentSharePrice;

        private String chartPreviousClose;

        private String source;

        /*
         * Yahoo price lookup status.
         *
         * null / empty
         * NOT_FOUND
         */
        private String yahooStatus;

        /*
         * Last time Yahoo was contacted for this symbol.
         *
         * This is used for the 1-minute price refresh limit.
         */
        private String lastYahooAttempt;
    }

    // ============================================================
    // CACHE FILE
    // ============================================================

    private static class CacheFile {

        /*
         * ========================================================
         * CACHE VERSION
         * ========================================================
         *
         * This represents the version of the actual data that
         * the frontend consumes.
         *
         * It changes ONLY when frontend-visible data changes.
         *
         * It does NOT change just because the cache file was saved.
         */
        private long version = 1;

        /*
         * Last time the JSON file itself was saved.
         *
         * This is metadata only and does NOT affect version.
         */
        private String lastUpdated;

        /*
         * Last time NSE + Groww dividend APIs were checked.
         *
         * This is metadata only and does NOT affect version.
         */
        private String lastDividendApiRefresh;

        /*
         * Cached dividend records.
         */
        private List<DividendData> dividends =
                new ArrayList<>();
    }
}
