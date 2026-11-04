package com.felarmouche.octo.application;

import com.felarmouche.octo.core.evaluator.EvaluatorException;
import com.felarmouche.octo.core.evaluator.EvaluatorI;
import com.felarmouche.octo.core.filter.FilterException;
import com.felarmouche.octo.core.filter.FilterI;
import com.felarmouche.octo.core.finder.FindException;
import com.felarmouche.octo.core.finder.FinderI;
import com.felarmouche.octo.core.notifier.NotifierException;
import com.felarmouche.octo.core.notifier.NotifierI;
import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Listing;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class Search {
    private final FinderI finder;
    private final FilterI filter;
    private final EvaluatorI evaluator;
    private final NotifierI notifier;
    private HashMap<Fingerprint, Listing> last;

    public Search(FinderI finder, FilterI filter, EvaluatorI evaluator, NotifierI notifier){
        this.finder = finder;
        this.filter = filter;
        this.evaluator = evaluator;
        this.notifier = notifier;
    }

    public void execute(){
        try {
            List<Listing> found = finder.find();
            List<Listing> filtered = filter != null
                    ? filter.filter(found)
                    : found;

            // setVerdict() for each Listing
            evaluator.evaluate(filtered);
            notifier.notify(filtered);
        } catch (FindException e ){
            log.error("Finding listings with finder {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (FilterException e){
            log.error("Filtering listings with filter {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (EvaluatorException e){
            log.error("Evaluation of listings with evaluator {} failed with message: {}", e.getPluginId(), e.getMessage());
        }  catch (NotifierException e ){
            log.error("Sending notification with {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (Exception e) {
            log.error("Search failed with error message: {}", e.getMessage());
        }


    }


}
