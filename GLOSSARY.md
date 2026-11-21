# Octo

Octo watches second-hand marketplaces for underpriced offers, judges whether each one is worth buying for resale, and alerts the user to the good ones.

## Language

**Search**:
A standing, user-defined hunt for one kind of item that is re-run on its Schedule. It is assembled from plugins and is either active or paused.
_Avoid_: Job, query, watch

**Listing**:
One offer for sale on a marketplace, as Octo captured it at a point in time.
_Avoid_: Ad, Anzeige, offer, item

**Listing Type**:
The category of item a Listing describes (e.g. phone), which determines which item-specific attributes it carries. Each Search has exactly one, and only Plugins that support it can be used in that Search.
_Avoid_: Product type, convertTo

**Phone Listing**:
A Listing of the Listing Type phone, carrying the offer's phone attributes (storage, battery, colour, condition).
_Avoid_: IR, phone DTO

**Fingerprint**:
The identity of a Listing's content on one marketplace (its text and price), used to recognise a Listing already seen even when the marketplace re-posts it. A change of price makes it a new Listing.

**Marketplace Details**:
The raw fields a marketplace shows for a Listing in its own wording (e.g. "Speicher (GB): 128 GB"), from which a Finder derives the Listing Type's attributes.
_Avoid_: Product-specific fields

**Phone Condition**:
The state of a phone in Octo's own scale (new, very good, good, okay, defect), independent of any marketplace's or buyback service's wording.
_Avoid_: Zustand, grade

**Verdict**:
The judgement attached to a Listing: a Decision, a human-readable reason, and any evaluation details (e.g. resale price, margin).
_Avoid_: Rating, result

**Decision**:
The outcome of a Verdict: buy, don't buy, unsure, or not evaluatable (to be retried on a later run).

**Deduplication**:
The fixed step of every Search that drops Listings whose Fingerprint the Search has already seen. What a Search has seen survives edits to that Search.
_Avoid_: Base filter, dedup filter

**Exclusion Filter**:
A step, available to every Search, that rejects Listings whose text matches words the user excluded.
_Avoid_: Text filter, damage filter, blacklist

**Exclusion Preset**:
A ready-made, named set of exclusion rules, offered either for one Listing Type (e.g. typical damage for phones) or for every Listing Type (e.g. wanted or swap ads), which the user can add to a Search's Exclusion Filter. Like the user's own words, a rule does not match when the text negates it (e.g. "kein Riss").
_Avoid_: Default set, default blacklist

**Listing Type Filter**:
A step that rejects Listings by the attributes of their Listing Type (e.g. for phones: min battery, storage sizes, price range). There is one per Listing Type, and all its settings are optional.
_Avoid_: Phone filter rules, pre-check

**Schedule**:
When a Search runs: how often, within which hours of the day, and on which weekdays.
_Avoid_: Interval, cron, delay

**Listing History**:
The Listings a Search keeps with their Verdicts after a run, limited by that Search's retention rule (e.g. the newest 100, or only certain Decisions).
_Avoid_: Results, feed, log

**Reference Product**:
The product page at a buyback service that an Evaluator compares Listings against. The user enters it per Search.
_Avoid_: Rebuy URL, model URL

**Plugin Settings**:
The values the user enters for one Plugin within one Search. Every setting belongs to exactly one Search; there are no shared defaults.
_Avoid_: Search settings, config, properties

### Plugins

**Plugin**:
An interchangeable building block that performs one stage of a Search.
_Avoid_: Module, extension

**Finder**:
A Plugin that collects Listings from one marketplace.
_Avoid_: Scraper, crawler, source

**Evaluator**:
A Plugin that gives Listings a Verdict, typically by comparing the purchase price with a resale price. A Search has at most one.
_Avoid_: Rater, judge

**Notifier**:
A Plugin that alerts the user over one channel about Listings whose Decision it was set to report. A Search can have several.
_Avoid_: Alerter, sender

**Alert**:
One message to the user about one Listing: the Listing with its Verdict and a list of labelled values provided by its Listing Type and the Evaluator. A Notifier renders Alerts without knowing any Listing Type.
_Avoid_: Notification, message
