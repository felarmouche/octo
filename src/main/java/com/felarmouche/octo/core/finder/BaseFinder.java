package com.felarmouche.octo.core.finder;

import com.felarmouche.octo.core.shared.Listing;
import com.felarmouche.octo.core.shared.Plugin;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
public abstract class BaseFinder extends Plugin implements FinderI{
    private final String searchUrl;
    private final int maxListingsPerSearch;

    public BaseFinder(String id, String searchUrl, int maxListingsPerSearch) {
        super(id);
        this.searchUrl = searchUrl;
        this.maxListingsPerSearch = maxListingsPerSearch;
    }

    @Override
    public abstract List<Listing> find();

}
