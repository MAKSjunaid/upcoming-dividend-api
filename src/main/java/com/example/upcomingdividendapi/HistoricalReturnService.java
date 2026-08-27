package com.example.upcomingdividendapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * HISTORICAL PRICE SERVICE
 * ============================================================
 *
 * Responsibilities:
 *
 * 1. Get REAL share symbols from DividendService.
 *
 * 2. Do NOT use MockData.
 *
 * 3. Fetch Yahoo historical data using Spark API.
 *
 * 4. Maximum 20 symbols per Yahoo request.
 *
 * 5. Process multiple batches in parallel.
 *
 * 6. Calculate historical prices for:
 *
 *      5Y
 *      3Y
 *      1Y
 *      6M
 *      3M
 *
 * 7. Return PRICES, NOT percentages.
 *
 * 8. Cache results in memory.
 *
 * 9. Later the Controller can expose these results
 *    directly to the frontend.
 *
 * Example response:
 *
 * [
 *   {
 *     "symbol": "POLYMED.NS",
 *     "5Y": 125.20,
 *     "3Y": 232.00,
 *     "1Y": 125.20,
 *     "6M": 232.00,
 *     "3M": 125.20
 *   },
 *   {
 *     "symbol": "BLS.NS",
 *     "5Y": 343.20,
 *     "3Y": 434.00,
 *     "1Y": 454.20,
 *     "6M": 56.00,
 *     "3M": 545.20
 *   }
 * ]
 *
 * Java 8 compatible.
 *
 * ============================================================
 */
@Service
public class HistoricalReturnService {


    // ============================================================
    // CONFIGURATION
    // ============================================================

    /*
     * Yahoo Spark allows maximum 20 symbols per request.
     */
    private static final int YAHOO_BATCH_SIZE = 20;


    /*
     * Number of Yahoo requests that can run simultaneously.
     */
    private static final int THREAD_COUNT = 8;


    /*
     * HTTP timeout.
     */
    private static final int CONNECT_TIMEOUT = 5000;

    private static final int READ_TIMEOUT = 10000;


    /*
     * Indian timezone.
     */
    private static final ZoneId INDIA_ZONE =
            ZoneId.of("Asia/Kolkata");


    /*
     * Yahoo Spark API.
     *
     * Example:
     *
     * https://query1.finance.yahoo.com/v7/finance/spark
     * ?symbols=MRF.NS,BLS.NS,TCS.NS
     * &range=5y
     * &interval=1d
     */
    private static final String YAHOO_SPARK_URL =
            "https://query1.finance.yahoo.com/v7/finance/spark"
                    + "?symbols=%s"
                    + "&range=5y"
                    + "&interval=1d";


    // ============================================================
    // OBJECT MAPPER
    // ============================================================

    private final ObjectMapper objectMapper =
            new ObjectMapper();


    // ============================================================
    // EXECUTOR
    // ============================================================

    private final ExecutorService executor =
            Executors.newFixedThreadPool(
                    THREAD_COUNT
            );


    // ============================================================
    // CACHE
    // ============================================================

