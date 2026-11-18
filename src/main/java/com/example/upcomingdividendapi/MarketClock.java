package com.example.upcomingdividendapi;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;

/**
 * ============================================================
 * NSE MARKET CLOCK (IST)
 * ============================================================
 *
 * Answers two questions for the price refresh logic:
 *
 * 1. Is the market open right now?
 *      Trading day (Mon-Fri, not a detected holiday)
 *      and time between 09:15 and 15:35 IST.
 *
 * 2. When was the last "settled close"?
 *      15:35 IST on the most recent trading day that has
 *      already reached 15:35.
 *
 *      A price fetched AFTER the last settled close is the
 *      final closing price and never needs to be fetched
 *      again until the market opens next.
 *
 * Holidays:
 *      Weekday holidays are detected automatically from
 *      Yahoo's last-trade time (see DividendService) and
 *      passed in here, so no holiday calendar is needed.
 */
public final class MarketClock {

    public static final ZoneId IST =
            ZoneId.of("Asia/Kolkata");

    /*
     * Normal session starts 09:15.
     * 09:00 - 09:15 is pre-open (indicative prices only).
     */
    public static final LocalTime MARKET_OPEN =
            LocalTime.of(9, 15);

    /*
     * Session ends 15:30. We treat prices as "settled"
     * from 15:35 so the closing price has been published.
     */
    public static final LocalTime SETTLED_CLOSE =
            LocalTime.of(15, 35);

    private MarketClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(IST);
    }

    public static boolean isTradingDay(
            LocalDate date,
            Set<LocalDate> holidays
    ) {
        if (date == null) {
            return false;
        }

        DayOfWeek day =
                date.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {
            return false;
        }

        return holidays == null
                || !holidays.contains(date);
    }

    public static boolean isMarketOpen(
            LocalDateTime now,
            Set<LocalDate> holidays
    ) {
        if (!isTradingDay(now.toLocalDate(), holidays)) {
            return false;
        }

        LocalTime time =
                now.toLocalTime();

        return !time.isBefore(MARKET_OPEN)
                && time.isBefore(SETTLED_CLOSE);
    }

    /**
     * 15:35 IST of the most recent trading day that has
     * already passed 15:35.
     */
    public static LocalDateTime lastSettledClose(
            LocalDateTime now,
            Set<LocalDate> holidays
    ) {
        LocalDate date =
                now.toLocalDate();

        if (isTradingDay(date, holidays)
                && !now.toLocalTime().isBefore(SETTLED_CLOSE)) {
            return date.atTime(SETTLED_CLOSE);
        }

        for (int i = 1; i <= 15; i++) {

            LocalDate previous =
                    date.minusDays(i);

            if (isTradingDay(previous, holidays)) {
                return previous.atTime(SETTLED_CLOSE);
            }
        }

        return date.minusDays(1).atTime(SETTLED_CLOSE);
    }

    public static Set<LocalDate> noHolidays() {
        return Collections.emptySet();
    }
}
