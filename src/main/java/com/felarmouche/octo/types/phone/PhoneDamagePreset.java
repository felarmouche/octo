package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.exclusion.ExclusionPreset;
import com.felarmouche.octo.core.exclusion.ExclusionRule;

import java.util.List;

/** The phone Exclusion Preset "damage": typical damage words in phone ads. */
final class PhoneDamagePreset {

    private PhoneDamagePreset() {
    }

    private static final String SEP = "[\\s\\-]*";
    private static final String DISPLAY = "(?:display|bildschirm|screen|glas|front)";
    private static final String BACK = "(?:rückseite|backcover|back" + SEP + "cover|rückglas|backglas)";
    private static final String BROKEN = "(?:gebrochen|gesprungen|zerbrochen|kaputt|riss\\w*|defekt|beschädigt|gerissen)";

    static final ExclusionPreset DAMAGE = new ExclusionPreset("damage", "Schäden", List.of(
            ExclusionRule.of("Für Bastler / Ersatzteile", false, "bastler|ersatzteil\\w*|teilespender|für" + SEP + "teile"),
            ExclusionRule.of("Defekt", false,
                    "defekt\\w*|kaputt\\w*|funktioniert" + SEP + "nicht|geht" + SEP + "nicht" + SEP + "an"),
            ExclusionRule.of("Display beschädigt", false,
                    DISPLAY + "\\w*\\W{1,3}(?:\\w+\\W+){0,3}?" + BROKEN
                            + "|(?:gebrochen|gesprungen|gerissen|zerbrochen)\\w*\\W+(?:\\w+\\W+){0,2}?" + DISPLAY
                            + "|displaybruch|displayschaden|glasbruch"),
            ExclusionRule.of("Rückseite beschädigt", false, BACK + "\\w*\\W{1,3}(?:\\w+\\W+){0,3}?" + BROKEN),
            ExclusionRule.of("iCloud / Aktivierungssperre", false,
                    "icloud" + SEP + "(?:gesperrt|sperre|lock)|aktivierungssperre|activation" + SEP + "lock|gesperrt"),
            ExclusionRule.of("Wasserschaden", false, "wasser" + SEP + "schaden"),
            ExclusionRule.of("Face ID defekt", false,
                    "face" + SEP + "id\\W+(?:\\w+\\W+){0,2}?(?:defekt|kaputt|geht" + SEP + "nicht|funktioniert" + SEP + "nicht)"
                            + "|ohne" + SEP + "face" + SEP + "id"),
            ExclusionRule.of("Attrappe", false, "attrappe|dummy")));
}
