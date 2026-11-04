package com.felarmouche.octo.plugins.evaluator;

import lombok.Getter;
import lombok.Setter;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
class RebuyCatalog {
    private Map<String, Integer> catalog = new HashMap<>();
    private String productUrl;
    public RebuyCatalog(String productUrl){
        this.productUrl = productUrl;
        updateCatalog();
    }

    @Scheduled(fixedDelayString = "${rebuy.catalog.update.intervall}")
    void updateCatalog(){

    }

}