    /*
     * Cache historical results by Yahoo symbol.
     *
     * Example:
     *
     * POLYMED.NS -> HistoricalPriceResponse
     * BLS.NS     -> HistoricalPriceResponse
     */
    private final Map<String, HistoricalPriceResponse> cache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>()
            );


    // ============================================================
    // SPRING INJECTION
    // ============================================================

    private final DividendService dividendService;


    public HistoricalReturnService(
            DividendService dividendService
    ) {

        this.dividendService =
                dividendService;
    }


    // ============================================================
    // GET HISTORICAL PRICES FOR ONE SYMBOL
    // ============================================================

    /**
     * Gets historical prices for one symbol.
     *
     * Example:
     *
     *     getHistoricalPrices("TCS")
     *
     * Returns:
     *
     * {
     *   "symbol": "TCS.NS",
     *   "5Y": 2500.20,
     *   "3Y": 3100.40,
     *   "1Y": 3500.10,
     *   "6M": 3900.25,
     *   "3M": 4100.80
     * }
     */
    public HistoricalPriceResponse getHistoricalPrices(
            String symbol
    ) {

        if (symbol == null
                || symbol.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Symbol cannot be empty"
            );
        }


        String normalizedSymbol =
                normalizeSymbol(symbol);


        /*
         * Convert to Yahoo format.
         *
         * TCS -> TCS.NS
         */
        String yahooSymbol =
                normalizedSymbol + ".NS";


        /*
         * Check cache first.
         */
        HistoricalPriceResponse cached =
                cache.get(yahooSymbol);


        if (cached != null) {

            return cached;
        }


        /*
         * If not cached, fetch directly.
         */
        HistoricalPriceResponse response =
                fetchHistoricalPriceForSymbol(
                        normalizedSymbol
                );


        if (response != null) {

            cache.put(
                    response.getSymbol(),
                    response
            );
        }


        return response;
    }


    // ============================================================
    // LOAD REAL SHARES FROM DIVIDEND SERVICE
    // ============================================================

    /**
     * Gets REAL symbols from DividendService.
     *
     * Then fetches historical prices for all shares.
     *
     * Maximum 20 symbols are sent in one Yahoo request.
     *
     * Example:
     *
     * 100 shares
     *
     * 100 / 20 = 5 Yahoo requests
     *
     * With 8 threads, those batches can execute in parallel.
     */
    public void loadHistoricalDataFromDividendService()
            throws Exception {

        System.out.println();
        System.out.println(
                "=================================================="
        );

        System.out.println(
                "STARTING HISTORICAL PRICE LOAD"
        );

        System.out.println(
                "=================================================="
        );


        // --------------------------------------------------------
        // Create Dividend Request
        // --------------------------------------------------------

        DividendRequest request =
                new DividendRequest();


        /*
         * Your DividendService already handles empty dates.
         *
         * from = tomorrow
         * to   = one month later
         */
        request.setFromDate(null);

        request.setToDate(null);


        // --------------------------------------------------------
        // Get REAL dividend shares
        // --------------------------------------------------------

        List<DividendResponse> dividendResponses =
                dividendService.getUpcomingDividends(
                        request
                );


        if (dividendResponses == null
                || dividendResponses.isEmpty()) {

            System.out.println(
                    "No dividend shares found."
            );

            return;
        }


        // --------------------------------------------------------
        // Extract symbols
        // --------------------------------------------------------

        List<String> symbols =
                new ArrayList<>();


        for (DividendResponse dividend :
                dividendResponses) {

            if (dividend == null) {

                continue;
            }


            String symbol =
                    dividend.getSymbol();


            if (symbol == null
                    || symbol.trim().isEmpty()) {

                continue;
            }


            String normalized =
                    normalizeSymbol(symbol);


            /*
             * Avoid duplicate symbols.
             */
            if (!symbols.contains(normalized)) {

                symbols.add(normalized);
            }
        }


        if (symbols.isEmpty()) {

            System.out.println(
                    "DividendService returned shares, "
                            + "but no valid symbols were found."
            );

            return;
        }


        // --------------------------------------------------------
        // Print symbols
        // --------------------------------------------------------

        System.out.println();

        System.out.println(
                "REAL SHARES RECEIVED FROM DividendService: "
                        + symbols.size()
        );


        for (String symbol :
                symbols) {

            System.out.println(
                    "  " + symbol + ".NS"
            );
        }


        System.out.println();


        // --------------------------------------------------------
        // Fetch historical data
        // --------------------------------------------------------

        fetchHistoricalDataInParallel(
                symbols
        );


        // --------------------------------------------------------
        // Print results
        // --------------------------------------------------------

        printAllCachedResults(
                symbols
        );
    }


    // ============================================================
    // FETCH HISTORICAL DATA IN PARALLEL
    // ============================================================

    private void fetchHistoricalDataInParallel(
            List<String> symbols
    ) {

        if (symbols == null
                || symbols.isEmpty()) {

            return;
        }


        /*
         * One CompletableFuture per batch.
         */
        List<CompletableFuture<Void>> futures =
                new ArrayList<>();


        /*
         * Maximum 20 symbols per Yahoo request.
         */
        for (
                int start = 0;
                start < symbols.size();
                start += YAHOO_BATCH_SIZE
        ) {

            int end =
                    Math.min(
                            start + YAHOO_BATCH_SIZE,
                            symbols.size()
                    );


            List<String> batch =
                    new ArrayList<>(
                            symbols.subList(
                                    start,
                                    end
                            )
                    );


            System.out.println(
                    "Creating Yahoo batch: "
                            + (start / YAHOO_BATCH_SIZE + 1)
                            + " | Symbols: "
                            + batch.size()
            );


            CompletableFuture<Void> future =
                    CompletableFuture.runAsync(
                            () -> fetchYahooBatch(
                                    batch
                            ),
                            executor
                    );


            futures.add(future);
        }


        /*
         * Wait until ALL batches finish.
         */
        CompletableFuture.allOf(
                futures.toArray(
                        new CompletableFuture[0]
                )
        ).join();
    }


    // ============================================================
    // FETCH ONE YAHOO BATCH
    // ============================================================

    private void fetchYahooBatch(
            List<String> symbols
    ) {

        if (symbols == null
                || symbols.isEmpty()) {

            return;
        }


        try {

            /*
             * Build:
             *
             * MRF.NS,BLS.NS,TCS.NS
             */
            StringBuilder symbolBuilder =
                    new StringBuilder();


            for (String symbol :
                    symbols) {

                if (symbol == null
                        || symbol.trim().isEmpty()) {

                    continue;
                }


                if (symbolBuilder.length() > 0) {

                    symbolBuilder.append(",");
                }


                symbolBuilder
                        .append(
                                normalizeSymbol(symbol)
                        )
                        .append(".NS");
            }


            if (symbolBuilder.length() == 0) {

                return;
            }


            String apiUrl =
                    String.format(
                            YAHOO_SPARK_URL,
                            symbolBuilder.toString()
                    );


            System.out.println();
            System.out.println(
                    "Yahoo request:"
            );

            System.out.println(
                    apiUrl
            );


            /*
             * ONE HTTP request for maximum 20 symbols.
             */
            String json =
                    fetchYahooData(
                            apiUrl
                    );


            /*
             * Parse all symbols.
             */
            Map<String, HistoricalPriceResponse> results =
                    parseYahooResponse(
                            json
                    );


            /*
             * Store in cache.
             */
            if (results != null
                    && !results.isEmpty()) {

                cache.putAll(results);
            }


            System.out.println(
                    "Yahoo batch completed. Results: "
                            + results.size()
            );


        } catch (Exception e) {

            System.out.println(
                    "Yahoo batch failed: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // PARSE YAHOO RESPONSE
    // ============================================================

    private Map<String, HistoricalPriceResponse>
    parseYahooResponse(
            String json
    ) {

        Map<String, HistoricalPriceResponse> results =
                new LinkedHashMap<>();


        if (json == null
                || json.trim().isEmpty()) {

            return results;
        }


        try {

            JsonNode root =
                    objectMapper.readTree(json);


            JsonNode spark =
                    root.path("spark");


            JsonNode resultArray =
                    spark.path("result");


            if (!resultArray.isArray()) {

                return results;
            }


            /*
             * Yahoo gives one result per symbol.
             */
            for (JsonNode result :
                    resultArray) {

                try {

                    HistoricalPriceResponse response =
                            parseSingleSymbol(
                                    result
                            );


                    if (response == null) {

                        continue;
                    }


                    String symbol =
                            response.getSymbol();


                    if (symbol == null
                            || symbol.trim().isEmpty()) {

                        continue;
                    }


                    results.put(
                            symbol,
                            response
                    );


                } catch (Exception e) {

                    System.out.println(
                            "Failed to parse one symbol: "
                                    + e.getMessage()
                    );
                }
            }


        } catch (Exception e) {

            System.out.println(
                    "Yahoo JSON parsing failed: "
                            + e.getMessage()
            );
        }


        return results;
    }


    // ============================================================
    // PARSE ONE SYMBOL
    // ============================================================

    private HistoricalPriceResponse parseSingleSymbol(
            JsonNode result
    ) {

        if (result == null
                || result.isMissingNode()
                || result.isNull()) {

            return null;
        }


        // --------------------------------------------------------
        // Yahoo symbol
        // --------------------------------------------------------

        String yahooSymbol =
                result.path("symbol").asText(null);


        if (yahooSymbol == null
                || yahooSymbol.trim().isEmpty()) {

            return null;
        }


        /*
         * Keep Yahoo format in final response.
         *
         * Example:
         *
         * MRF.NS
         * TCS.NS
         * POLYMED.NS
         */
        String symbol =
                yahooSymbol
                        .trim()
                        .toUpperCase();


        // --------------------------------------------------------
        // Yahoo response array
        // --------------------------------------------------------

        JsonNode responseArray =
                result.path("response");


        if (!responseArray.isArray()
                || responseArray.size() == 0) {

            return null;
        }


        JsonNode response =
                responseArray.get(0);


        // --------------------------------------------------------
        // timestamps[]
        // --------------------------------------------------------

        JsonNode timestamps =
                response.path("timestamp");


        // --------------------------------------------------------
        // indicators
        // --------------------------------------------------------

        JsonNode indicators =
                response.path("indicators");


        // --------------------------------------------------------
        // close[]
        // --------------------------------------------------------

        JsonNode closePrices =
                indicators
                        .path("quote")
                        .path(0)
                        .path("close");


        // --------------------------------------------------------
        // adjusted close[]
        // --------------------------------------------------------

        JsonNode adjustedClose =
                indicators
                        .path("adjclose")
                        .path(0)
                        .path("adjclose");


        if (!timestamps.isArray()
                || !closePrices.isArray()) {

            return null;
        }


        boolean hasAdjustedClose =
                adjustedClose.isArray()
                        && adjustedClose.size() > 0;


        // --------------------------------------------------------
        // Find latest valid price
        // --------------------------------------------------------

        int latestIndex =
                findLatestValidIndex(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose
                );


        if (latestIndex == -1) {

            return null;
        }


        // --------------------------------------------------------
        // Latest date
        // --------------------------------------------------------

        long latestTimestamp =
                timestamps
                        .get(latestIndex)
                        .asLong();


        LocalDate latestDate =
                Instant
                        .ofEpochSecond(
                                latestTimestamp
                        )
                        .atZone(
                                INDIA_ZONE
                        )
                        .toLocalDate();


        // --------------------------------------------------------
        // Target dates
        // --------------------------------------------------------

        LocalDate date5Y =
                latestDate.minusYears(5);

        LocalDate date3Y =
                latestDate.minusYears(3);

        LocalDate date1Y =
                latestDate.minusYears(1);

        LocalDate date6M =
                latestDate.minusMonths(6);

        LocalDate date3M =
                latestDate.minusMonths(3);


        // --------------------------------------------------------
        // Find historical prices
        // --------------------------------------------------------

        HistoricalPrices prices =
                findAllHistoricalPrices(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date5Y,
                        date3Y,
                        date1Y,
                        date6M,
                        date3M
                );


        // --------------------------------------------------------
        // Create response
        // --------------------------------------------------------

        return new HistoricalPriceResponse(
                symbol,
                latestDate,
                prices.price5Y,
                prices.price3Y,
                prices.price1Y,
                prices.price6M,
                prices.price3M
        );
    }


    // ============================================================
    // FIND LATEST VALID INDEX
    // ============================================================

    private int findLatestValidIndex(
            JsonNode timestamps,
            JsonNode closePrices,
            JsonNode adjustedClose,
            boolean hasAdjustedClose
    ) {

        for (
                int i = timestamps.size() - 1;
                i >= 0;
                i--
        ) {

            JsonNode priceNode =
                    getPriceNode(
                            closePrices,
                            adjustedClose,
                            hasAdjustedClose,
                            i
                    );


            if (isValidPrice(priceNode)) {

                return i;
            }
        }


        return -1;
    }


    // ============================================================
    // FIND ALL HISTORICAL PRICES
    // ============================================================

    /**
     * Finds the latest available price ON or BEFORE
     * each target date.
     *
     * Example:
     *
     * Target:
     * 2021-08-27
     *
     * If Yahoo has:
     *
     * 2021-08-26 -> 120
     * 2021-08-27 -> no trading
     * 2021-08-30 -> 125
     *
     * Then:
     *
     * 5Y price = 120
     *
     * This matches the logic from your Postman script:
     *
     * getPriceBefore(date)
     */
    private HistoricalPrices findAllHistoricalPrices(
            JsonNode timestamps,
            JsonNode closePrices,
            JsonNode adjustedClose,
            boolean hasAdjustedClose,
            LocalDate date5Y,
            LocalDate date3Y,
            LocalDate date1Y,
            LocalDate date6M,
            LocalDate date3M
    ) {

        Double price5Y = null;

        Double price3Y = null;

        Double price1Y = null;

        Double price6M = null;

        Double price3M = null;


        /*
         * Process oldest -> newest.
         *
         * Every time we find a valid price ON or BEFORE
         * a target date, we update that target.
         *
         * Therefore the final stored price is the latest
         * available trading price before that target date.
         */
        for (
                int i = 0;
                i < timestamps.size();
                i++
        ) {

            long timestamp =
                    timestamps
                            .get(i)
                            .asLong();


            LocalDate currentDate =
                    Instant
                            .ofEpochSecond(
                                    timestamp
                            )
                            .atZone(
                                    INDIA_ZONE
                            )
                            .toLocalDate();


            /*
             * Once currentDate is greater than the largest
             * target date (3M), all required target dates
             * have already been found.
             */
            if (currentDate.isAfter(date3M)) {

                break;
            }


            JsonNode priceNode =
                    getPriceNode(
                            closePrices,
                            adjustedClose,
                            hasAdjustedClose,
                            i
                    );


            if (!isValidPrice(priceNode)) {

                continue;
            }


            double price =
                    priceNode.asDouble();


            // ----------------------------------------------------
            // 5 YEARS
            // ----------------------------------------------------

            if (!currentDate.isAfter(date5Y)) {

                price5Y = price;
            }


            // ----------------------------------------------------
            // 3 YEARS
            // ----------------------------------------------------

            if (!currentDate.isAfter(date3Y)) {

                price3Y = price;
            }


            // ----------------------------------------------------
            // 1 YEAR
            // ----------------------------------------------------

            if (!currentDate.isAfter(date1Y)) {

                price1Y = price;
            }


            // ----------------------------------------------------
            // 6 MONTHS
            // ----------------------------------------------------

            if (!currentDate.isAfter(date6M)) {

                price6M = price;
            }


            // ----------------------------------------------------
            // 3 MONTHS
            // ----------------------------------------------------

            if (!currentDate.isAfter(date3M)) {

                price3M = price;
            }
        }


        return new HistoricalPrices(
                price5Y,
                price3Y,
                price1Y,
                price6M,
                price3M
        );
    }


    // ============================================================
    // GET PRICE NODE
    // ============================================================

    /**
     * Prefer adjusted close when Yahoo provides it.
     *
     * Otherwise use normal close.
     */
    private JsonNode getPriceNode(
            JsonNode closePrices,
            JsonNode adjustedClose,
            boolean hasAdjustedClose,
            int index
    ) {

        /*
         * Adjusted close first.
         */
        if (hasAdjustedClose
                && index < adjustedClose.size()) {

            JsonNode adjusted =
                    adjustedClose.get(index);


            if (isValidPrice(adjusted)) {

                return adjusted;
            }
        }


        /*
         * Normal close fallback.
         */
        if (index < closePrices.size()) {

            return closePrices.get(index);
        }


        return null;
    }


    // ============================================================
    // VALID PRICE
    // ============================================================

    private boolean isValidPrice(
            JsonNode node
    ) {

        if (node == null
                || node.isNull()
                || !node.isNumber()) {

            return false;
        }


        double value =
                node.asDouble();


        return value > 0
                && !Double.isNaN(value)
                && !Double.isInfinite(value);
    }


    // ============================================================
    // FETCH YAHOO DATA
    // ============================================================

    private String fetchYahooData(
            String urlString
    ) throws Exception {

        HttpURLConnection connection = null;


        try {

            URL url =
                    new URL(urlString);


            connection =
                    (HttpURLConnection)
                            url.openConnection();


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
                    "Mozilla/5.0"
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            int responseCode =
                    connection.getResponseCode();


            if (responseCode
                    != HttpURLConnection.HTTP_OK) {

                throw new RuntimeException(
                        "Yahoo Finance returned HTTP "
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

            if (connection != null) {

                connection.disconnect();
            }
        }
    }


    // ============================================================
    // FETCH ONE SYMBOL DIRECTLY
    // ============================================================

    private HistoricalPriceResponse
    fetchHistoricalPriceForSymbol(
            String symbol
    ) {

        try {

            String yahooSymbol =
                    normalizeSymbol(symbol)
                            + ".NS";


            String apiUrl =
                    String.format(
                            YAHOO_SPARK_URL,
                            yahooSymbol
                    );


            String json =
                    fetchYahooData(
                            apiUrl
                    );


            Map<String, HistoricalPriceResponse>
                    results =
                    parseYahooResponse(
                            json
                    );


            return results.get(
                    yahooSymbol
            );


        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to fetch historical prices for "
                            + symbol,
                    e
            );
        }
    }


    // ============================================================
    // PRINT ALL RESULTS
    // ============================================================

    private void printAllCachedResults(
            List<String> symbols
    ) {

        System.out.println();

        System.out.println(
                "=================================================="
        );

        System.out.println(
                "HISTORICAL PRICE RESULTS"
        );

        System.out.println(
                "=================================================="
        );


        for (String symbol :
                symbols) {

            String yahooSymbol =
                    normalizeSymbol(symbol)
                            + ".NS";


            HistoricalPriceResponse response =
                    cache.get(
                            yahooSymbol
                    );


            System.out.println();

            System.out.println(
                    "--------------------------------------------"
            );

            System.out.println(
                    "Symbol: "
                            + yahooSymbol
            );


            if (response == null) {

                System.out.println(
                        "Historical data: NOT AVAILABLE"
                );

                continue;
            }


            System.out.println(
                    "Latest Trading Date: "
                            + response.getLatestDate()
            );


            System.out.println(
                    "5Y Price : "
                            + formatPrice(
                            response.getPrice5Y()
                    )
            );


            System.out.println(
                    "3Y Price : "
                            + formatPrice(
                            response.getPrice3Y()
                    )
            );


            System.out.println(
                    "1Y Price : "
                            + formatPrice(
                            response.getPrice1Y()
                    )
            );


            System.out.println(
                    "6M Price : "
                            + formatPrice(
                            response.getPrice6M()
                    )
            );


            System.out.println(
                    "3M Price : "
                            + formatPrice(
                            response.getPrice3M()
                    )
            );
        }


        System.out.println();

        System.out.println(
                "=================================================="
        );

        System.out.println(
                "HISTORICAL PRICE DATA LOAD COMPLETE"
        );

        System.out.println(
                "=================================================="
        );
    }


    // ============================================================
    // PUBLIC PRINT METHOD
    // ============================================================

    public void printHistoricalReturns(
            String symbol
    ) {

        HistoricalPriceResponse response =
                getHistoricalPrices(
                        symbol
                );


        if (response == null) {

            System.out.println(
                    "No historical data found for "
                            + symbol
            );

            return;
        }


        System.out.println(
                "Symbol: "
                        + response.getSymbol()
        );


        System.out.println(
                "Latest Date: "
                        + response.getLatestDate()
        );


        System.out.println(
                "5Y Price: "
                        + formatPrice(
                        response.getPrice5Y()
                )
        );


        System.out.println(
                "3Y Price: "
                        + formatPrice(
                        response.getPrice3Y()
                )
        );


        System.out.println(
                "1Y Price: "
                        + formatPrice(
                        response.getPrice1Y()
                )
        );


        System.out.println(
                "6M Price: "
                        + formatPrice(
                        response.getPrice6M()
                )
        );


        System.out.println(
                "3M Price: "
                        + formatPrice(
                        response.getPrice3M()
                )
        );
    }


    // ============================================================
    // CACHE CONTROL
    // ============================================================

    /**
     * Clears historical price cache.
     */
    public void clearCache() {

        cache.clear();


        System.out.println(
                "Historical price cache cleared."
        );
    }


    /**
     * Returns number of cached symbols.
     */
    public int getCachedSymbolCount() {

        return cache.size();
    }


    // ============================================================
    // NORMALIZE SYMBOL
    // ============================================================

    /**
     * Converts:
     *
     * TCS
     * TCS.NS
     * tcs
     * tcs.ns
     *
     * into:
     *
     * TCS
     */
    private String normalizeSymbol(
            String symbol
    ) {

        if (symbol == null) {

            return "";
        }


        String value =
                symbol
                        .trim()
                        .toUpperCase();


        if (value.endsWith(".NS")) {

            value =
                    value.substring(
                            0,
                            value.length() - 3
                    );
        }


        return value;
    }


    // ============================================================
    // FORMAT PRICE
    // ============================================================

    private String formatPrice(
            Double price
    ) {

        if (price == null) {

            return "N/A";
        }


        return String.format(
                "%.2f",
                price
        );
    }


    // ============================================================
    // INTERNAL HISTORICAL PRICES
    // ============================================================

    private static class HistoricalPrices {

        private final Double price5Y;

        private final Double price3Y;

        private final Double price1Y;

        private final Double price6M;

        private final Double price3M;


        private HistoricalPrices(
                Double price5Y,
                Double price3Y,
                Double price1Y,
                Double price6M,
                Double price3M
        ) {

            this.price5Y = price5Y;

            this.price3Y = price3Y;

            this.price1Y = price1Y;

            this.price6M = price6M;

            this.price3M = price3M;
        }
    }


    // ============================================================
    // RESPONSE OBJECT
    // ============================================================

    public static class HistoricalPriceResponse {

        /*
         * We want:
         *
         * "symbol": "POLYMED.NS"
         */
        private final String symbol;


        /*
         * Kept internally for debugging/logging.
         *
         * We will NOT expose this through JSON if you use
         * the Jackson annotations shown below.
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final LocalDate latestDate;


        /*
         * IMPORTANT:
         *
         * @JsonProperty allows the JSON field names to be:
         *
         * "5Y"
         * "3Y"
         * "1Y"
         * "6M"
         * "3M"
         */
        @com.fasterxml.jackson.annotation.JsonProperty("5Y")
        private final Double price5Y;


        @com.fasterxml.jackson.annotation.JsonProperty("3Y")
        private final Double price3Y;


        @com.fasterxml.jackson.annotation.JsonProperty("1Y")
        private final Double price1Y;


        @com.fasterxml.jackson.annotation.JsonProperty("6M")
        private final Double price6M;


        @com.fasterxml.jackson.annotation.JsonProperty("3M")
        private final Double price3M;


        public HistoricalPriceResponse(
                String symbol,
                LocalDate latestDate,
                Double price5Y,
                Double price3Y,
                Double price1Y,
                Double price6M,
                Double price3M
        ) {

            this.symbol = symbol;

            this.latestDate = latestDate;

            this.price5Y = price5Y;

            this.price3Y = price3Y;

            this.price1Y = price1Y;

            this.price6M = price6M;

            this.price3M = price3M;
        }


        public String getSymbol() {

            return symbol;
        }


        public LocalDate getLatestDate() {

            return latestDate;
        }


        public Double getPrice5Y() {

            return price5Y;
        }


        public Double getPrice3Y() {

            return price3Y;
        }


        public Double getPrice1Y() {

            return price1Y;
        }


        public Double getPrice6M() {

            return price6M;
        }


        public Double getPrice3M() {

            return price3M;
        }
    }
}

//DONE //
