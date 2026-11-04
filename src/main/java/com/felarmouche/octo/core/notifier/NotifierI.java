package com.felarmouche.octo.core.notifier;

import com.felarmouche.octo.core.shared.Listing;

import java.util.List;
import java.util.Set;

public interface NotifierI {

    public void notify(List<Listing> listings);
}
