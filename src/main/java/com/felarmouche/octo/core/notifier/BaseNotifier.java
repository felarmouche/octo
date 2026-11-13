package com.felarmouche.octo.core.notifier;

import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.Plugin;
import com.felarmouche.octo.core.shared.Verdict;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
public abstract class BaseNotifier extends Plugin implements NotifierI {
    public BaseNotifier(String id) {
        super(id);
    }

    @Override
    public abstract void notify(List<Listing> listings, Set<Verdict.Decision> notifyLevels);

}
