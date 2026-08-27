package com.example.upcomingdividendapi;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MockData {

    // ============================================================
    // DATE FORMATTERS
    // ============================================================

    /*
     * Supported input date:
     *
     * 26-09-2026
     */
    private static final DateTimeFormatter NUMERIC_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /*
     * Supported / frontend date:
     *
     * 26-Sep-2026
     */
    private static final DateTimeFormatter FRONTEND_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd-MMM-yyyy",
                    Locale.ENGLISH
            );


    // ============================================================
    // MOCK DATA
    // ============================================================

    /*
     * ============================================================
     * HOW TO USE MOCK DATA
     * ============================================================
     *
     * If ANY field contains data:
     *
     *     MockData.hasMockData() = true
     *
     * and the record will be sent to DividendService.
     *
     * If ALL fields are null / empty / blank:
     *
     *     MockData.hasMockData() = false
     *
     * and DividendService will completely ignore MockData.
     *
     * ============================================================
     *
     * Example with data:
     *
     * new MockDividend(
     *         "This is me",
     *         "TIM",
     *         "28-Aug-2026",
     *         55,
     *         450,
     *         423
     * )
     *
     * ============================================================
     *
     * Example with only one field:
     *
     * new MockDividend(
     *         "This is me",
     *         null,
     *         null,
     *         null,
     *         null,
     *         null
     * )
     *
     * This STILL means mock data exists.
     *
     * ============================================================
     *
     * Example with no data:
     *
     * new MockDividend(
     *         null,
     *         null,
     *         null,
     *         null,
     *         null,
     *         null
     * )
     *
     * This means mock data does NOT exist.
     *
     * ============================================================
     */

    public static List<MockDividend> getMockDividends() {

        return Arrays.asList(

                new MockDividend(
                        "",       // shareName
                        "",              // symbol
                        "",      // exDate
                        null,                 // dividendAmount
                        null,                // currentSharePrice
                        null                 // previousSharePrice
                )

        );
    }


    // ============================================================
    // MOCK DATA FLAG
    // ============================================================

    /*
     * ============================================================
     * IMPORTANT
     * ============================================================
     *
     * This method is the FLAG.
     *
     * true:
     *     At least one mock record contains data.
     *
     * false:
     *     All mock records are empty.
     *
     * DividendService only uses MockData when this returns true.
     *
     * ============================================================
     */

    public static boolean hasMockData() {

        List<MockDividend> mockDividends =
                getMockDividends();

        /*
         * No list / empty list
         * means no mock data.
         */
        if (mockDividends == null
                || mockDividends.isEmpty()) {

            return false;
        }


        /*
         * Check every mock record.
         */
        for (MockDividend mock :
                mockDividends) {

            /*
             * Ignore null records.
             */
            if (mock == null) {
                continue;
            }


            /*
             * If ANY field has data,
             * mock data exists.
             */
            if (hasAnyData(mock)) {

                return true;
            }
        }


        /*
         * No record contained any data.
         */
        return false;
    }


    // ============================================================
    // CHECK ONE MOCK RECORD
    // ============================================================

    /*
     * Returns:
     *
     * true  = at least one field has data
     * false = every field is empty/null
     */
    private static boolean hasAnyData(
            MockDividend mock
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
    // FORMAT EX-DATE FOR FRONTEND
    // ============================================================

    /*
     * Supported:
     *
     * 26-09-2026
     *
     * 26-Sep-2026
     *
     * Frontend output:
     *
     * 26-Sep-2026
     */
    private static String formatExDate(
            String exDate
    ) {

        if (isEmpty(exDate)) {

            return null;
        }


        String trimmedDate =
                exDate.trim();


        // --------------------------------------------------------
        // CASE 1
        // dd-MM-yyyy
        // --------------------------------------------------------

        try {

            LocalDate date =
                    LocalDate.parse(
                            trimmedDate,
                            NUMERIC_DATE_FORMAT
                    );

            return date.format(
                    FRONTEND_DATE_FORMAT
            );

        } catch (Exception ignored) {
        }


        // --------------------------------------------------------
        // CASE 2
        // dd-MMM-yyyy
        // --------------------------------------------------------

        try {

            LocalDate date =
                    LocalDate.parse(
                            trimmedDate,
                            FRONTEND_DATE_FORMAT
                    );

            return date.format(
                    FRONTEND_DATE_FORMAT
            );

        } catch (Exception ignored) {
        }


        // --------------------------------------------------------
        // Unknown date format
        //
        // Keep original value rather than deleting it.
        // --------------------------------------------------------

        return trimmedDate;
    }


    // ============================================================
    // STRING EMPTY CHECK
    // ============================================================

    private static boolean isEmpty(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }


    // ============================================================
    // MOCK DIVIDEND
    // ============================================================

    public static class MockDividend {

        private final String shareName;

        private final String symbol;

        private final String exDate;

        private final Double dividendAmount;

        private final Double currentSharePrice;

        private final Double previousSharePrice;


        // ========================================================
        // CONSTRUCTOR
        // ========================================================

        public MockDividend(
                String shareName,
                String symbol,
                String exDate,
                Number dividendAmount,
                Number currentSharePrice,
                Number previousSharePrice
        ) {

            this.shareName =
                    shareName;

            this.symbol =
                    symbol;


            /*
             * Convert the date into the format expected
             * by the frontend.
             */
            this.exDate =
                    formatExDate(exDate);


            /*
             * Convert Number to Double.
             *
             * null remains null.
             */
            this.dividendAmount =
                    dividendAmount != null
                            ? dividendAmount.doubleValue()
                            : null;


            this.currentSharePrice =
                    currentSharePrice != null
                            ? currentSharePrice.doubleValue()
                            : null;


            this.previousSharePrice =
                    previousSharePrice != null
                            ? previousSharePrice.doubleValue()
                            : null;
        }


        // ========================================================
        // GETTERS
        // ========================================================

        public String getShareName() {

            return shareName;
        }


        public String getSymbol() {

            return symbol;
        }


        public String getExDate() {

            return exDate;
        }


        public Double getDividendAmount() {

            return dividendAmount;
        }


        public Double getCurrentSharePrice() {

            return currentSharePrice;
        }


        public Double getPreviousSharePrice() {

            return previousSharePrice;
        }
    }
}
