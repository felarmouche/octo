package com.felarmouche.octo.core.exclusion;

import com.felarmouche.octo.core.domain.Listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rejects Listings whose title or description matches one of its rules: the user's own words plus
 * the rules of the selected Exclusion Presets. One matcher applies to every rule: a negated mention
 * ("kein Riss", "Defekt: nein") or damage to an accessory ("Panzerglas gesprungen") does not match.
 */
public final class ExclusionFilter {

    // "kein Riss", "nicht gebrochen", "ohne Displayschaden" (within the same sentence)
    private static final Pattern NEG_BEFORE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:kein\\w*|nicht|ohne|nie)(?![\\p{L}])[^.!?\\n]{0,25}$");
    // "Display ist nicht gebrochen", but not "geht nicht an"
    private static final Pattern NEG_INSIDE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:kein\\w*|nicht)\\W+(?!an(?![\\p{L}]))\\p{L}");
    // "Defekt: nein", "Risse: keine"
    private static final Pattern NEG_AFTER = Pattern.compile(
            "(?iu)^\\s*[:?\\-]?\\s*(?:keine?|nein|nicht)(?![\\p{L}])");
    // the damage concerns an accessory, not the item
    private static final Pattern ACCESSORY = Pattern.compile(
            "(?iu)folie|schutzglas|panzerglas|displayschutz|hülle|case|cover(?!glas)");

    private final List<ExclusionRule> rules;

    private ExclusionFilter(List<ExclusionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * The filter from the settings: the rules of the selected presets in the order they are offered,
     * then the user's own words. So when several rules match, the reason is always the same.
     *
     * @throws IllegalArgumentException when a selected preset is not available
     */
    public static ExclusionFilter of(ExclusionFilterSettings settings, List<ExclusionPreset> available) {
        Set<String> offered = available.stream().map(ExclusionPreset::id).collect(Collectors.toSet());
        for (String id : settings.presets()) {
            if (!offered.contains(id)) throw new IllegalArgumentException("Exclusion Preset not available: " + id);
        }
        List<ExclusionRule> rules = new ArrayList<>();
        available.stream().filter(p -> settings.presets().contains(p.id())).forEach(p -> rules.addAll(p.rules()));
        settings.words().stream().map(ExclusionRule::word).forEach(rules::add);
        return new ExclusionFilter(rules);
    }

    /** The reason {@code <rule> ("<matched text>")} for the first match, title before description. */
    public Optional<String> reject(Listing listing) {
        return find(listing.title(), true).or(() -> find(listing.description(), false));
    }

    private Optional<String> find(String text, boolean isTitle) {
        for (ExclusionRule rule : rules) {
            if (rule.titleOnly() && !isTitle) continue;
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                if (!isNegatedOrAccessory(text, m)) return Optional.of(rule.reason() + " (\"" + m.group().trim() + "\")");
            }
        }
        return Optional.empty();
    }

    private static boolean isNegatedOrAccessory(String text, Matcher m) {
        String before = text.substring(Math.max(0, m.start() - 30), m.start());
        String after = text.substring(m.end(), Math.min(text.length(), m.end() + 12));
        String around = text.substring(Math.max(0, m.start() - 20), Math.min(text.length(), m.end() + 20));
        return NEG_INSIDE.matcher(m.group()).find()
                || NEG_BEFORE.matcher(before).find()
                || NEG_AFTER.matcher(after).find()
                || ACCESSORY.matcher(around).find();
    }
}
