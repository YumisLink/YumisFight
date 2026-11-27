package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.TowerCard.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** One player's selections and discarded cards for a single climb. */
public final class TowerCardDraft {
    public static final int CARDS_PER_OFFER = 3;
    public static final int REFRESHES_PER_CARD = 2;

    public record Slot(TowerCard card, int refreshesLeft) {}
    public record SavedSlot(TowerCard.Reference card, int refreshesLeft) {}
    public record Snapshot(int offer, int revision, List<TowerCard.Reference> chosen, Set<String> removed,
                           List<SavedSlot> slots, boolean careerOffer) {
        public Snapshot(int offer, int revision, List<TowerCard.Reference> chosen, Set<String> removed,
                        List<SavedSlot> slots) {
            this(offer, revision, chosen, removed, slots, false);
        }

        public int selectedThrough() {
            return slots.isEmpty() ? offer : offer - 1;
        }
    }

    private final RandomGenerator random;
    private final List<TowerCard> chosen = new ArrayList<>();
    private final Set<String> removed = new HashSet<>();
    private List<TowerCard> eligible = List.of();
    private List<Slot> slots = List.of();
    private int offer = -1;
    private int revision;
    private boolean careerOffer;

    public TowerCardDraft(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public int offer() { return offer; }
    public int revision() { return revision; }
    public boolean careerOffer() { return careerOffer; }

    public Snapshot snapshot() {
        return new Snapshot(offer, revision, chosen.stream().map(TowerCard::reference).toList(), Set.copyOf(removed),
            slots.stream().map(slot -> new SavedSlot(slot.card().reference(), slot.refreshesLeft())).toList(), careerOffer);
    }

    public void restore(Snapshot saved, List<TowerCard> pool) {
        offer = saved.offer();
        revision = saved.revision();
        restoreChosen(saved.chosen());
        removed.clear();
        removed.addAll(saved.removed());
        for (TowerCard card : chosen) removed.addAll(TowerBattleCard.exclusionsFor(card.id()));
        slots = saved.slots().stream().map(slot -> new Slot(TowerCard.restore(slot.card()), slot.refreshesLeft())).toList();
        careerOffer = !slots.isEmpty() && saved.careerOffer();
        eligible = slots.isEmpty() ? List.of() : List.copyOf(pool);
    }

    public void restoreChosen(List<TowerCard.Reference> cards) {
        chosen.clear();
        cards.stream().map(TowerCard::restore).forEach(chosen::add);
    }

    public boolean open(int checkpoint, List<TowerCard> pool) {
        if (checkpoint != offer + 1 || !slots.isEmpty()) return false;
        var snapshot = List.copyOf(pool);
        var candidates = available(snapshot, removed);

        List<Slot> next;
        if (checkpoint == 0) {
            var careerPool = availableCareerMythics(snapshot, removed);
            if (!careerPool.isEmpty()) {
                next = dealCareerOffer(careerPool, candidates);
                careerOffer = true;
            } else {
                next = dealNormalOffer(candidates);
                careerOffer = false;
            }
        } else {
            next = dealNormalOffer(candidates);
            careerOffer = false;
        }
        offer = checkpoint;
        revision++;
        eligible = snapshot;
        slots = List.copyOf(next);
        return true;
    }

    private List<Slot> dealNormalOffer(Map<Rarity, List<TowerCard>> candidates) {
        var next = new ArrayList<Slot>(CARDS_PER_OFFER);
        for (int index = 0; index < CARDS_PER_OFFER; index++) {
            TowerCard card = draw(candidates);
            candidates.get(card.rarity()).remove(card);
            next.add(new Slot(card.bind(random), REFRESHES_PER_CARD));
        }
        return next;
    }

    private List<Slot> dealCareerOffer(List<TowerCard> careerPool, Map<Rarity, List<TowerCard>> fallbackCandidates) {
        var remaining = new ArrayList<>(careerPool);
        var next = new ArrayList<Slot>(CARDS_PER_OFFER);
        for (int index = 0; index < CARDS_PER_OFFER; index++) {
            TowerCard card;
            if (!remaining.isEmpty()) {
                card = remaining.remove(random.nextInt(remaining.size()));
                fallbackCandidates.get(card.rarity()).remove(card);
            } else {
                card = draw(fallbackCandidates);
                fallbackCandidates.get(card.rarity()).remove(card);
            }
            next.add(new Slot(card.bind(random), REFRESHES_PER_CARD));
        }
        return next;
    }

    public List<Slot> slots() {
        return slots;
    }

    public List<TowerCard> chosen() {
        return List.copyOf(chosen);
    }

    public String boundSkill(TowerBattleCard effect) {
        for (var card : chosen) if (card.id().equals(effect.id())) return card.variant();
        return "";
    }

    public boolean canRefresh(int index) {
        if (index < 0 || index >= slots.size()) return false;
        Slot current = slots.get(index);
        if (current.refreshesLeft() == 0) return false;
        if (careerOffer) {
            Set<String> excluded = new HashSet<>(removed);
            for (Slot slot : slots) excluded.add(slot.card().id());
            return !availableCareerMythics(eligible, excluded).isEmpty();
        }
        if (current.card().rarity() == Rarity.MYTHIC
            && slots.stream().anyMatch(slot -> slot.card().id().equals(TowerBattleCard.MYTHIC_GIFT.id()))
            && mythics(current.card().id()).isEmpty()) return false;
        return true;
    }

    public boolean refresh(int checkpoint, int index, int version) {
        if (!matches(checkpoint, index, version)) return false;
        Slot current = slots.get(index);
        if (current.refreshesLeft() == 0) return false;

        Set<String> excluded = new HashSet<>(removed);
        for (Slot slot : slots) excluded.add(slot.card().id());

        if (careerOffer) {
            var careerPool = availableCareerMythics(eligible, excluded);
            if (careerPool.isEmpty()) return false;
            TowerCard picked = careerPool.get(random.nextInt(careerPool.size())).bind(random);
            var next = new ArrayList<>(slots);
            next.set(index, new Slot(picked, current.refreshesLeft() - 1));
            if (!current.card().milk()) removed.add(current.card().id());
            revision++;
            slots = List.copyOf(next);
            return true;
        }

        if (current.card().rarity() == Rarity.MYTHIC
            && slots.stream().anyMatch(slot -> slot.card().id().equals(TowerBattleCard.MYTHIC_GIFT.id()))
            && mythics(current.card().id()).isEmpty()) return false;

        var candidates = available(eligible, excluded);

        var next = new ArrayList<>(slots);
        next.set(index, new Slot(draw(candidates).bind(random), current.refreshesLeft() - 1));
        if (!current.card().milk()) removed.add(current.card().id());
        revision++;
        slots = List.copyOf(next);
        return true;
    }

    public Optional<TowerCard> choose(int checkpoint, int index, int version) {
        if (!matches(checkpoint, index, version)) return Optional.empty();
        TowerCard card = slots.get(index).card();
        if (card.id().equals(TowerBattleCard.MYTHIC_GIFT.id())) {
            var pool = mythics("");
            if (pool.isEmpty()) return Optional.empty();
            var awarded = pool.get(random.nextInt(pool.size())).bind(random);
            chosen.add(awarded);
            removed.add(awarded.id());
            removed.addAll(TowerBattleCard.exclusionsFor(awarded.id()));
        }
        chosen.add(card);
        if (!card.milk()) {
            removed.add(card.id());
            removed.addAll(TowerBattleCard.exclusionsFor(card.id()));
        }
        revision++;
        if (careerOffer) {
            careerOffer = false;
            slots = List.copyOf(dealNormalOffer(available(eligible, removed)));
            return Optional.of(card);
        }
        slots = List.of();
        eligible = List.of();
        return Optional.of(card);
    }

    /** Each chosen passive is applied once to the supplied, unmodified snapshot. */
    public void applyPassives(Stats snapshot, Progress progress, TowerBattleState battle, long now) {
        for (TowerCard card : chosen) card.passive().accept(snapshot);
        battle.apply(snapshot, now);
        TowerCardEffects.applyDerivedStats(snapshot, progress);
    }

    private boolean matches(int checkpoint, int index, int version) {
        return checkpoint == offer && version == revision && index >= 0 && index < slots.size();
    }

    private List<TowerCard> mythics(String excluded) {
        return eligible.stream().filter(card -> card.rarity() == Rarity.MYTHIC
            && !removed.contains(card.id()) && !excluded.equals(card.id())).toList();
    }

    private static List<TowerCard> availableCareerMythics(List<TowerCard> pool, Set<String> excluded) {
        var result = new ArrayList<TowerCard>();
        for (TowerCard card : pool) {
            if (card.careerMythic() && !excluded.contains(card.id())) result.add(card);
        }
        return result;
    }

    private static Map<Rarity, List<TowerCard>> available(List<TowerCard> pool, Set<String> excluded) {
        var result = new EnumMap<Rarity, List<TowerCard>>(Rarity.class);
        Set<String> ids = new HashSet<>();
        for (Rarity rarity : Rarity.values()) result.put(rarity, new ArrayList<>());
        for (TowerCard card : pool) {
            if (!ids.add(card.id())) throw new IllegalArgumentException("重复的塔卡 ID " + card.id());
            if (!excluded.contains(card.id())) result.get(card.rarity()).add(card);
        }
        if (result.get(Rarity.MYTHIC).isEmpty())
            result.get(Rarity.EPIC).removeIf(card -> card.id().equals(TowerBattleCard.MYTHIC_GIFT.id()));
        return result;
    }

    private TowerCard draw(Map<Rarity, List<TowerCard>> candidates) {
        int roll = random.nextInt(100);
        Rarity[] rarities = Rarity.values();
        for (int index = 0; index < rarities.length; index++) {
            roll -= rarities[index].weight();
            if (roll < 0) {
                for (int lower = index; lower >= 0; lower--) {
                    var cards = candidates.get(rarities[lower]);
                    if (!cards.isEmpty()) return cards.get(random.nextInt(cards.size()));
                }
                return TowerCard.MILK;
            }
        }
        throw new IllegalStateException("塔卡稀有度权重应合计为 100");
    }
}
