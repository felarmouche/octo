package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.listingtype.ListingType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phones: storage, battery, colour and Phone Condition. A Finder maps its own structured fields
 * onto {@link PhoneAttributes}; {@link #complete} then reads what is still unknown from the text,
 * so every Finder gets the same text reading.
 */
public class PhoneListingType implements ListingType<PhoneFilterSettings> {

    public static final String ID = "phone";

    private static final String BATTERY_WORD = "(?:akku|batterie|battery)";

    /** "Akkukapazität: 87 %", "Batterie 88%", "Battery Health 91%" */
    private static final Pattern BATTERY_WORD_FIRST = Pattern.compile(
            "(?i)" + BATTERY_WORD + "\\w*\\D{0,40}?\\b(\\d{2,3})\\s*(?:%|prozent)");

    /** "99% Batteriezustand", "70 Prozent Batterie", "85% Kapazität vom Akku" */
    private static final Pattern BATTERY_NUMBER_FIRST = Pattern.compile(
            "(?i)\\b(\\d{2,3})\\s*(?:%|prozent)[^\\d.,;!?\\n]{0,15}?" + BATTERY_WORD);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Class<PhoneFilterSettings> filterSettingsType() {
        return PhoneFilterSettings.class;
    }

    /** Storage from title, then description; battery from description, then title, within the Finder's range hint. */
    @Override
    public Listing complete(Listing listing) {
        PhoneAttributes known = attributes(listing);
        return listing.withAttributes(new PhoneAttributes(
                known.storage().or(() -> Storage.inText(listing.title())).or(() -> Storage.inText(listing.description())),
                readBattery(known.battery(), listing),
                known.colour(),
                known.condition()));
    }

    private static Optional<Battery> readBattery(Optional<Battery> hint, Listing listing) {
        if (hint.isPresent() && hint.get().isExact()) return hint;
        Battery range = hint.orElse(new Battery(0, 100));
        for (String text : List.of(listing.description(), listing.title())) {
            OptionalInt percent = batteryInText(text);
            if (percent.isPresent() && percent.getAsInt() > 0 && range.contains(percent.getAsInt()))
                return Optional.of(Battery.exact(percent.getAsInt()));
        }
        return hint;
    }

    private static OptionalInt batteryInText(String text) {
        for (Pattern pattern : List.of(BATTERY_WORD_FIRST, BATTERY_NUMBER_FIRST)) {
            Matcher m = pattern.matcher(text);
            if (m.find()) return OptionalInt.of(Integer.parseInt(m.group(1)));
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> reject(Listing listing, PhoneFilterSettings filter) {
        Optional<String> price = filter.price().reject(listing);
        if (price.isPresent()) return price;

        PhoneAttributes phone = attributes(listing);
        if (filter.minBattery() != null && phone.battery().isPresent()) {
            Battery battery = phone.battery().get();
            if (battery.isBelow(filter.minBattery())) {
                String known = battery.isExact() ? battery.min() + " %" : "höchstens " + battery.max() + " %";
                return Optional.of("Akku " + known + " < " + filter.minBattery() + " %");
            }
        }
        if (!filter.storages().isEmpty() && phone.storage().isPresent()
                && !filter.storages().contains(phone.storage().get())) {
            return Optional.of("Speicher " + phone.storage().get().label() + " nicht erlaubt");
        }
        if (!filter.conditions().isEmpty() && phone.condition().isPresent()
                && !filter.conditions().contains(phone.condition().get())) {
            return Optional.of("Zustand " + phone.condition().get().label() + " nicht erlaubt");
        }
        return Optional.empty();
    }

    @Override
    public List<LabelledValue> describe(Listing listing) {
        PhoneAttributes phone = attributes(listing);
        List<LabelledValue> values = new ArrayList<>();
        phone.storage().ifPresent(s -> values.add(new LabelledValue("Speicher", s.label())));
        phone.battery().ifPresent(b -> values.add(new LabelledValue("Akku", b.label())));
        phone.colour().ifPresent(c -> values.add(new LabelledValue("Farbe", c)));
        phone.condition().ifPresent(c -> values.add(new LabelledValue("Zustand", c.label())));
        return values;
    }

    private static PhoneAttributes attributes(Listing listing) {
        return listing.attributes()
                .filter(PhoneAttributes.class::isInstance)
                .map(PhoneAttributes.class::cast)
                .orElse(PhoneAttributes.UNKNOWN);
    }
}
