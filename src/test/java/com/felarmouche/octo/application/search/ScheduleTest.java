package com.felarmouche.octo.application.search;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The next-run calculation. 2026-11-23 is a Monday; Berlin is UTC+1 in winter and UTC+2 in summer. */
class ScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Set<DayOfWeek> WORKDAYS = Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);

    private final Schedule everyFiveMinutes11To17 = new Schedule(5, window("11:00", "17:00"), Set.of());

    @Test
    void insideTheWindowTheNextRunIsTheEndOfThePreviousPlusTheInterval() {
        assertThat(everyFiveMinutes11To17.nextRunAfter(berlin("2026-11-23T12:00"), BERLIN))
                .isEqualTo(berlin("2026-11-23T12:05"));
    }

    @Test
    void withoutWindowAndWeekdaysEveryTimeIsAllowed() {
        Schedule always = new Schedule(60, null, Set.of());

        assertThat(always.nextRunAfter(berlin("2026-11-23T23:30"), BERLIN)).isEqualTo(berlin("2026-11-24T00:30"));
    }

    @Test
    void crossingTheEndOfTheWindowMovesTheRunToTheNextWindowStart() {
        assertThat(everyFiveMinutes11To17.nextRunAfter(berlin("2026-11-23T16:58"), BERLIN))
                .isEqualTo(berlin("2026-11-24T11:00"));
    }

    @Test
    void theEndOfTheWindowIsExcluded() {
        assertThat(everyFiveMinutes11To17.nextRunAfter(berlin("2026-11-23T16:55"), BERLIN))
                .isEqualTo(berlin("2026-11-24T11:00"));
    }

    @Test
    void beforeTheWindowTheRunWaitsForItsStart() {
        assertThat(everyFiveMinutes11To17.nextRunAfter(berlin("2026-11-23T08:00"), BERLIN))
                .isEqualTo(berlin("2026-11-23T11:00"));
    }

    @Test
    void excludedWeekdaysAreSkipped() {
        Schedule workdays = new Schedule(5, window("11:00", "17:00"), WORKDAYS);

        assertThat(workdays.nextRunAfter(berlin("2026-11-27T16:58"), BERLIN)).isEqualTo(berlin("2026-11-30T11:00"));
    }

    @Test
    void anExcludedWeekdayWithoutWindowStartsAtMidnightOfTheNextAllowedDay() {
        Schedule mondays = new Schedule(5, null, Set.of(MONDAY));

        assertThat(mondays.nextRunAfter(berlin("2026-11-23T23:58"), BERLIN)).isEqualTo(berlin("2026-11-30T00:00"));
    }

    @Test
    void theWindowIsInTheGivenTimezone() {
        // 16:58 in Berlin is 15:58 UTC; the next window opens at 11:00 Berlin = 10:00 UTC
        assertThat(everyFiveMinutes11To17.nextRunAfter(Instant.parse("2026-11-23T15:58:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-11-24T10:00:00Z"));
        assertThat(everyFiveMinutes11To17.nextRunAfter(Instant.parse("2026-11-23T15:58:00Z"), ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2026-11-23T16:03:00Z"));
    }

    @Test
    void theWindowFollowsDaylightSavingTime() {
        // the clocks go forward on Sunday 2026-03-29 and back on Sunday 2026-10-25
        assertThat(everyFiveMinutes11To17.nextRunAfter(Instant.parse("2026-03-28T15:58:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-03-29T09:00:00Z"));
        assertThat(everyFiveMinutes11To17.nextRunAfter(Instant.parse("2026-10-24T14:58:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-10-25T10:00:00Z"));
    }

    @Test
    void theIntervalIsElapsedTimeAcrossTheClockChange() {
        Schedule hourly = new Schedule(60, null, Set.of());

        // 01:30 CET + 60 minutes is 03:30 CEST
        assertThat(hourly.nextRunAfter(Instant.parse("2026-03-29T00:30:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void aWindowStartingInTheSkippedHourOpensWhenTheClockJumps() {
        Schedule night = new Schedule(5, window("02:30", "05:00"), Set.of());

        // 02:30 doesn't exist on 2026-03-29; the window opens at 03:30 CEST = 01:30 UTC
        assertThat(night.nextRunAfter(Instant.parse("2026-03-28T03:58:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void aWindowThatFallsIntoTheSkippedHourIsSkippedThatDay() {
        Schedule skipped = new Schedule(5, window("02:30", "03:15"), Set.of());

        // on 2026-03-29 the clock jumps from 02:00 to 03:00, so 02:30 would open at 03:30, after the close;
        // the next window is on 2026-03-30 at 02:30 CEST = 00:30 UTC
        assertThat(skipped.nextRunAfter(Instant.parse("2026-03-28T02:58:00Z"), BERLIN))
                .isEqualTo(Instant.parse("2026-03-30T00:30:00Z"));
    }

    @Test
    void aFirstRunInsideTheWindowIsImmediate() {
        assertThat(everyFiveMinutes11To17.firstRunFrom(berlin("2026-11-23T12:34"), BERLIN))
                .isEqualTo(berlin("2026-11-23T12:34"));
    }

    @Test
    void aFirstRunOutsideTheWindowWaitsForTheNextWindowStart() {
        Schedule workdays = new Schedule(5, window("11:00", "17:00"), WORKDAYS);

        assertThat(workdays.firstRunFrom(berlin("2026-11-22T12:00"), BERLIN)).isEqualTo(berlin("2026-11-23T11:00"));
        assertThat(workdays.firstRunFrom(berlin("2026-11-23T18:00"), BERLIN)).isEqualTo(berlin("2026-11-24T11:00"));
    }

    @Test
    void aWindowMustEndAfterItStarts() {
        assertThatThrownBy(() -> window("17:00", "11:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window("11:00", "11:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Schedule.Window(null, LocalTime.NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Schedule.Window window(String from, String to) {
        return new Schedule.Window(LocalTime.parse(from), LocalTime.parse(to));
    }

    private static Instant berlin(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant();
    }
}
