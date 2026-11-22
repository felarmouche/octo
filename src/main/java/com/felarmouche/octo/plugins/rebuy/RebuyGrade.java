package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.types.phone.PhoneCondition;

import java.util.Locale;
import java.util.Optional;

/** Rebuy's condition scale, from A0 (new) to A5 (broken, no price published). */
public enum RebuyGrade {
    A0, A1, A2, A3, A4, A5;

    /** The best grade a phone in this Phone Condition can plausibly reach; NEW, DEFECT or unknown → A0. */
    static RebuyGrade bestPlausible(Optional<PhoneCondition> condition) {
        return switch (condition.orElse(PhoneCondition.NEW)) {
            case VERY_GOOD -> A1;
            case GOOD -> A2;
            case OK -> A3;
            default -> A0;
        };
    }

    /** "a0" … "a5", as in Rebuy's price fields. */
    String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True when this grade is {@code best} or worse. */
    boolean isNoBetterThan(RebuyGrade best) {
        return ordinal() >= best.ordinal();
    }
}
