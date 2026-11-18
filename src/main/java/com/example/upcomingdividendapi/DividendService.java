package com.example.upcomingdividendapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendService {

    /*
     * ============================================================
     * HOW THIS SERVICE WORKS
     * ============================================================
     *
     * 1. Users are ALWAYS answered from memory (a lock-free,
     *    immutable snapshot). A request never waits for NSE,
     *    Groww, Yahoo, the disk or Neon. The only exceptions:
     *      - the very first run with no data at all
     *      - a custom date range outside the default 30-day
     *        window (fetched once, then reused for 1 hour)
     *
     * 2. A background tick (every 10 seconds) decides if
     *    anything is due. It only works while someone used the
     *    app in the last 10 minutes or a live tab is open, so
     *    an idle server makes ZERO outside calls.
     *
     * 3. Prices (Yahoo spark, 20 symbols per request):
     *      - market open (09:15-15:35 IST, trading day):
     *          refreshed every 30 seconds
     *      - market closed / weekend / holiday:
     *          fetched ONCE after 15:35 if the stored price is
     *          older than the last close, then never again
     *          until the next session (closing price is final)
     *      - new dividend announced (any time): price fetched
     *        immediately
     *      - weekday holidays are detected automatically from
     *        Yahoo's last-trade time
     *
     * 4. Dividends (NSE + Groww): every 15 minutes during the
     *    day on trading days, hourly otherwise.
     *
     * 5. Every change bumps "version" and is pushed to open
     *    tabs instantly via Server-Sent Events (LiveUpdates).
     *
     * 6. Backup (CacheStore): local file every 30 seconds when
     *    something changed (atomic write), Neon at most every
     *    15 minutes for price-only changes, at the next save
     *    for new dividends.
     */

    // ============================================================
    // CONFIGURATION
    // ============================================================

    /*
     * Maximum number of Yahoo requests running at once.
     */
    private static final int THREAD_COUNT = 4;

    /*
     * Yahoo spark accepts up to 20 symbols per request.
     */
    private static final int YAHOO_SPARK_BATCH_SIZE = 20;

    private static final int CONNECT_TIMEOUT = 10000;

    private static final int READ_TIMEOUT = 15000;

    /*
     * Default window: tomorrow .. tomorrow + 30 days.
     */
    private static final int ACTIVE_PRICE_WINDOW_DAYS = 30;

    /*
     * Dividends kept ready in memory: today .. tomorrow + 90
     * days. Any Search inside this window is answered
     * instantly (no NSE / Groww call while the user waits).
     */
    private static final int CACHE_WINDOW_DAYS = 90;

    /*
     * Shares whose ex-date is more than 31 days away get live
     * prices every 5 minutes (instead of 30 seconds) to keep
     * Yahoo load low. After close they still get the final
     * closing price once.
     */
    private static final int NEAR_TERM_DAYS = 31;

    private static final int FAR_PRICE_REFRESH_SECONDS = 300;

    /*
     * While the market is open, prices older than this are
     * refreshed.
     */
    private static final int LIVE_PRICE_REFRESH_SECONDS = 30;

    /*
     * After a failed Yahoo attempt for a symbol, wait this
     * long before trying that symbol again.
     */
    private static final int YAHOO_FAILURE_BACKOFF_SECONDS = 120;

    /*
     * Symbols Yahoo does not know are retried after 2 days.
     */
    private static final int YAHOO_NOT_FOUND_RETRY_DAYS = 2;

    /*
     * NSE + Groww dividend check intervals.
     */
    private static final int DIVIDEND_REFRESH_DAY_MINUTES = 15;

    private static final int DIVIDEND_REFRESH_NIGHT_MINUTES = 60;

    private static final LocalTime DAYTIME_START =
            LocalTime.of(8, 0);

    private static final LocalTime DAYTIME_END =
            LocalTime.of(20, 0);

    /*
     * After NSE + Groww both fail, retry after 5 minutes.
     */
    private static final long DIVIDEND_FAILURE_BACKOFF_MILLIS =
            5L * 60L * 1000L;

    /*
     * Custom date ranges are re-fetched from NSE + Groww at
     * most once per hour.
     */
    private static final long CUSTOM_RANGE_REFRESH_MILLIS =
            60L * 60L * 1000L;

    /*
     * Background work runs only while the app was used in the
     * last 10 minutes or a live tab is connected.
     */
    private static final long ACTIVITY_WINDOW_MILLIS =
            10L * 60L * 1000L;

    private static final int TICK_SECONDS = 10;

    /*
     * Backup intervals.
     */
    private static final int FILE_SAVE_SECONDS = 30;

    private static final long NEON_PRICE_SAVE_MILLIS =
            15L * 60L * 1000L;

    /*
     * Holiday detection: during market hours (after 09:45),
     * if most quotes' last trade is from an earlier day, the
     * market is closed today.
     */
    private static final LocalTime HOLIDAY_CHECK_AFTER =
            LocalTime.of(9, 45);

    private static final ZoneId INDIA_ZONE =
            MarketClock.IST;

    /*
     * Base URLs (overridable with -D system properties for
     * local testing only).
     */
    private static final String YAHOO_BASE_URL =
            System.getProperty(
                    "yahoo.base.url",
                    "https://query1.finance.yahoo.com"
            );

    private static final String NSE_BASE_URL =
            System.getProperty(
                    "nse.base.url",
                    "https://www.nseindia.com"
            );

    private static final String GROWW_BASE_URL =
            System.getProperty(
                    "groww.base.url",
                    "https://groww.in"
            );

    /*
     * Compact JSON (no pretty printing) for faster saves.
     */
    private static final Gson GSON =
            new GsonBuilder()
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
    // STATE
    // ============================================================

    private final CacheStore cacheStore;

    private final LiveUpdates liveUpdates;

    /*
     * Every change to "cache" happens while holding stateLock.
     * Requests never take this lock: they read "snapshot".
     */
    private final Object stateLock =
            new Object();

    private CacheFile cache =
            new CacheFile();

    /*
     * Immutable view used to answer requests (lock-free).
     */
    private volatile Snapshot snapshot =
            Snapshot.EMPTY;

    private volatile Set<LocalDate> holidays =
            Collections.emptySet();

    private final ReentrantLock dividendRefreshLock =
            new ReentrantLock();

    private final ReentrantLock priceRefreshLock =
            new ReentrantLock();

    private final AtomicBoolean tickQueued =
            new AtomicBoolean(false);

    private final AtomicBoolean fileDirty =
            new AtomicBoolean(false);

    private final AtomicBoolean neonDirty =
            new AtomicBoolean(false);

    private final AtomicBoolean neonUrgent =
            new AtomicBoolean(false);

    private volatile long lastNeonSaveMillis = 0L;

    private volatile long lastActivityMillis =
            System.currentTimeMillis();

    private volatile long lastDividendAttemptMillis = 0L;

    private final Map<String, Long> customRangeFetchedAt =
            new ConcurrentHashMap<>();

    /*
     * Runs background refresh jobs (dividends, prices).
     */
    private final ExecutorService workers =
            Executors.newFixedThreadPool(
                    2,
                    daemonThreads("dividend-worker")
            );

    /*
     * Runs Yahoo spark requests in parallel.
     */
    private final ExecutorService yahooPool =
            Executors.newFixedThreadPool(
                    THREAD_COUNT,
                    daemonThreads("yahoo")
            );

    /*
     * Ticks, backup saves, SSE heartbeats and SSE publishing.
     */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    2,
                    daemonThreads("dividend-scheduler")
            );

    public DividendService(
            CacheStore cacheStore,
            LiveUpdates liveUpdates
    ) {
        this.cacheStore = cacheStore;
        this.liveUpdates = liveUpdates;
    }

    // ============================================================
    // STARTUP / SHUTDOWN
    // ============================================================

    @PostConstruct
    public void start() {

        CacheFile loaded = null;

        String json =
                cacheStore.loadNewest();

        if (json != null) {
            try {
                loaded =
                        GSON.fromJson(
                                json,
                                CacheFile.class
                        );
            } catch (Exception e) {
                System.out.println(
                        "Saved cache could not be parsed: "
                                + e.getMessage()
                );
            }
        }

        if (loaded == null) {
            loaded = new CacheFile();
        }

        if (loaded.dividends == null) {
            loaded.dividends = new ArrayList<>();
        }

        loaded.dividends.removeIf(share -> share == null);

        if (loaded.holidays == null) {
            loaded.holidays = new ArrayList<>();
        }

        if (loaded.version <= 0) {
            loaded.version = 1;
        }

        synchronized (stateLock) {

            cache = loaded;

            refreshHolidaySet();

            boolean changed =
                    removeExpiredCacheRecords(
                            cache,
                            today()
                    );

            if (applyMockData()) {
                changed = true;
            }

            publishSnapshot(changed);
        }

        System.out.println(
                "Dividend service ready with "
                        + snapshot.shares.size()
                        + " cached record(s), version "
                        + snapshot.version
                        + "."
        );

        /*
         * Warm-up: lastActivityMillis = startup time, so the
         * first ticks refresh anything that is out of date
         * before the first visitor arrives.
         */
        scheduler.scheduleWithFixedDelay(
                this::safeTick,
                1,
                TICK_SECONDS,
                TimeUnit.SECONDS
        );

        scheduler.scheduleWithFixedDelay(
                this::persistIfNeeded,
                FILE_SAVE_SECONDS,
                FILE_SAVE_SECONDS,
                TimeUnit.SECONDS
        );

        scheduler.scheduleWithFixedDelay(
                liveUpdates::heartbeat,
                25,
                25,
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    public void stop() {

        scheduler.shutdownNow();
        workers.shutdownNow();
        yahooPool.shutdownNow();

        /*
         * Final backup so nothing is lost on redeploy.
         */
        neonUrgent.set(true);
        persistIfNeeded();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public List<DividendResponse> getUpcomingDividends(
            DividendRequest request
    ) throws Exception {

        String requestedFromDate =
                request != null
                        ? request.getFromDate()
                        : null;

        String requestedToDate =
                request != null
                        ? request.getToDate()
                        : null;

        LocalDate today =
                today();

        LocalDate fromDateValue =
                isEmpty(requestedFromDate)
                        ? today.plusDays(1)
                        : parseDate(requestedFromDate, "from_date");

        LocalDate toDateValue =
                isEmpty(requestedToDate)
                        ? fromDateValue.plusDays(ACTIVE_PRICE_WINDOW_DAYS)
                        : parseDate(requestedToDate, "to_date");

        if (toDateValue.isBefore(fromDateValue)) {
            throw new IllegalArgumentException(
                    "to_date cannot be earlier than from_date"
            );
        }

        touch();

        /*
         * First run ever (no saved data): load synchronously
         * so the first visitor does not get an empty page.
         */
        if (!snapshot.dividendsLoaded) {
            refreshDividends(true);
            refreshPrices(true);
        }

        /*
         * Custom range outside the default window.
         */
        if (!isInsideActiveWindow(
                fromDateValue,
                toDateValue,
                today
        )) {
            ensureCustomRange(
                    fromDateValue,
                    toDateValue
            );
        }

        kick();

        return buildResponse(
                snapshot,
                fromDateValue,
                toDateValue
        );
    }

    public String getCacheVersion() {

        touch();
        kick();

        return String.valueOf(
                snapshot.version
        );
    }

    /**
     * Version + price freshness, used by /version and pushed
     * to live tabs.
     */
    public Map<String, Object> getStatus() {

        Snapshot current =
                snapshot;

        LocalDateTime now =
                MarketClock.now();

        Set<LocalDate> currentHolidays =
                holidays;

        boolean marketOpen =
                MarketClock.isMarketOpen(
                        now,
                        currentHolidays
                );

        LocalDateTime lastClose =
                MarketClock.lastSettledClose(
                        now,
                        currentHolidays
                );

        LocalDateTime pricesAsOf =
                parseDateTime(current.pricesAsOf);

        String priceStatus;

        if (marketOpen) {
            priceStatus = "LIVE";
        } else if (pricesAsOf != null
                && !pricesAsOf.isBefore(lastClose)) {
            priceStatus = "CLOSING";
        } else {
            priceStatus = "UPDATING";
        }

        Map<String, Object> status =
                new LinkedHashMap<>();

        status.put("version", String.valueOf(current.version));
        status.put("pricesAsOf", current.pricesAsOf);
        status.put("marketOpen", marketOpen);
        status.put("priceStatus", priceStatus);
        status.put("lastCloseDate", lastClose.toLocalDate().toString());

        return status;
    }

    /**
     * Called when a browser tab opens the live stream.
     */
    public void onLiveSubscriber() {
        touch();
        kick();
    }

    // ============================================================
    // RESPONSE (from snapshot only)
    // ============================================================

    private List<DividendResponse> buildResponse(
            Snapshot current,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<DividendResponse> response =
                new ArrayList<>();

        for (DividendData share :
                current.shares) {

            LocalDate exDate =
                    parseCachedExDate(share.exDate);

            if (exDate == null
                    || exDate.isBefore(fromDate)
                    || exDate.isAfter(toDate)) {
                continue;
            }

            boolean mock =
                    "MOCK".equalsIgnoreCase(share.source);

            Double currentPrice =
                    parseDoubleOrNull(share.currentSharePrice);

            /*
             * Shares without a price yet are added a few
             * seconds later (pushed to the page live).
             */
            if (currentPrice == null && !mock) {
                continue;
            }

            response.add(
                    new DividendResponse(
                            share.shareName,
                            share.symbol,
                            share.exDate,
                            parseDoubleOrNull(share.dividendAmount),
                            currentPrice,
                            parseDoubleOrNull(share.chartPreviousClose)
                    )
            );
        }

        return response;
    }

    // ============================================================
    // CUSTOM DATE RANGE (outside default window)
    // ============================================================

    private void ensureCustomRange(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String key =
                fromDate + "|" + toDate;

        long nowMillis =
                System.currentTimeMillis();

        Long fetchedAt =
                customRangeFetchedAt.get(key);

        if (fetchedAt != null
                && nowMillis - fetchedAt < CUSTOM_RANGE_REFRESH_MILLIS) {
            return;
        }

        if (customRangeFetchedAt.size() > 200) {
            customRangeFetchedAt.clear();
        }

        customRangeFetchedAt.put(key, nowMillis);

        List<DividendData> fresh =
                fetchDividendDataFromApis(
                        fromDate,
                        toDate
                );

        boolean changed = false;

        synchronized (stateLock) {

            for (DividendData incoming : fresh) {

                if (incoming == null
                        || isEmpty(incoming.symbol)) {
                    continue;
                }

                LocalDate exDate =
                        parseCachedExDate(incoming.exDate);

                /*
                 * Past ex-dates are not kept in the cache.
                 */
                if (exDate == null
                        || exDate.isBefore(today())) {
                    continue;
                }

                if (addOrUpdateDividendInCache(cache, incoming)) {
                    changed = true;
                }
            }

            if (changed) {
                neonUrgent.set(true);
                publishSnapshot(true);
            }
        }

        /*
         * One-time synchronous price fetch for newly found
         * shares in this range.
         */
        if (changed) {
            refreshPrices(true);
        }
    }

    // ============================================================
    // BACKGROUND TICK
    // ============================================================

    private void touch() {
        lastActivityMillis = System.currentTimeMillis();
    }

    /**
     * Runs a tick soon without blocking the caller.
     */
    private void kick() {

        if (!tickQueued.compareAndSet(false, true)) {
            return;
        }

        try {
            scheduler.execute(() -> {
                tickQueued.set(false);
                safeTick();
            });
        } catch (Exception e) {
            tickQueued.set(false);
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            System.out.println(
                    "Background tick failed: "
                            + t.getMessage()
            );
        }
    }

    private void tick() {

        boolean active =
                liveUpdates.hasSubscribers()
                        || System.currentTimeMillis() - lastActivityMillis
                        < ACTIVITY_WINDOW_MILLIS;

        if (!active) {
            return;
        }

        /*
         * Drop records whose ex-date has passed (date change).
         */
        synchronized (stateLock) {
            if (removeExpiredCacheRecords(cache, today())) {
                publishSnapshot(true);
            }
        }

        if (isDividendRefreshDue()
                && !dividendRefreshLock.isLocked()) {
            submit(() -> refreshDividends(false));
        }

        if (hasPricesDue()
                && !priceRefreshLock.isLocked()) {
            submit(() -> refreshPrices(false));
        }
    }

    private void submit(Runnable job) {
        try {
            workers.execute(() -> {
                try {
                    job.run();
                } catch (Throwable t) {
                    System.out.println(
                            "Background job failed: "
                                    + t.getMessage()
                    );
                }
            });
        } catch (Exception ignored) {
            // shutting down
        }
    }

    // ============================================================
    // DIVIDEND REFRESH (NSE + Groww)
    // ============================================================

    private boolean isDividendRefreshDue() {

        long nowMillis =
                System.currentTimeMillis();

        if (nowMillis - lastDividendAttemptMillis
                < DIVIDEND_FAILURE_BACKOFF_MILLIS) {
            return false;
        }

        LocalDateTime lastRefresh =
                parseDateTime(snapshot.lastDividendApiRefresh);

        if (lastRefresh == null) {
            return true;
        }

        LocalDateTime now =
                MarketClock.now();

        boolean daytime =
                MarketClock.isTradingDay(now.toLocalDate(), holidays)
                        && !now.toLocalTime().isBefore(DAYTIME_START)
                        && now.toLocalTime().isBefore(DAYTIME_END);

        int intervalMinutes =
                daytime
                        ? DIVIDEND_REFRESH_DAY_MINUTES
                        : DIVIDEND_REFRESH_NIGHT_MINUTES;

        return !now.isBefore(
                lastRefresh.plusMinutes(intervalMinutes)
        );
    }

    /**
     * @param waitForResult true = first-run load: wait for
     *                      any running refresh instead of
     *                      skipping.
     */
    private void refreshDividends(boolean waitForResult) {

        if (waitForResult) {
            dividendRefreshLock.lock();
        } else if (!dividendRefreshLock.tryLock()) {
            return;
        }

        boolean newRecords = false;

        try {
            if (waitForResult) {
                /*
                 * Another thread may have loaded data while we
                 * waited, or the APIs failed moments ago.
                 */
                if (snapshot.dividendsLoaded
                        || System.currentTimeMillis() - lastDividendAttemptMillis
                        < DIVIDEND_FAILURE_BACKOFF_MILLIS) {
                    return;
                }
            } else if (!isDividendRefreshDue()) {
                return;
            }

            lastDividendAttemptMillis =
                    System.currentTimeMillis();

            /*
             * Same window as isInsideActiveWindow:
             * today .. tomorrow + 90 days.
             */
            LocalDate fromDate =
                    today();

            LocalDate toDate =
                    today().plusDays(1 + CACHE_WINDOW_DAYS);

            List<DividendData> fresh =
                    fetchDividendDataFromApis(
                            fromDate,
                            toDate
                    );

            if (fresh.isEmpty()) {
                System.out.println(
                        "NSE + Groww returned no dividends. "
                                + "Keeping existing data; retry in 5 minutes."
                );
                return;
            }

            synchronized (stateLock) {

                boolean changed = false;

                for (DividendData incoming : fresh) {

                    if (incoming == null
                            || isEmpty(incoming.symbol)) {
                        continue;
                    }

                    boolean isNew =
                            findCachedDividend(cache, incoming) == null;

                    if (addOrUpdateDividendInCache(cache, incoming)) {
                        changed = true;

                        if (isNew) {
                            newRecords = true;
                        }
                    }
                }

                cache.lastDividendApiRefresh =
                        MarketClock.now().toString();

                if (changed) {
                    neonUrgent.set(true);
                }

                publishSnapshot(changed);
            }

        } finally {
            dividendRefreshLock.unlock();
        }

        /*
         * New dividend announced: fetch its price right away.
         */
        if (newRecords) {
            kick();
        }
    }

    // ============================================================
    // PRICE REFRESH (Yahoo)
    // ============================================================

    private boolean hasPricesDue() {

        LocalDateTime now =
                MarketClock.now();

        LocalDate today =
                now.toLocalDate();

        for (DividendData share : snapshot.shares) {
            if (isPriceTarget(share, today)
                    && needsPrice(share, now)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPriceTarget(
            DividendData share,
            LocalDate today
    ) {
        if (share == null
                || isEmpty(share.symbol)
                || "MOCK".equalsIgnoreCase(share.source)) {
            return false;
        }

        LocalDate exDate =
                parseCachedExDate(share.exDate);

        return exDate != null
                && !exDate.isBefore(today);
    }

    /**
     * The core freshness rule.
     */
    private boolean needsPrice(
            DividendData share,
            LocalDateTime now
    ) {
        LocalDateTime lastAttempt =
                parseDateTime(share.lastYahooAttempt);

        if ("NOT_FOUND".equalsIgnoreCase(share.yahooStatus)) {
            return lastAttempt == null
                    || !now.isBefore(
                    lastAttempt.plusDays(YAHOO_NOT_FOUND_RETRY_DAYS)
            );
        }

        LocalDateTime asOf =
                parseDateTime(share.priceAsOf);

        boolean lastAttemptFailed =
                lastAttempt != null
                        && (asOf == null || lastAttempt.isAfter(asOf));

        if (lastAttemptFailed
                && now.isBefore(
                lastAttempt.plusSeconds(YAHOO_FAILURE_BACKOFF_SECONDS)
        )) {
            return false;
        }

        if (!hasPrice(share) || asOf == null) {
            return true;
        }

        Set<LocalDate> currentHolidays =
                holidays;

        if (MarketClock.isMarketOpen(now, currentHolidays)) {

            LocalDate exDate =
                    parseCachedExDate(share.exDate);

            boolean farAway =
                    exDate != null
                            && exDate.isAfter(
                            now.toLocalDate().plusDays(NEAR_TERM_DAYS)
                    );

            int refreshSeconds =
                    farAway
                            ? FAR_PRICE_REFRESH_SECONDS
                            : LIVE_PRICE_REFRESH_SECONDS;

            return !now.isBefore(
                    asOf.plusSeconds(refreshSeconds)
            );
        }

        /*
         * Market closed: only if the price is older than the
         * last settled close (e.g. fetched at 2 PM, now 6 PM).
         */
        return asOf.isBefore(
                MarketClock.lastSettledClose(now, currentHolidays)
        );
    }

    private boolean hasPrice(DividendData share) {
        return !isEmpty(share.currentSharePrice)
                && !"N/A".equalsIgnoreCase(share.currentSharePrice);
    }

    /**
     * @param waitForResult true = caller needs prices now
     *                      (first run / custom range).
     */
    private void refreshPrices(boolean waitForResult) {

        if (waitForResult) {
            priceRefreshLock.lock();
        } else if (!priceRefreshLock.tryLock()) {
            return;
        }

        try {
            LocalDateTime now =
                    MarketClock.now();

            LocalDate today =
                    now.toLocalDate();

            List<String> symbols =
                    new ArrayList<>();

            synchronized (stateLock) {
                for (DividendData share : cache.dividends) {

                    if (!isPriceTarget(share, today)
                            || !needsPrice(share, now)) {
                        continue;
                    }

                    String symbol =
                            normalizeSymbol(share.symbol);

                    if (!symbols.contains(symbol)) {
                        symbols.add(symbol);
                    }
                }
            }

            if (symbols.isEmpty()) {
                return;
            }

            QuoteResult result =
                    fetchQuotes(symbols);

            applyQuotes(
                    symbols,
                    result,
                    now
            );

        } finally {
            priceRefreshLock.unlock();
        }
    }

    private void applyQuotes(
            List<String> requestedSymbols,
            QuoteResult result,
            LocalDateTime attemptTime
    ) {
        String attempt =
                attemptTime.toString();

        Set<String> requested =
                new HashSet<>(requestedSymbols);

        synchronized (stateLock) {

            boolean visibleChange = false;

            for (DividendData share : cache.dividends) {

                if (share == null
                        || isEmpty(share.symbol)) {
                    continue;
                }

                String symbol =
                        normalizeSymbol(share.symbol);

                if (!requested.contains(symbol)) {
                    continue;
                }

                share.lastYahooAttempt = attempt;

                YahooQuote quote =
                        result.quotes.get(symbol);

                if (quote != null) {

                    if (!safeEquals(share.currentSharePrice, quote.price)) {
                        share.currentSharePrice = quote.price;
                        visibleChange = true;
                    }

                    if (!isEmpty(quote.previousClose)
                            && !safeEquals(share.chartPreviousClose, quote.previousClose)) {
                        share.chartPreviousClose = quote.previousClose;
                        visibleChange = true;
                    }

                    if (!isEmpty(share.yahooStatus)) {
                        share.yahooStatus = null;
                        visibleChange = true;
                    }

                    share.priceAsOf = attempt;
                    share.marketTime = quote.marketTime;

                } else if (result.answeredSymbols.contains(symbol)) {

                    /*
                     * Yahoo answered but has no price for it.
                     */
                    if (isEmpty(share.currentSharePrice)) {
                        share.currentSharePrice = "N/A";
                    }

                    if (isEmpty(share.chartPreviousClose)) {
                        share.chartPreviousClose = "N/A";
                    }

                    if (!"NOT_FOUND".equalsIgnoreCase(share.yahooStatus)) {
                        share.yahooStatus = "NOT_FOUND";
                        visibleChange = true;
                    }
                }
            }

            if (!result.quotes.isEmpty()) {
                cache.lastPriceRefresh = attempt;
                detectHoliday(result, attemptTime);
            }

            publishSnapshot(visibleChange);
        }

        System.out.println(
                "Yahoo: "
                        + result.quotes.size()
                        + "/"
                        + requestedSymbols.size()
                        + " price(s) updated in "
                        + result.requestCount
                        + " request(s)."
        );
    }

    /**
     * If the market should be open but most last-trade times
     * are from an earlier day, today is a holiday.
     */
    private void detectHoliday(
            QuoteResult result,
            LocalDateTime now
    ) {
        if (!MarketClock.isMarketOpen(now, holidays)
                || now.toLocalTime().isBefore(HOLIDAY_CHECK_AFTER)) {
            return;
        }

        LocalDate today =
                now.toLocalDate();

        int withTime = 0;
        int stale = 0;

        for (YahooQuote quote : result.quotes.values()) {

            LocalDateTime marketTime =
                    parseDateTime(quote.marketTime);

            if (marketTime == null) {
                continue;
            }

            withTime++;

            if (marketTime.toLocalDate().isBefore(today)) {
                stale++;
            }
        }

        if (withTime >= 5
                && stale * 10 >= withTime * 8) {

            String day =
                    today.toString();

            if (!cache.holidays.contains(day)) {
                cache.holidays.add(day);

                /*
                 * Keep only recent holidays.
                 */
                cache.holidays.removeIf(value -> {
                    LocalDate date = parseCachedExDate(value);
                    return date == null
                            || date.isBefore(today.minusDays(30));
                });

                refreshHolidaySet();

                System.out.println(
                        "Market holiday detected for "
                                + day
                                + ". Live price refresh paused until next session."
                );
            }
        }
    }

    private void refreshHolidaySet() {

        Set<LocalDate> set =
                new HashSet<>();

        for (String value : cache.holidays) {
            LocalDate date =
                    parseCachedExDate(value);

            if (date != null) {
                set.add(date);
            }
        }

        holidays = Collections.unmodifiableSet(set);
    }

    // ============================================================
    // YAHOO SPARK (20 symbols per request)
    // ============================================================

    private QuoteResult fetchQuotes(List<String> symbols) {

        QuoteResult result =
                new QuoteResult();

        List<CompletableFuture<QuoteResult>> futures =
                new ArrayList<>();

        for (int start = 0;
             start < symbols.size();
             start += YAHOO_SPARK_BATCH_SIZE) {

            List<String> chunk =
                    new ArrayList<>(
                            symbols.subList(
                                    start,
                                    Math.min(
                                            start + YAHOO_SPARK_BATCH_SIZE,
                                            symbols.size()
                                    )
                            )
                    );

            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> fetchQuoteChunk(chunk),
                            yahooPool
                    )
            );
        }

        for (CompletableFuture<QuoteResult> future : futures) {
            try {
                QuoteResult part =
                        future.get(60, TimeUnit.SECONDS);

                result.quotes.putAll(part.quotes);
                result.answeredSymbols.addAll(part.answeredSymbols);
                result.requestCount += part.requestCount;

            } catch (Exception e) {
                System.out.println(
                        "Yahoo batch failed: "
                                + e.getMessage()
                );
            }
        }

        return result;
    }

    /**
     * One spark request for up to 20 symbols. If spark fails
     * (blocked / changed), falls back to the per-symbol chart
     * endpoint for this chunk so prices still update.
     */
    private QuoteResult fetchQuoteChunk(List<String> symbols) {

        QuoteResult result =
                new QuoteResult();

        StringBuilder joined =
                new StringBuilder();

        for (String symbol : symbols) {

            if (joined.length() > 0) {
                joined.append(',');
            }

            joined.append(encode(symbol + ".NS"));
        }

        String url =
                YAHOO_BASE_URL
                        + "/v7/finance/spark?symbols="
                        + joined
                        + "&range=1d&interval=1d";

        try {
            result.requestCount++;

            String response =
                    fetchResponse(
                            url,
                            "https://finance.yahoo.com/"
                    );

            parseSpark(response, result);

            /*
             * Spark answered: every requested symbol counts as
             * answered (missing ones are NOT_FOUND).
             */
            result.answeredSymbols.addAll(symbols);

            return result;

        } catch (Exception e) {
            System.out.println(
                    "Yahoo spark failed ("
                            + e.getMessage()
                            + "). Falling back to per-symbol requests."
            );
        }

        for (String symbol : symbols) {
            try {
                result.requestCount++;

                String response =
                        fetchResponse(
                                YAHOO_BASE_URL
                                        + "/v8/finance/chart/"
                                        + encode(symbol + ".NS")
                                        + "?range=1d&interval=1d",
                                "https://finance.yahoo.com/"
                        );

                result.answeredSymbols.add(symbol);

                JsonObject root =
                        JsonParser.parseString(response).getAsJsonObject();

                JsonArray chartResults =
                        root.getAsJsonObject("chart")
                                .getAsJsonArray("result");

                if (chartResults != null
                        && chartResults.size() > 0
                        && chartResults.get(0).isJsonObject()) {

                    YahooQuote quote =
                            parseQuote(chartResults.get(0).getAsJsonObject());

                    if (quote != null) {
                        result.quotes.put(symbol, quote);
                    }
                }

            } catch (Exception e) {
                /*
                 * HTTP 404 = Yahoo does not know the symbol.
                 */
                if (e.getMessage() != null
                        && e.getMessage().contains("HTTP 404")) {
                    result.answeredSymbols.add(symbol);
                }
            }
        }

        return result;
    }

    private void parseSpark(
            String response,
            QuoteResult result
    ) {
        JsonObject root =
                JsonParser.parseString(response).getAsJsonObject();

        JsonObject spark =
                root.getAsJsonObject("spark");

        if (spark == null) {
            throw new IllegalStateException("Unexpected spark response");
        }

        JsonArray results =
                spark.getAsJsonArray("result");

        if (results == null) {
            return;
        }

        for (JsonElement element : results) {

            if (element == null
                    || !element.isJsonObject()) {
                continue;
            }

            JsonObject item =
                    element.getAsJsonObject();

            String yahooSymbol =
                    getStringValue(item, "symbol");

            JsonArray responses =
                    item.getAsJsonArray("response");

            if (yahooSymbol == null
                    || responses == null
                    || responses.size() == 0
                    || !responses.get(0).isJsonObject()) {
                continue;
            }

            YahooQuote quote =
                    parseQuote(responses.get(0).getAsJsonObject());

            if (quote == null) {
                continue;
            }

            String symbol =
                    normalizeSymbol(yahooSymbol);

            if (symbol.endsWith(".NS")) {
                symbol = symbol.substring(0, symbol.length() - 3);
            }

            result.quotes.put(symbol, quote);
        }
    }

    /**
     * Reads price / previous close / last trade time from a
     * spark or chart result ("meta" + "indicators").
     */
    private YahooQuote parseQuote(JsonObject chartResult) {

        JsonObject meta =
                chartResult.has("meta")
                        && chartResult.get("meta").isJsonObject()
                        ? chartResult.getAsJsonObject("meta")
                        : null;

        if (meta == null) {
            return null;
        }

        String price =
                getFormattedNumber(meta, "regularMarketPrice");

        /*
         * Fallback: last valid close in indicators.
         */
        if ("N/A".equalsIgnoreCase(price)) {
            try {
                JsonArray closes =
                        chartResult.getAsJsonObject("indicators")
                                .getAsJsonArray("quote")
                                .get(0).getAsJsonObject()
                                .getAsJsonArray("close");

                for (int i = closes.size() - 1; i >= 0; i--) {
                    if (!closes.get(i).isJsonNull()
                            && closes.get(i).getAsDouble() > 0) {
                        price = formatPrice(closes.get(i).getAsDouble());
                        break;
                    }
                }
            } catch (Exception ignored) {
                // no indicators
            }
        }

        if ("N/A".equalsIgnoreCase(price)) {
            return null;
        }

        String previousClose =
                getFormattedNumber(meta, "chartPreviousClose");

        if ("N/A".equalsIgnoreCase(previousClose)) {
            previousClose =
                    getFormattedNumber(meta, "previousClose");
        }

        YahooQuote quote =
                new YahooQuote();

        quote.price = price;
        quote.previousClose =
                "N/A".equalsIgnoreCase(previousClose)
                        ? null
                        : previousClose;

        try {
            if (meta.has("regularMarketTime")
                    && !meta.get("regularMarketTime").isJsonNull()) {

                long epochSeconds =
                        meta.get("regularMarketTime").getAsLong();

                quote.marketTime =
                        LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(epochSeconds),
                                INDIA_ZONE
                        ).toString();
            }
        } catch (Exception ignored) {
            // optional field
        }

        return quote;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8.name()
            );
        } catch (Exception e) {
            return value;
        }
    }

    // ============================================================
    // SNAPSHOT + BACKUP
    // ============================================================

    /**
     * Must be called while holding stateLock.
     */
    private void publishSnapshot(boolean dataChanged) {

        Snapshot previous =
                snapshot;

        if (dataChanged) {
            cache.version++;
        }

        cache.lastUpdated =
                MarketClock.now().toString();

        List<DividendData> copies =
                new ArrayList<>(cache.dividends.size());

        for (DividendData share : cache.dividends) {
            if (share != null) {
                copies.add(share.copy());
            }
        }

        Snapshot next =
                new Snapshot(
                        Collections.unmodifiableList(copies),
                        cache.version,
                        cache.lastPriceRefresh,
                        cache.lastDividendApiRefresh,
                        !isEmpty(cache.lastDividendApiRefresh)
                                || !cache.dividends.isEmpty()
                );

        snapshot = next;

        fileDirty.set(true);
        neonDirty.set(true);

        boolean statusChanged =
                previous.version != next.version
                        || !safeEquals(previous.pricesAsOf, next.pricesAsOf);

        if (statusChanged
                && liveUpdates.hasSubscribers()) {
            try {
                scheduler.execute(
                        () -> liveUpdates.publish(getStatus())
                );
            } catch (Exception ignored) {
                // shutting down
            }
        }
    }

    private void persistIfNeeded() {

        try {
            if (fileDirty.getAndSet(false)) {
                cacheStore.saveFile(serializeCache());
            }

            if (!cacheStore.isNeonEnabled()
                    || !neonDirty.get()) {
                return;
            }

            long nowMillis =
                    System.currentTimeMillis();

            boolean due =
                    neonUrgent.get()
                            || nowMillis - lastNeonSaveMillis >= NEON_PRICE_SAVE_MILLIS;

            if (!due) {
                return;
            }

            neonDirty.set(false);
            neonUrgent.set(false);

            String json;
            long version;

            synchronized (stateLock) {
                json = GSON.toJson(cache);
                version = cache.version;
            }

            if (cacheStore.saveNeon(json, version)) {
                lastNeonSaveMillis = nowMillis;
            } else {
                neonDirty.set(true);
                lastNeonSaveMillis = nowMillis;
            }

        } catch (Throwable t) {
            System.out.println(
                    "Backup failed: "
                            + t.getMessage()
            );
        }
    }

    private String serializeCache() {
        synchronized (stateLock) {
            return GSON.toJson(cache);
        }
    }

    // ============================================================
    // MOCK DATA (static, applied at startup)
    // ============================================================

    /**
     * Must be called while holding stateLock.
     */
    private boolean applyMockData() {

        if (!MockData.hasMockData()) {
            return removeCachedMockRecords(cache);
        }

        boolean changed = false;

        for (DividendData mock :
                convertMockDataToDividendData()) {

            if (mock != null
                    && mergeSingleDividendIntoCache(cache, mock)) {
                changed = true;
            }
        }

        return changed;
    }

    // ============================================================
    // SMALL HELPERS
    // ============================================================

    private static LocalDate today() {
        return LocalDate.now(INDIA_ZONE);
    }

    private static LocalDateTime parseDateTime(String value) {

        if (value == null
                || value.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static ThreadFactory daemonThreads(String prefix) {

        AtomicInteger counter =
                new AtomicInteger(1);

        return runnable -> {
            Thread thread =
                    new Thread(
                            runnable,
                            prefix + "-" + counter.getAndIncrement()
                    );
            thread.setDaemon(true);
            return thread;
        };
    }

    // ============================================================
    // REMOVE CACHED MOCK RECORDS
    // ============================================================

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
                NSE_BASE_URL + "/api/"
                        + "corporates-corporateActions"
                        + "?index=equities"
                        + "&from_date=" + nseFromDate
                        + "&to_date=" + nseToDate
                        + "&category=dividend";

        String growwApiUrl =
                GROWW_BASE_URL + "/v1/api/"
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

        /*
         * FIX: the cached window is today .. tomorrow + 90 days.
         *
         * Previously it ended at today + 30, one day short of
         * the default request (tomorrow .. tomorrow + 30), so
         * every default load and every Search was treated as a
         * custom range and called NSE + Groww live.
         */
        LocalDate activeStart =
                today;

        LocalDate activeEnd =
                today.plusDays(
                        1 + CACHE_WINDOW_DAYS
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
    // YAHOO QUOTE
    // ============================================================

    private static class YahooQuote {

        private String price;

        private String previousClose;

        /*
         * Last trade time reported by Yahoo (IST).
         */
        private String marketTime;
    }

    private static class QuoteResult {

        private final Map<String, YahooQuote> quotes =
                new HashMap<>();

        /*
         * Symbols Yahoo answered for (with or without a price).
         * Symbols in a failed request are NOT included, so a
         * network error never marks a share NOT_FOUND.
         */
        private final Set<String> answeredSymbols =
                new HashSet<>();

        private int requestCount = 0;
    }

    // ============================================================
    // SNAPSHOT (immutable view served to users)
    // ============================================================

    private static final class Snapshot {

        private static final Snapshot EMPTY =
                new Snapshot(
                        Collections.<DividendData>emptyList(),
                        1,
                        null,
                        null,
                        false
                );

        private final List<DividendData> shares;

        private final long version;

        private final String pricesAsOf;

        private final String lastDividendApiRefresh;

        private final boolean dividendsLoaded;

        private Snapshot(
                List<DividendData> shares,
                long version,
                String pricesAsOf,
                String lastDividendApiRefresh,
                boolean dividendsLoaded
        ) {
            this.shares = shares;
            this.version = version;
            this.pricesAsOf = pricesAsOf;
            this.lastDividendApiRefresh = lastDividendApiRefresh;
            this.dividendsLoaded = dividendsLoaded;
        }
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
         * Yahoo price lookup status: null or NOT_FOUND.
         */
        private String yahooStatus;

        /*
         * Last time Yahoo was contacted for this symbol (IST).
         */
        private String lastYahooAttempt;

        /*
         * Time the current price was fetched (IST).
         * A price fetched after the last settled close
         * (15:35) is the final closing price.
         */
        private String priceAsOf;

        /*
         * Last trade time reported by Yahoo (IST).
         */
        private String marketTime;

        private DividendData copy() {

            DividendData copy =
                    new DividendData();

            copy.shareName = shareName;
            copy.symbol = symbol;
            copy.exDate = exDate;
            copy.dividendDetails = dividendDetails;
            copy.dividendAmount = dividendAmount;
            copy.currentSharePrice = currentSharePrice;
            copy.chartPreviousClose = chartPreviousClose;
            copy.source = source;
            copy.yahooStatus = yahooStatus;
            copy.lastYahooAttempt = lastYahooAttempt;
            copy.priceAsOf = priceAsOf;
            copy.marketTime = marketTime;

            return copy;
        }
    }

    // ============================================================
    // CACHE FILE
    // ============================================================

    private static class CacheFile {

        /*
         * Version of the data the frontend sees. Changes ONLY
         * when frontend-visible data changes.
         */
        private long version = 1;

        /*
         * Last time the cache changed (metadata).
         */
        private String lastUpdated;

        /*
         * Last successful NSE + Groww check.
         */
        private String lastDividendApiRefresh;

        /*
         * Last successful Yahoo price update.
         */
        private String lastPriceRefresh;

        /*
         * Weekday market holidays detected automatically
         * (yyyy-MM-dd), last 30 days only.
         */
        private List<String> holidays =
                new ArrayList<>();

        /*
         * Cached dividend records.
         */
        private List<DividendData> dividends =
                new ArrayList<>();
    }
}
