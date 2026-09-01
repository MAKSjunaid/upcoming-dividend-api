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
 *      1M
 *      5D
 *
 * 7. Return PRICES, NOT percentages.
 *
 * 8. Prefer Adjusted Close when available.
 *
 * 9. Fall back to normal Close when Adjusted Close
 *    is unavailable.
 *
 * 10. Cache results in memory.
 *
 * 11. Historical target dates use the nearest available
 *     trading-day price.
 *
 * 12. If no price exists before the target because the
 *     Yahoo response begins slightly after the target,
 *     the first available price after the target is used.
 *
 * Frontend formula:
 *
 * ((Current Price - Historical Price)
 *      / Historical Price) * 100
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
     * We request 5 years because all required periods
     * are inside this range.
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


            String json =
                    fetchYahooData(
                            apiUrl
                    );


            Map<String, HistoricalPriceResponse> results =
                    parseYahooResponse(
                            json
                    );


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
        // normal close[]
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


        /*
         * Keep adjustedClose.
         *
         * We use it when Yahoo actually provides valid
         * adjusted values.
         */
        boolean hasAdjustedClose =
                adjustedClose.isArray()
                        && adjustedClose.size() > 0;


        if (!timestamps.isArray()
                || timestamps.size() == 0) {

            return null;
        }


        if (!closePrices.isArray()
                || closePrices.size() == 0) {

            return null;
        }


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

        LocalDate date1M =
                latestDate.minusMonths(1);

        LocalDate date5D =
                latestDate.minusDays(5);


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
                        date3M,
                        date1M,
                        date5D
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
                prices.price3M,
                prices.price1M,
                prices.price5D
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

        int maxIndex =
                Math.min(
                        timestamps.size(),
                        closePrices.size()
                );


        for (
                int i = maxIndex - 1;
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
     * Finds the best available historical price for each
     * requested period.
     *
     * Normal behavior:
     *
     *     target date
     *          |
     *          v
     *     latest price ON or BEFORE target
     *
     * Fallback behavior:
     *
     * If the Yahoo 5Y response starts slightly AFTER the
     * requested 5Y target, the first available price AFTER
     * the target is used.
     *
     * This prevents valid historical data from becoming N/A
     * merely because Yahoo's returned range begins a few
     * hours/days after the exact target boundary.
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
            LocalDate date3M,
            LocalDate date1M,
            LocalDate date5D
    ) {

        Double price5Y =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date5Y
                );


        Double price3Y =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date3Y
                );


        Double price1Y =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date1Y
                );


        Double price6M =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date6M
                );


        Double price3M =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date3M
                );


        Double price1M =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date1M
                );


        Double price5D =
                findHistoricalPrice(
                        timestamps,
                        closePrices,
                        adjustedClose,
                        hasAdjustedClose,
                        date5D
                );


        return new HistoricalPrices(
                price5Y,
                price3Y,
                price1Y,
                price6M,
                price3M,
                price1M,
                price5D
        );
    }


    // ============================================================
    // FIND ONE HISTORICAL PRICE
    // ============================================================

    /**
     * Finds the best available trading price for a target date.
     *
     * Step 1:
     *
     * Find the latest valid trading price ON or BEFORE
     * the target date.
     *
     * Step 2:
     *
     * If no such price exists, find the earliest valid
     * trading price AFTER the target date.
     *
     * This makes the method robust against:
     *
     * - weekends
     * - holidays
     * - Yahoo range boundaries
     * - missing daily candles
     * - IPOs whose history starts close to a target date
     */
    private Double findHistoricalPrice(
            JsonNode timestamps,
            JsonNode closePrices,
            JsonNode adjustedClose,
            boolean hasAdjustedClose,
            LocalDate targetDate
    ) {

        if (timestamps == null
                || !timestamps.isArray()
                || timestamps.size() == 0
                || targetDate == null) {

            return null;
        }


        /*
         * --------------------------------------------------------
         * FIRST PASS
         *
         * Find latest valid price ON or BEFORE target date.
         * --------------------------------------------------------
         */

        Double previousPrice =
                null;


        LocalDate previousDate =
                null;


        int maxIndex =
                Math.min(
                        timestamps.size(),
                        closePrices.size()
                );


        for (
                int i = 0;
                i < maxIndex;
                i++
        ) {

            JsonNode timestampNode =
                    timestamps.get(i);


            if (timestampNode == null
                    || !timestampNode.isNumber()) {

                continue;
            }


            LocalDate currentDate =
                    Instant
                            .ofEpochSecond(
                                    timestampNode.asLong()
                            )
                            .atZone(
                                    INDIA_ZONE
                            )
                            .toLocalDate();


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


            /*
             * We have gone beyond the target.
             *
             * Stop the first pass.
             */
            if (currentDate.isAfter(targetDate)) {

                break;
            }


            previousPrice =
                    priceNode.asDouble();


            previousDate =
                    currentDate;
        }


        /*
         * If we found a valid previous trading day,
         * use it.
         */
        if (previousPrice != null) {

            return previousPrice;
        }


        /*
         * --------------------------------------------------------
         * SECOND PASS
         *
         * No price existed before the target.
         *
         * This usually happens because the Yahoo 5Y response
         * starts slightly after the exact 5-year target.
         *
         * Use the first valid price AFTER the target.
         * --------------------------------------------------------
         */

        for (
                int i = 0;
                i < maxIndex;
                i++
        ) {

            JsonNode timestampNode =
                    timestamps.get(i);


            if (timestampNode == null
                    || !timestampNode.isNumber()) {

                continue;
            }


            LocalDate currentDate =
                    Instant
                            .ofEpochSecond(
                                    timestampNode.asLong()
                            )
                            .atZone(
                                    INDIA_ZONE
                            )
                            .toLocalDate();


            if (currentDate.isBefore(targetDate)) {

                continue;
            }


            JsonNode priceNode =
                    getPriceNode(
                            closePrices,
                            adjustedClose,
                            hasAdjustedClose,
                            i
                    );


            if (isValidPrice(priceNode)) {

                return priceNode.asDouble();
            }
        }


        /*
         * No usable historical price exists.
         */
        return null;
    }


    // ============================================================
    // GET PRICE NODE
    // ============================================================

    /**
     * Prefer Adjusted Close when Yahoo provides a valid value.
     *
     * Otherwise use normal Close.
     *
     * This preserves the adjustedClose / hasAdjustedClose
     * behavior.
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
        if (closePrices != null
                && index < closePrices.size()) {

            JsonNode close =
                    closePrices.get(index);


            if (isValidPrice(close)) {

                return close;
            }
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


            System.out.println(
                    "1M Price : "
                            + formatPrice(
                            response.getPrice1M()
                    )
            );


            System.out.println(
                    "5D Price : "
                            + formatPrice(
                            response.getPrice5D()
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


        System.out.println(
                "1M Price: "
                        + formatPrice(
                        response.getPrice1M()
                )
        );


        System.out.println(
                "5D Price: "
                        + formatPrice(
                        response.getPrice5D()
                )
        );
    }


    // ============================================================
    // CACHE CONTROL
    // ============================================================

    public void clearCache() {

        cache.clear();


        System.out.println(
                "Historical price cache cleared."
        );
    }


    public int getCachedSymbolCount() {

        return cache.size();
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

        private final Double price1M;

        private final Double price5D;


        private HistoricalPrices(
                Double price5Y,
                Double price3Y,
                Double price1Y,
                Double price6M,
                Double price3M,
                Double price1M,
                Double price5D
        ) {

            this.price5Y = price5Y;

            this.price3Y = price3Y;

            this.price1Y = price1Y;

            this.price6M = price6M;

            this.price3M = price3M;

            this.price1M = price1M;

            this.price5D = price5D;
        }
    }


    // ============================================================
    // RESPONSE OBJECT
    // ============================================================

    public static class HistoricalPriceResponse {

        private final String symbol;


        /*
         * Internal only.
         *
         * Not exposed through JSON.
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final LocalDate latestDate;


        /*
         * HISTORICAL PRICES.
         *
         * These are NOT percentages.
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


        @com.fasterxml.jackson.annotation.JsonProperty("1M")
        private final Double price1M;


        @com.fasterxml.jackson.annotation.JsonProperty("5D")
        private final Double price5D;


        public HistoricalPriceResponse(
                String symbol,
                LocalDate latestDate,
                Double price5Y,
                Double price3Y,
                Double price1Y,
                Double price6M,
                Double price3M,
                Double price1M,
                Double price5D
        ) {

            this.symbol = symbol;

            this.latestDate = latestDate;

            this.price5Y = price5Y;

            this.price3Y = price3Y;

            this.price1Y = price1Y;

            this.price6M = price6M;

            this.price3M = price3M;

            this.price1M = price1M;

            this.price5D = price5D;
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


        public Double getPrice1M() {

            return price1M;
        }


        public Double getPrice5D() {

            return price5D;
        }
    }
}
