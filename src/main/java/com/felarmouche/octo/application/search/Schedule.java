package com.felarmouche.octo.application.search;

import jakarta.validation.constraints.Min;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * When a Search runs: every {@code intervalMinutes}, only inside the window of hours and on the weekdays.
 * Times are read in the one global timezone, which the caller passes in.
 *
 * @param window   null allows the whole day
 * @param weekdays empty or null allows every day
 */
public record Schedule(@Min(1) int intervalMinutes, Window window, Set<DayOfWeek> weekdays) {

    public Schedule {
        weekdays = weekdays == null ? Set.of() : Set.copyOf(weekdays);
    }

    /** The hours of the day a Search may run: from inclusive, to exclusive, on the same day. */
    public record Window(LocalTime from, LocalTime to) {
        public Window {
            if (from == null || to == null) throw new IllegalArgumentException("window needs from and to");
            if (!from.isBefore(to)) throw new IllegalArgumentException("window must end after it starts");
        }
    }

    /** The end of the previous run plus the interval, or the start of the next allowed window after that. */
    public Instant nextRunAfter(Instant previousEnd, ZoneId zone) {
        return firstRunFrom(previousEnd.plus(Duration.ofMinutes(intervalMinutes)), zone);
    }

    /** The given time if it is allowed, otherwise the start of the next allowed window. */
    public Instant firstRunFrom(Instant time, ZoneId zone) {
        LocalDate day = time.atZone(zone).toLocalDate();
        // a week ahead always reaches an allowed day
        for (int i = 0; i <= 7; i++, day = day.plusDays(1)) {
            if (!weekdays.isEmpty() && !weekdays.contains(day.getDayOfWeek())) continue;
            Instant opens = window == null ? day.atStartOfDay(zone).toInstant() : at(day, window.from(), zone);
            Instant closes = window == null ? day.plusDays(1).atStartOfDay(zone).toInstant() : at(day, window.to(), zone);
            if (!opens.isBefore(closes)) continue; // the clock change swallowed the window that day
            if (time.isBefore(opens)) return opens;
            if (time.isBefore(closes)) return time;
        }
        throw new IllegalStateException("no allowed time within a week: " + this);
    }

    /** A local time skipped by the clock change moves forward by the gap. */
    private static Instant at(LocalDate day, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(day, time, zone).toInstant();
    }
}
