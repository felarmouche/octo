package com.felarmouche.octo.core.notifier;

import com.felarmouche.octo.core.shared.listing.Listing;

import java.util.List;

public interface NotifierI {

    public void notify(List<Listing> listings);
}
