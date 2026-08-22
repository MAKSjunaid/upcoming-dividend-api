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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DividendService {

    private static final DateTimeFormatter API_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final DateTimeFormatter NSE_RESPONSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private static final int MAX_THREADS = 20;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 15000;
    private static final int MAX_RETRIES = 2;
    private static final double SORTING_INVESTMENT_AMOUNT = 200000;

    private static final Pattern DIVIDEND_PATTERN =
            Pattern.compile(
                    "(?i)\\bR(?:s|e)\\.?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*Per\\s*(?:(\\d+(?:\\.\\d+)?)\\s*)?Share(?:s)?"
            );

    public List<DividendResponse> getUpcomingDividends(
            DividendRequest request) throws Exception {

        String requestedFromDate =
                request != null ? request.getFromDate() : null;

        String requestedToDate =
                request != null ? request.getToDate() : null;

        LocalDate fromDateValue;

        if (isEmpty(requestedFromDate)) {
            fromDateValue = LocalDate.now().plusDays(1);
        } else {
            fromDateValue = parseDate(
                    requestedFromDate,
                    "from_date"
            );
        }

        LocalDate toDateValue;

        if (isEmpty(requestedToDate)) {
            toDateValue = fromDateValue.plusMonths(1);
        } else {
            toDateValue = parseDate(
                    requestedToDate,
                    "to_date"
            );
        }

        if (toDateValue.isBefore(fromDateValue)) {
            throw new IllegalArgumentException(
                    "to_date cannot be earlier than from_date"
            );
        }

        String fromDate =
                fromDateValue.format(API_DATE_FORMAT);

        String toDate =
                toDateValue.format(API_DATE_FORMAT);

        String nseApiUrl =
                "https://www.nseindia.com/api/corporates-corporateActions"
                        + "?index=equities"
                        + "&from_date=" + fromDate
                        + "&to_date=" + toDate
                        + "&category=dividend";

        String nseResponse =
                fetchWithRetry(nseApiUrl, true);

        List<DividendData> dividendShares =
                extractDividendShares(
                        nseResponse,
                        fromDateValue,
                        toDateValue
                );

        if (dividendShares.isEmpty()) {
            return new ArrayList<>();
        }

        int threadCount =
                Math.min(
                        MAX_THREADS,
                        Math.max(1, dividendShares.size())
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<?>> futures =
                    new ArrayList<>(dividendShares.size());

            for (DividendData share : dividendShares) {
                futures.add(
                        executor.submit(
                                () -> fetchSharePrice(share)
                        )
                );
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                }
            }

        } finally {
            executor.shutdown();

            try {
                if (!executor.awaitTermination(
                        30,
                        TimeUnit.SECONDS)) {

                    executor.shutdownNow();
                }

            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        dividendShares.sort(
                Comparator.comparingDouble(
                        (DividendData share) ->
                                share.expectedDividendForSorting
                ).reversed()
        );

        List<DividendResponse> response =
                new ArrayList<>(dividendShares.size());

        for (DividendData share : dividendShares) {
            response.add(
                    new DividendResponse(
                            fromDate,
                            toDate,
                            share.shareName,
                            share.symbol,
                            share.exDate,
                            share.dividendAmount,
                            share.currentSharePrice
                    )
            );
        }

        return response;
    }

    private LocalDate parseDate(
            String date,
            String fieldName) {

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

    private void fetchSharePrice(
            DividendData share) {

        try {
            if (share == null || isEmpty(share.symbol)) {
                return;
            }

            String yahooApiUrl =
                    "https://query1.finance.yahoo.com/v8/finance/chart/"
                            + share.symbol.trim()
                            + ".NS?interval=1m&range=1d";

            String yahooResponse =
                    fetchWithRetry(yahooApiUrl, false);

            Double price =
                    extractCurrentSharePrice(yahooResponse);

            share.currentSharePrice = price;

            share.expectedDividendForSorting =
                    calculateExpectedDividend(
                            price,
                            share.dividendAmount,
                            SORTING_INVESTMENT_AMOUNT
                    );

        } catch (Exception e) {
            share.currentSharePrice = null;
            share.expectedDividendForSorting = 0;
        }
    }

    private String fetchWithRetry(
            String apiUrl,
            boolean nseRequest) throws Exception {

        Exception lastException = null;

        for (int attempt = 0;
             attempt <= MAX_RETRIES;
             attempt++) {

            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(apiUrl).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setUseCaches(false);

                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json, text/plain, */*"
                );

                if (nseRequest) {
                    connection.setRequestProperty(
                            "Accept-Language",
                            "en-US,en;q=0.9"
                    );

                    connection.setRequestProperty(
                            "Referer",
                            "https://www.nseindia.com/"
                    );
                }

                int responseCode =
                        connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return readResponse(connection);
                }

                lastException =
                        new Exception(
                                "API failed. Response Code: "
                                        + responseCode
                        );

            } catch (Exception e) {
                lastException = e;

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(
                            500L * (attempt + 1)
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception(
                            "API request interrupted",
                            e
                    );
                }
            }
        }

        throw new Exception(
                "API request failed after "
                        + (MAX_RETRIES + 1)
                        + " attempts",
                lastException
        );
    }

    private String readResponse(
            HttpURLConnection connection) throws Exception {

        StringBuilder response =
                new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     connection.getInputStream()
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    private List<DividendData> extractDividendShares(
            String response,
            LocalDate fromDate,
            LocalDate toDate) {

        List<DividendData> dividendShares =
                new ArrayList<>();

        if (isEmpty(response)) {
            return dividendShares;
        }

        try {
            JsonElement root =
                    JsonParser.parseString(response);

            if (!root.isJsonArray()) {
                return dividendShares;
            }

            JsonArray jsonArray =
                    root.getAsJsonArray();

            for (JsonElement element : jsonArray) {

                if (element == null
                        || !element.isJsonObject()) {
                    continue;
                }

                try {
                    JsonObject record =
                            element.getAsJsonObject();

                    String subject =
                            getStringValue(record, "subject");

                    String symbol =
                            getStringValue(record, "symbol");

                    String shareName =
                            getStringValue(record, "comp");

                    String exDate =
                            getStringValue(record, "exDate");

                    if (isEmpty(subject)
                            || !subject.toLowerCase()
                            .contains("dividend")
                            || isEmpty(symbol)
                            || isEmpty(exDate)) {
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

                    DividendData dividendData =
                            new DividendData();

                    dividendData.shareName = shareName;
                    dividendData.symbol = symbol.trim();
                    dividendData.exDate = exDate.trim();
                    dividendData.dividendAmount =
                            extractDividendAmount(subject);

                    dividendShares.add(dividendData);

                } catch (Exception ignored) {
                }
            }

        } catch (Exception ignored) {
        }

        return dividendShares;
    }

    private Double extractDividendAmount(String subject) {

        if (isEmpty(subject)) {
            return null;
        }

        try {
            String normalizedSubject =
                    subject.replace('\u00A0', ' ')
                            .replaceAll("\\s+", " ")
                            .trim();

            Matcher matcher =
                    DIVIDEND_PATTERN.matcher(
                            normalizedSubject
                    );

            if (matcher.find()) {
                double dividendAmount =
                        Double.parseDouble(matcher.group(1));

                String shareCount =
                        matcher.group(2);

                if (!isEmpty(shareCount)) {
                    double numberOfShares =
                            Double.parseDouble(shareCount);

                    if (numberOfShares > 0) {
                        return dividendAmount / numberOfShares;
                    }
                }

                return dividendAmount;
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private Double extractCurrentSharePrice(
            String response) {

        if (isEmpty(response)) {
            return null;
        }

        try {
            JsonElement rootElement =
                    JsonParser.parseString(response);

            if (!rootElement.isJsonObject()) {
                return null;
            }

            JsonObject root =
                    rootElement.getAsJsonObject();

            if (!root.has("chart")
                    || root.get("chart").isJsonNull()
                    || !root.get("chart").isJsonObject()) {
                return null;
            }

            JsonObject chart =
                    root.getAsJsonObject("chart");

            if (!chart.has("result")
                    || chart.get("result").isJsonNull()
                    || !chart.get("result").isJsonArray()) {
                return null;
            }

            JsonArray result =
                    chart.getAsJsonArray("result");

            if (result.size() == 0
                    || result.get(0).isJsonNull()
                    || !result.get(0).isJsonObject()) {
                return null;
            }

            JsonObject firstResult =
                    result.get(0).getAsJsonObject();

            if (!firstResult.has("meta")
                    || firstResult.get("meta").isJsonNull()
                    || !firstResult.get("meta").isJsonObject()) {
                return null;
            }

            JsonObject meta =
                    firstResult.getAsJsonObject("meta");

            if (!meta.has("regularMarketPrice")
                    || meta.get("regularMarketPrice")
                    .isJsonNull()) {
                return null;
            }

            double price =
                    meta.get("regularMarketPrice")
                            .getAsDouble();

            if (price <= 0
                    || Double.isNaN(price)
                    || Double.isInfinite(price)) {
                return null;
            }

            return price;

        } catch (Exception e) {
            return null;
        }
    }

    private double calculateExpectedDividend(
            Double sharePrice,
            Double dividendAmount,
            double investmentAmount) {

        if (sharePrice == null
                || dividendAmount == null
                || sharePrice <= 0
                || dividendAmount < 0
                || investmentAmount <= 0) {
            return 0;
        }

        double sharesCanBuy =
                Math.floor(
                        investmentAmount / sharePrice
                );

        return sharesCanBuy * dividendAmount;
    }

    private String getStringValue(
            JsonObject jsonObject,
            String key) {

        if (jsonObject == null
                || key == null
                || !jsonObject.has(key)
                || jsonObject.get(key).isJsonNull()) {
            return null;
        }

        try {
            String value =
                    jsonObject.get(key).getAsString();

            return isEmpty(value)
                    ? null
                    : value.trim();

        } catch (Exception e) {
            return null;
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class DividendData {

        private String shareName;
        private String symbol;
        private String exDate;
        private Double dividendAmount;
        private Double currentSharePrice;
        private double expectedDividendForSorting;
    }
}