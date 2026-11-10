package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Findet eindeutig schlechte Listings anhand von Titel/Beschreibung. */
public final class DamageTextFilter {

    private DamageTextFilter() {}

    private record Rule(String reason, Pattern pattern, boolean titleOnly) {}

    private static final String SEP = "[\\s\\-]*";

    private static Rule rule(String reason, boolean titleOnly, String regex) {
        return new Rule(reason, Pattern.compile("(?iu)(?<![\\p{L}])(?:" + regex + ")"), titleOnly);
    }

    private static final String DISPLAY = "(?:display|bildschirm|screen|glas|front)";
    private static final String BACK    = "(?:rückseite|backcover|back" + SEP + "cover|rückglas|backglas)";
    private static final String BROKEN  = "(?:gebrochen|gesprungen|zerbrochen|kaputt|riss\\w*|defekt|beschädigt|gerissen)";

    private static final List<Rule> RULES = List.of(
            rule("Gesuch / Tausch", true, "suche|gesucht|kaufe|ankauf|tausche|nur" + SEP + "tausch"),
            rule("Für Bastler / Ersatzteile", false, "bastler|ersatzteil\\w*|teilespender|für" + SEP + "teile"),
            rule("Defekt", false, "defekt\\w*|kaputt\\w*|funktioniert" + SEP + "nicht|geht" + SEP + "nicht" + SEP + "an"),
            rule("Display beschädigt", false,
                    DISPLAY + "\\w*\\W{1,3}(?:\\w+\\W+){0,3}?" + BROKEN
                            + "|(?:gebrochen|gesprungen|gerissen|zerbrochen)\\w*\\W+(?:\\w+\\W+){0,2}?" + DISPLAY
                            + "|displaybruch|displayschaden|glasbruch"),
            rule("Rückseite beschädigt", false, BACK + "\\w*\\W{1,3}(?:\\w+\\W+){0,3}?" + BROKEN),
            rule("iCloud / Aktivierungssperre", false,
                    "icloud" + SEP + "(?:gesperrt|sperre|lock)|aktivierungssperre|activation" + SEP + "lock|gesperrt"),
            rule("Wasserschaden", false, "wasser" + SEP + "schaden"),
            rule("Face ID defekt", false,
                    "face" + SEP + "id\\W+(?:\\w+\\W+){0,2}?(?:defekt|kaputt|geht" + SEP + "nicht|funktioniert" + SEP + "nicht)"
                            + "|ohne" + SEP + "face" + SEP + "id"),
            rule("Attrappe", false, "attrappe|dummy")
    );

    // "kein Riss", "nicht gebrochen", "ohne Displayschaden"
    private static final Pattern NEG_BEFORE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:kein\\w*|nicht|ohne|nie)(?![\\p{L}])[^.!?\\n]{0,25}$");
    // "Display ist nicht gebrochen" (Verneinung im Treffer, aber nicht "geht nicht" / "geht nicht an")
    private static final Pattern NEG_INSIDE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:kein\\w*|nicht)\\W+(?!an(?![\\p{L}]))\\p{L}");
    // "Defekt: nein", "Risse: keine"
    private static final Pattern NEG_AFTER = Pattern.compile(
            "(?iu)^\\s*[:?\\-]?\\s*(?:keine?|nein|nicht)(?![\\p{L}])");
    // Schaden betrifft Zubehör, nicht das Gerät
    private static final Pattern ACCESSORY = Pattern.compile(
            "(?iu)folie|schutzglas|panzerglas|displayschutz|hülle|case|cover(?!glas)");

    /** Liefert den Grund, falls das Listing eindeutig schlecht ist. */
    public static Optional<String> findReason(String title, String description, Collection<String> extraKeywords) {
        List<Rule> rules = new ArrayList<>(RULES);
        if (extraKeywords != null) {
            for (String k : extraKeywords) {
                if (k != null && !k.isBlank()) {
                    rules.add(rule("Ausschlusswort", false,
                            Pattern.quote(k.trim()) + "(?![\\p{L}])"));
                }
            }
        }
        return find(title, true, rules).or(() -> find(description, false, rules));
    }

    private static Optional<String> find(String text, boolean isTitle, List<Rule> rules) {
        if (text == null || text.isBlank()) return Optional.empty();
        for (Rule r : rules) {
            if (r.titleOnly() && !isTitle) continue;
            Matcher m = r.pattern().matcher(text);
            while (m.find()) {
                if (isNegatedOrAccessory(text, m)) continue;
                return Optional.of(r.reason() + " (\"" + m.group().trim() + "\")");
            }
        }
        return Optional.empty();
    }

    private static boolean isNegatedOrAccessory(String text, Matcher m) {
        String before = text.substring(Math.max(0, m.start() - 30), m.start());
        String after  = text.substring(m.end(), Math.min(text.length(), m.end() + 12));
        String around = text.substring(Math.max(0, m.start() - 20), Math.min(text.length(), m.end() + 20));
        return NEG_INSIDE.matcher(m.group()).find()
                || NEG_BEFORE.matcher(before).find()
                || NEG_AFTER.matcher(after).find()
                || ACCESSORY.matcher(around).find();
    }
}