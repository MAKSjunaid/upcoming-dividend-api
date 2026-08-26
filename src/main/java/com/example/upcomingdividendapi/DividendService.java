package com.example.upcomingdividendapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendService {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    /*
     * Yahoo supports multiple symbols in one Spark request.
     *
     * Keeping the batch size at 20 avoids making one request
     * per share.
     */
    private static final int YAHOO_BATCH_SIZE = 20;

    /*
     * Number of parallel API requests.
     *
     * 8 is a good balance between speed and avoiding
     * unnecessary API pressure.
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
                    LocalDate.now().plusDays(1);

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
                    fromDateValue.plusMonths(1);

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
        // 5. FORMAT API DATES
        // ========================================================

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


        // ========================================================
        // 6. CREATE API URLS
        // ========================================================

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


        // ========================================================
        // 7. THREAD POOL
        // ========================================================

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );


        try {

            // ====================================================
            // 8. FETCH NSE + GROWW IN PARALLEL
            // ====================================================

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


            // ====================================================
            // 9. WAIT FOR BOTH
            // ====================================================

            CompletableFuture.allOf(
                    nseFuture,
                    growwFuture
            ).join();


            String nseResponse =
                    nseFuture.join();

            String growwResponse =
                    growwFuture.join();


            // ====================================================
            // 10. PARSE NSE
            // ====================================================

            List<DividendData> nseDividendShares =
                    nseResponse == null
                            ? new ArrayList<>()
                            : extractNseDividendShares(
                            nseResponse,
                            fromDateValue,
                            toDateValue
                    );


            // ====================================================
            // 11. PARSE GROWW
            // ====================================================

            List<DividendData> growwDividendShares =
                    growwResponse == null
                            ? new ArrayList<>()
                            : extractGrowwDividendShares(
                            growwResponse,
                            fromDateValue,
                            toDateValue
                    );


            System.out.println(
                    "NSE records: "
                            + nseDividendShares.size()
            );

            System.out.println(
                    "Groww records: "
                            + growwDividendShares.size()
            );


            // ====================================================
            // 12. MERGE
            // ====================================================

            List<DividendData> dividendShares =
                    mergeAndRemoveDuplicates(
                            nseDividendShares,
                            growwDividendShares
                    );


            System.out.println(
                    "Unique shares: "
                            + dividendShares.size()
            );


            // ====================================================
            // 13. NO DATA
            // ====================================================

            if (dividendShares.isEmpty()) {

                return new ArrayList<>();
            }


            // ====================================================
            // 14. FETCH YAHOO PRICES
            // ====================================================

            fetchYahooSparkPricesInParallel(
                    dividendShares,
                    executor
            );


            // ====================================================
            // 15. REMOVE SHARES WITHOUT CURRENT PRICE
            // ====================================================
            //
            // IMPORTANT:
            //
            // If Yahoo current price is:
            //
            // null
            // empty
            // blank
            // N/A
            //
            // the share is removed here.
            //
            // Therefore it NEVER reaches the frontend.
            // ====================================================

            dividendShares.removeIf(
                    share ->
                            share == null
                                    || isEmpty(
                                    share.currentSharePrice
                            )
                                    || "N/A".equalsIgnoreCase(
                                    share.currentSharePrice
                            )
            );


            // ========================================================
            // 16. NO VALID PRICES
            // ========================================================

            if (dividendShares.isEmpty()) {

                return new ArrayList<>();
            }


            // ========================================================
            // 17. CREATE RESPONSE
            // ========================================================

            List<DividendResponse> response =
                    new ArrayList<>(
                            dividendShares.size()
                    );


            for (DividendData share :
                    dividendShares) {

                if (share == null) {
                    continue;
                }


                // ------------------------------------------------
                // Convert values
                // ------------------------------------------------

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


                // ------------------------------------------------
                // Current price MUST exist
                // ------------------------------------------------

                if (currentPrice == null) {
                    continue;
                }


                // ------------------------------------------------
                // Add complete response
                // ------------------------------------------------

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


            return response;


        } finally {

            executor.shutdown();
        }
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
    // YAHOO REQUEST
    // ============================================================

    private String fetchYahooResponse(
            String apiUrl
    ) throws Exception {

        return fetchResponse(
                apiUrl,
                null
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


        connection.setRequestMethod("GET");

        connection.setConnectTimeout(
                CONNECT_TIMEOUT
        );

        connection.setReadTimeout(
                READ_TIMEOUT
        );

        connection.setUseCaches(false);

        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
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
                                            connection.getInputStream()
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


                    dividendShares.add(data);


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


                    dividendShares.add(data);


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
    // MERGE + DEDUPLICATE
    // ============================================================

    private List<DividendData>
    mergeAndRemoveDuplicates(
            List<DividendData> nseDividendShares,
            List<DividendData> growwDividendShares
    ) {

        /*
         * LinkedHashMap preserves insertion order.
         *
         * NSE is inserted first, so NSE gets priority.
         */
        Map<String, DividendData> uniqueShares =
                new LinkedHashMap<>();


        if (nseDividendShares != null) {

            for (DividendData share :
                    nseDividendShares) {

                if (!isValidDividendData(share)) {
                    continue;
                }


                String symbol =
                        normalizeSymbol(
                                share.symbol
                        );


                uniqueShares.putIfAbsent(
                        symbol,
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


                String symbol =
                        normalizeSymbol(
                                share.symbol
                        );


                uniqueShares.putIfAbsent(
                        symbol,
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

    private void fetchYahooSparkPricesInParallel(
            List<DividendData> dividendShares,
            ExecutorService executor
    ) {

        if (dividendShares == null
                || dividendShares.isEmpty()) {

            return;
        }


        List<CompletableFuture<Void>> futures =
                new ArrayList<>();


        /*
         * Split into batches of 20.
         */
        for (
                int start = 0;
                start < dividendShares.size();
                start += YAHOO_BATCH_SIZE
        ) {

            int end =
                    Math.min(
                            start + YAHOO_BATCH_SIZE,
                            dividendShares.size()
                    );


            List<DividendData> batch =
                    new ArrayList<>(
                            dividendShares.subList(
                                    start,
                                    end
                            )
                    );


            CompletableFuture<Void> future =
                    CompletableFuture.runAsync(
                            () -> fetchYahooBatch(batch),
                            executor
                    );


            futures.add(future);
        }


        /*
         * Wait for all Yahoo batches.
         */
        CompletableFuture.allOf(
                futures.toArray(
                        new CompletableFuture[0]
                )
        ).join();
    }


    // ============================================================
    // YAHOO BATCH
    // ============================================================

    private void fetchYahooBatch(
            List<DividendData> batch
    ) {

        if (batch == null
                || batch.isEmpty()) {

            return;
        }


        try {

            StringBuilder symbolsBuilder =
                    new StringBuilder();


            for (DividendData share :
                    batch) {

                if (share == null
                        || isEmpty(share.symbol)) {

                    continue;
                }


                if (symbolsBuilder.length() > 0) {

                    symbolsBuilder.append(",");
                }


                symbolsBuilder
                        .append(
                                normalizeSymbol(
                                        share.symbol
                                )
                        )
                        .append(".NS");
            }


            if (symbolsBuilder.length() == 0) {

                return;
            }


            String yahooApiUrl =
                    "https://query1.finance.yahoo.com/"
                            + "v7/finance/spark"
                            + "?symbols="
                            + symbolsBuilder
                            + "&range=1d"
                            + "&interval=1d";


            String response =
                    fetchYahooResponse(
                            yahooApiUrl
                    );


            Map<String, YahooPriceData> priceMap =
                    extractYahooSparkPrices(
                            response
                    );


            /*
             * Map prices back to dividend records.
             */
            for (DividendData share :
                    batch) {

                if (share == null) {
                    continue;
                }


                String yahooSymbol =
                        normalizeSymbol(
                                share.symbol
                        ) + ".NS";


                YahooPriceData yahooData =
                        priceMap.get(
                                yahooSymbol
                        );


                if (yahooData == null
                        || isEmpty(
                        yahooData.currentSharePrice
                )
                        || "N/A".equalsIgnoreCase(
                        yahooData.currentSharePrice
                )) {

                    /*
                     * Mark as N/A.
                     *
                     * Main method will remove it.
                     */
                    share.currentSharePrice =
                            "N/A";

                    share.chartPreviousClose =
                            "N/A";

                    continue;
                }


                share.currentSharePrice =
                        yahooData.currentSharePrice;


                share.chartPreviousClose =
                        yahooData.chartPreviousClose;
            }


        } catch (Exception e) {

            System.out.println(
                    "Yahoo batch failed: "
                            + e.getMessage()
            );


            /*
             * If the batch fails, all shares in
             * this batch are removed later.
             */
            for (DividendData share :
                    batch) {

                if (share == null) {
                    continue;
                }


                share.currentSharePrice =
                        "N/A";


                share.chartPreviousClose =
                        "N/A";
            }
        }
    }


    // ============================================================
    // PARSE YAHOO RESPONSE
    // ============================================================

    private Map<String, YahooPriceData>
    extractYahooSparkPrices(
            String response
    ) {

        Map<String, YahooPriceData> priceMap =
                new LinkedHashMap<>();


        if (isEmpty(response)) {

            return priceMap;
        }


        try {

            JsonObject root =
                    JsonParser.parseString(
                            response
                    ).getAsJsonObject();


            JsonObject spark =
                    root.getAsJsonObject(
                            "spark"
                    );


            if (spark == null) {

                return priceMap;
            }


            JsonArray results =
                    spark.getAsJsonArray(
                            "result"
                    );


            if (results == null) {

                return priceMap;
            }


            for (JsonElement element :
                    results) {

                try {

                    if (element == null
                            || !element.isJsonObject()) {

                        continue;
                    }


                    JsonObject result =
                            element.getAsJsonObject();


                    String symbol =
                            getStringValue(
                                    result,
                                    "symbol"
                            );


                    if (isEmpty(symbol)) {

                        continue;
                    }


                    JsonArray responseArray =
                            result.getAsJsonArray(
                                    "response"
                            );


                    if (responseArray == null
                            || responseArray.isEmpty()) {

                        continue;
                    }


                    JsonObject firstResponse =
                            responseArray
                                    .get(0)
                                    .getAsJsonObject();


                    JsonObject meta =
                            firstResponse.getAsJsonObject(
                                    "meta"
                            );


                    if (meta == null) {

                        continue;
                    }


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


                    priceMap.put(
                            symbol
                                    .trim()
                                    .toUpperCase(),
                            price
                    );


                } catch (Exception e) {

                    System.out.println(
                            "Yahoo symbol parsing failed: "
                                    + e.getMessage()
                    );
                }
            }


        } catch (Exception e) {

            System.out.println(
                    "Yahoo parsing failed: "
                            + e.getMessage()
            );
        }


        return priceMap;
    }


    // ============================================================
    // FORMAT YAHOO NUMBER
    // ============================================================

    private String getFormattedNumber(
            JsonObject jsonObject,
            String key
    ) {

        if (jsonObject == null
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


            return formatPrice(value);


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
    // GROWW DATE
    // ============================================================

    private LocalDate parseGrowwDate(
            String date
    ) {

        if (isEmpty(date)) {

            return null;
        }


        String value =
                date.trim();


        /*
         * ISO date:
         *
         * 2026-09-15
         *
         * or:
         *
         * 2026-09-15T00:00:00
         */
        if (value.length() >= 10
                && value.charAt(4) == '-'
                && value.charAt(7) == '-') {

            try {

                return LocalDate.parse(
                        value.substring(0, 10)
                );

            } catch (Exception ignored) {
            }
        }


        /*
         * dd-MMM-yyyy
         */
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
    // GET STRING
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
                            .replace(",", "")
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
    }
}
