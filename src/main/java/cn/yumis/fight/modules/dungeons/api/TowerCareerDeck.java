package cn.yumis.fight.modules.dungeons.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Shared deck snapshot with per-career mythic card slots and stele re-pick state.
 * Non-career cards remain shared across careers; career mythic cards are stored and picked per jobId.
 */
public final class TowerCareerDeck {
    private TowerCareerDeck() {}

    public record CareerOffer(int revision, List<TowerCardDraft.SavedSlot> slots, Set<String> removed) {
        public CareerOffer {
            revision = Math.max(0, revision);
            slots = slots == null ? List.of() : List.copyOf(slots);
            removed = removed == null ? Set.of() : Set.copyOf(removed);
        }

        public static CareerOffer empty() {
            return new CareerOffer(0, List.of(), Set.of());
        }
    }

    public record DeckSnapshot(
        String id,
        List<TowerCard.Reference> chosen,
        int careerSlots,
        Map<String, List<TowerCard.Reference>> careerCards,
        Map<String, CareerOffer> careerOffers
    ) {
        public DeckSnapshot {
            id = id == null ? "" : id;
            chosen = chosen == null ? List.of() : List.copyOf(chosen);
            careerSlots = Math.max(0, careerSlots);
            var cleanCards = new LinkedHashMap<String, List<TowerCard.Reference>>();
            if (careerCards != null) {
                for (var entry : careerCards.entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                        cleanCards.put(entry.getKey(), List.copyOf(entry.getValue()));
                    }
                }
            }
            careerCards = Map.copyOf(cleanCards);
            var cleanOffers = new LinkedHashMap<String, CareerOffer>();
            if (careerOffers != null) {
                for (var entry : careerOffers.entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                        cleanOffers.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            careerOffers = Map.copyOf(cleanOffers);
        }

        public static DeckSnapshot empty(String id) {
            return new DeckSnapshot(id, List.of(), 0, Map.of(), Map.of());
        }

        /** Splits a single climb's chosen list into shared non-career cards and per-job career mythics. */
        public static DeckSnapshot fromClimb(String id, List<TowerCard.Reference> allChosen) {
            var shared = new ArrayList<TowerCard.Reference>();
            var byJob = new LinkedHashMap<String, List<TowerCard.Reference>>();
            int slots = 0;
            if (allChosen != null) {
                for (var ref : allChosen) {
                    TowerCard card = TowerCard.restore(ref);
                    if (card.careerMythic()) {
                        byJob.computeIfAbsent(card.job(), ignored -> new ArrayList<>()).add(ref);
                        slots++;
                    } else {
                        shared.add(ref);
                    }
                }
            }
            return new DeckSnapshot(id, shared, slots, byJob, Map.of());
        }

        public boolean hasCards() {
            if (!chosen.isEmpty() || careerSlots > 0) return true;
            for (var cards : careerCards.values()) {
                if (!cards.isEmpty()) return true;
            }
            return false;
        }

        public int countCard(String cardId) {
            if (cardId == null || cardId.isEmpty()) return 0;
            int total = 0;
            for (var ref : chosen) if (cardId.equals(ref.id())) total++;
            for (var list : careerCards.values()) {
                for (var ref : list) if (cardId.equals(ref.id())) total++;
            }
            return total;
        }

        public boolean holdsCard(String cardId) {
            return countCard(cardId) > 0;
        }

        public TowerCard.Reference findCard(String cardId) {
            if (cardId == null || cardId.isEmpty()) return null;
            for (var ref : chosen) if (cardId.equals(ref.id())) return ref;
            for (var list : careerCards.values()) {
                for (var ref : list) if (cardId.equals(ref.id())) return ref;
            }
            return null;
        }

        public Set<String> allHeldIds() {
            var ids = new HashSet<String>();
            for (var ref : chosen) ids.add(ref.id());
            for (var list : careerCards.values()) {
                for (var ref : list) ids.add(ref.id());
            }
            return Set.copyOf(ids);
        }

        public Map<String, TowerCard.Reference> allHeldMap() {
            var map = new LinkedHashMap<String, TowerCard.Reference>();
            for (var ref : chosen) map.putIfAbsent(ref.id(), ref);
            for (var list : careerCards.values()) {
                for (var ref : list) map.putIfAbsent(ref.id(), ref);
            }
            return Map.copyOf(map);
        }

        public record CreativeMutation(DeckSnapshot deck, List<String> replacedIds) {}

        public CreativeMutation creativeAdd(String fallbackDeckId, TowerCard.Reference reference) {
            TowerCard card = TowerCard.restore(reference);
            if (card.skillBinding() != TowerBattleCard.Binding.NONE
                && (card.variant().isBlank() || card.variantTitle().isBlank())) {
                throw new IllegalArgumentException("该卡牌需要绑定具体技能");
            }
            TowerCard.Reference normalized = card.reference();
            String nextId = id.isEmpty() ? Objects.requireNonNull(fallbackDeckId) : id;
            Set<String> excluded = TowerBattleCard.exclusionsFor(card.id());
            var replaced = new ArrayList<String>();
            if (card.careerMythic()) {
                var nextCareerCards = new LinkedHashMap<>(careerCards);
                var jobCards = new ArrayList<>(nextCareerCards.getOrDefault(card.job(), List.of()));
                int existingIndex = -1;
                for (int i = jobCards.size() - 1; i >= 0; i--) {
                    String itemId = jobCards.get(i).id();
                    if (excluded.contains(itemId)) {
                        replaced.add(itemId);
                        jobCards.remove(i);
                    } else if (itemId.equals(card.id())) {
                        existingIndex = i;
                    }
                }
                if (existingIndex >= 0) jobCards.set(existingIndex, normalized);
                else jobCards.add(normalized);
                nextCareerCards.put(card.job(), List.copyOf(jobCards));
                var nextOffers = new LinkedHashMap<>(careerOffers);
                var previousOffer = nextOffers.get(card.job());
                if (previousOffer != null) {
                    nextOffers.put(card.job(), new CareerOffer(previousOffer.revision() + 1, List.of(), previousOffer.removed()));
                }
                return new CreativeMutation(new DeckSnapshot(nextId, chosen, careerSlots, nextCareerCards, nextOffers), List.copyOf(replaced));
            }
            var nextChosen = new ArrayList<>(chosen);
            if (card.milk()) {
                nextChosen.add(normalized);
            } else {
                int existingIndex = -1;
                for (int i = nextChosen.size() - 1; i >= 0; i--) {
                    String itemId = nextChosen.get(i).id();
                    if (excluded.contains(itemId)) {
                        replaced.add(itemId);
                        nextChosen.remove(i);
                    } else if (itemId.equals(card.id())) {
                        existingIndex = i;
                    }
                }
                if (existingIndex >= 0) nextChosen.set(existingIndex, normalized);
                else nextChosen.add(normalized);
            }
            return new CreativeMutation(new DeckSnapshot(nextId, nextChosen, careerSlots, careerCards, careerOffers), List.copyOf(replaced));
        }

        public DeckSnapshot creativeRemove(String cardId) {
            if (cardId == null || cardId.isEmpty()) return this;
            TowerBattleCard battle = TowerBattleCard.find(cardId);
            if (battle != null && battle.careerMythic()) {
                String job = battle.job();
                var existing = careerCards.get(job);
                if (existing == null || existing.isEmpty()) return this;
                var nextJobCards = new ArrayList<>(existing);
                if (!nextJobCards.removeIf(ref -> cardId.equals(ref.id()))) return this;
                var nextCareerCards = new LinkedHashMap<>(careerCards);
                if (nextJobCards.isEmpty()) nextCareerCards.remove(job);
                else nextCareerCards.put(job, List.copyOf(nextJobCards));
                var nextOffers = new LinkedHashMap<>(careerOffers);
                var previousOffer = nextOffers.get(job);
                if (previousOffer != null) {
                    nextOffers.put(job, new CareerOffer(previousOffer.revision() + 1, List.of(), previousOffer.removed()));
                }
                int maxCareerCount = 0;
                for (var list : nextCareerCards.values()) maxCareerCount = Math.max(maxCareerCount, list.size());
                int nextSlots = Math.min(careerSlots, maxCareerCount);
                var next = new DeckSnapshot(id, chosen, nextSlots, nextCareerCards, nextOffers);
                return next.hasCards() ? next : empty("");
            }
            var nextChosen = new ArrayList<>(chosen);
            boolean removed = false;
            if (TowerCard.MILK_ID.equals(cardId)) {
                for (int i = nextChosen.size() - 1; i >= 0; i--) {
                    if (cardId.equals(nextChosen.get(i).id())) {
                        nextChosen.remove(i);
                        removed = true;
                        break;
                    }
                }
            } else {
                removed = nextChosen.removeIf(ref -> cardId.equals(ref.id()));
            }
            if (!removed) return this;
            var next = new DeckSnapshot(id, nextChosen, careerSlots, careerCards, careerOffers);
            return next.hasCards() ? next : empty("");
        }

        public static DeckSnapshot creativeClear() {
            return empty("");
        }

        /** Active cards for the specified career: this job's career mythics followed by shared non-career cards. */
        public List<TowerCard.Reference> activeCards(String jobId) {
            var active = new ArrayList<TowerCard.Reference>(chosen.size() + careerSlots);
            if (jobId != null && !jobId.isEmpty()) {
                var own = careerCards.get(jobId);
                if (own != null) active.addAll(own);
            }
            active.addAll(chosen);
            return List.copyOf(active);
        }

        public List<TowerCard.Reference> cardsFor(String jobId) {
            if (jobId == null || jobId.isEmpty()) return List.of();
            return careerCards.getOrDefault(jobId, List.of());
        }

        public CareerOffer offerFor(String jobId) {
            if (jobId == null || jobId.isEmpty()) return CareerOffer.empty();
            return careerOffers.getOrDefault(jobId, CareerOffer.empty());
        }

        public int targetCount(String jobId) {
            return targetCount(TowerCard.careerMythics(jobId));
        }

        public int targetCount(List<TowerCard> careerPool) {
            if (careerSlots <= 0 || careerPool == null || careerPool.isEmpty()) return 0;
            return Math.min(careerSlots, careerPool.size());
        }

        public int pendingCount(String jobId) {
            return pendingCount(jobId, TowerCard.careerMythics(jobId));
        }

        public int pendingCount(String jobId, List<TowerCard> careerPool) {
            if (jobId == null || jobId.isEmpty()) return 0;
            int target = targetCount(careerPool);
            if (target <= 0) return 0;
            return Math.max(0, target - cardsFor(jobId).size());
        }

        public DeckSnapshot withCareer(String jobId, List<TowerCard.Reference> cards, CareerOffer offer) {
            var nextCards = new LinkedHashMap<>(careerCards);
            nextCards.put(jobId, List.copyOf(cards));
            var nextOffers = new LinkedHashMap<>(careerOffers);
            nextOffers.put(jobId, offer);
            return new DeckSnapshot(id, chosen, careerSlots, nextCards, nextOffers);
        }

        /**
         * Validates and merges an owner-submitted career mythic pick/refresh update for this deck.
         * Returns empty when the revision is stale (idempotent duplicate); throws on invalid structure.
         */
        public Optional<DeckSnapshot> applyCareerUpdate(String jobId, List<TowerCard.Reference> cards, CareerOffer offer) {
            return applyCareerUpdate(jobId, cards, offer, TowerCard.careerMythics(jobId));
        }

        public Optional<DeckSnapshot> applyCareerUpdate(String jobId, List<TowerCard.Reference> cards, CareerOffer offer,
                                                        List<TowerCard> careerPool) {
            if (jobId == null || jobId.isEmpty()) throw new IllegalArgumentException("职业标识无效");
            if (careerPool == null || careerPool.isEmpty()) throw new IllegalArgumentException("当前职业暂无可选职业神话卡");
            if (cards == null || offer == null) throw new IllegalArgumentException("职业神话卡数据不完整");
            int prevRev = offerFor(jobId).revision();
            if (offer.revision() <= prevRev) return Optional.empty();

            int target = targetCount(careerPool);
            var prevCards = cardsFor(jobId);
            if (cards.size() < prevCards.size() || cards.size() > target) {
                throw new IllegalArgumentException("职业神话卡数量无效");
            }
            for (int i = 0; i < prevCards.size(); i++) {
                if (!cards.get(i).equals(prevCards.get(i))) {
                    throw new IllegalArgumentException("已选定的职业神话卡不可替换");
                }
            }
            var allowedIds = new HashSet<String>();
            for (TowerCard card : careerPool) allowedIds.add(card.id());
            var seen = new HashSet<String>();
            var excludedByPicked = new HashSet<String>();
            for (var ref : cards) {
                if (ref == null || !allowedIds.contains(ref.id()) || !seen.add(ref.id()) || excludedByPicked.contains(ref.id())) {
                    throw new IllegalArgumentException("包含无效或重复的职业神话卡");
                }
                excludedByPicked.addAll(TowerBattleCard.exclusionsFor(ref.id()));
            }
            if (offer.slots().size() > TowerCardDraft.CARDS_PER_OFFER) {
                throw new IllegalArgumentException("职业神话卡待选数量无效");
            }
            if (cards.size() >= target && !offer.slots().isEmpty()) {
                throw new IllegalArgumentException("职业神话卡已补选完成");
            }
            var slotIds = new HashSet<String>();
            for (var slot : offer.slots()) {
                if (slot == null || slot.card() == null || !allowedIds.contains(slot.card().id())
                    || seen.contains(slot.card().id()) || excludedByPicked.contains(slot.card().id()) || !slotIds.add(slot.card().id())
                    || slot.refreshesLeft() < 0 || slot.refreshesLeft() > TowerCardDraft.REFRESHES_PER_CARD) {
                    throw new IllegalArgumentException("职业神话卡选项状态无效");
                }
            }
            return Optional.of(withCareer(jobId, cards, offer));
        }
    }

    /** Client-side state machine for stele career mythic 3-pick-1 re-picks. */
    public static final class Session {
        private final String deckId;
        private final String jobId;
        private final int targetCount;
        private final List<TowerCard> pool;
        private final Map<String, TowerCard> poolById;
        private final RandomGenerator random;
        private final List<TowerCard> picked = new ArrayList<>();
        private final Set<String> removed = new HashSet<>();
        private List<TowerCardDraft.Slot> slots = List.of();
        private int revision;

        public Session(DeckSnapshot deck, String jobId, RandomGenerator random) {
            this(deck, jobId, TowerCard.careerMythics(jobId), random);
        }

        public Session(DeckSnapshot deck, String jobId, List<TowerCard> careerPool, RandomGenerator random) {
            this.deckId = Objects.requireNonNull(deck).id();
            this.jobId = Objects.requireNonNull(jobId);
            this.pool = List.copyOf(Objects.requireNonNull(careerPool));
            this.random = Objects.requireNonNull(random);
            this.poolById = new HashMap<>();
            for (TowerCard card : this.pool) poolById.put(card.id(), card);
            this.targetCount = deck.targetCount(this.pool);
            restore(deck.cardsFor(jobId), deck.offerFor(jobId));
        }

        public String deckId() { return deckId; }
        public String jobId() { return jobId; }
        public int revision() { return revision; }
        public List<TowerCardDraft.Slot> slots() { return slots; }
        public int pending() { return Math.max(0, targetCount - picked.size()); }
        public boolean complete() { return pending() == 0; }

        public List<TowerCard.Reference> savedCards() {
            return picked.stream().map(TowerCard::reference).toList();
        }

        public CareerOffer savedOffer() {
            var savedSlots = slots.stream()
                .map(slot -> new TowerCardDraft.SavedSlot(slot.card().reference(), slot.refreshesLeft()))
                .toList();
            return new CareerOffer(revision, savedSlots, Set.copyOf(removed));
        }

        public void syncFrom(DeckSnapshot deck) {
            if (!deckId.equals(deck.id())) return;
            var offer = deck.offerFor(jobId);
            if (offer.revision() > revision) restore(deck.cardsFor(jobId), offer);
        }

        private void restore(List<TowerCard.Reference> cards, CareerOffer offer) {
            picked.clear();
            for (var ref : cards) {
                TowerCard card = poolById.get(ref.id());
                picked.add(card != null ? card : TowerCard.restore(ref));
            }
            removed.clear();
            removed.addAll(offer.removed());
            for (TowerCard card : picked) {
                removed.add(card.id());
                removed.addAll(TowerBattleCard.exclusionsFor(card.id()));
            }
            revision = offer.revision();
            var restoredSlots = new ArrayList<TowerCardDraft.Slot>(offer.slots().size());
            for (var saved : offer.slots()) {
                TowerCard card = poolById.get(saved.card().id());
                restoredSlots.add(new TowerCardDraft.Slot(card != null ? card : TowerCard.restore(saved.card()), saved.refreshesLeft()));
            }
            slots = List.copyOf(restoredSlots);
        }

        /** Deals an offer if none is open yet and picks remain. Returns true if state changed and must be persisted. */
        public boolean ensureOpen() {
            if (pending() <= 0) {
                if (!slots.isEmpty()) {
                    slots = List.of();
                    revision++;
                    return true;
                }
                return false;
            }
            if (!slots.isEmpty()) return false;
            slots = dealOffer();
            if (slots.isEmpty()) return false;
            revision++;
            return true;
        }

        public boolean canRefresh(int index) {
            if (index < 0 || index >= slots.size()) return false;
            if (slots.get(index).refreshesLeft() <= 0) return false;
            return !refreshCandidates().isEmpty();
        }

        public boolean refresh(int index, int version) {
            if (version != revision || index < 0 || index >= slots.size()) return false;
            var current = slots.get(index);
            if (current.refreshesLeft() <= 0) return false;
            var candidates = refreshCandidates();
            if (candidates.isEmpty()) return false;
            TowerCard replacement = candidates.get(random.nextInt(candidates.size())).bind(random);
            var next = new ArrayList<>(slots);
            next.set(index, new TowerCardDraft.Slot(replacement, current.refreshesLeft() - 1));
            removed.add(current.card().id());
            revision++;
            slots = List.copyOf(next);
            return true;
        }

        public Optional<TowerCard> choose(int index, int version) {
            if (version != revision || index < 0 || index >= slots.size()) return Optional.empty();
            TowerCard card = slots.get(index).card();
            picked.add(card);
            removed.add(card.id());
            removed.addAll(TowerBattleCard.exclusionsFor(card.id()));
            revision++;
            slots = pending() > 0 ? dealOffer() : List.of();
            return Optional.of(card);
        }

        private List<TowerCard> refreshCandidates() {
            var excluded = new HashSet<>(removed);
            for (TowerCard card : picked) {
                excluded.add(card.id());
                excluded.addAll(TowerBattleCard.exclusionsFor(card.id()));
            }
            for (var slot : slots) excluded.add(slot.card().id());
            var result = new ArrayList<TowerCard>();
            for (TowerCard card : pool) {
                if (!excluded.contains(card.id())) result.add(card);
            }
            return result;
        }

        private List<TowerCardDraft.Slot> dealOffer() {
            var pickedIds = new HashSet<String>();
            var excludedByPicked = new HashSet<String>();
            for (TowerCard card : picked) {
                pickedIds.add(card.id());
                excludedByPicked.addAll(TowerBattleCard.exclusionsFor(card.id()));
            }
            var preferred = new ArrayList<TowerCard>();
            var fallback = new ArrayList<TowerCard>();
            for (TowerCard card : pool) {
                if (pickedIds.contains(card.id()) || excludedByPicked.contains(card.id())) continue;
                if (!removed.contains(card.id())) preferred.add(card);
                else fallback.add(card);
            }
            int count = Math.min(TowerCardDraft.CARDS_PER_OFFER, preferred.size() + fallback.size());
            var next = new ArrayList<TowerCardDraft.Slot>(count);
            for (int i = 0; i < count; i++) {
                TowerCard card = !preferred.isEmpty()
                    ? preferred.remove(random.nextInt(preferred.size()))
                    : fallback.remove(random.nextInt(fallback.size()));
                next.add(new TowerCardDraft.Slot(card.bind(random), TowerCardDraft.REFRESHES_PER_CARD));
            }
            return List.copyOf(next);
        }
    }
}
