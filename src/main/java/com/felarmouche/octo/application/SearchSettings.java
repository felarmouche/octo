package com.felarmouche.octo.application;

import lombok.Getter;

@Getter
public class SearchSettings {
    private int minMarginInCents;
    private String rebuyUrl;
    public SearchSettings(int minMarginInCents, String rebuyUrl){
        this.minMarginInCents=minMarginInCents;
        this.rebuyUrl = rebuyUrl;
    }
}
