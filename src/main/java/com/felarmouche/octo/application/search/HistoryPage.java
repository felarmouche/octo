package com.felarmouche.octo.application.search;

import java.util.List;

/** One page of a Listing History, newest first, and whether older entries follow. */
public record HistoryPage(List<RecordedListing> entries, boolean hasMore) {
}
