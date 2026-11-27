package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.character.api.JobCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Full card library catalog, categories, ordering and multi-keyword search. */
public final class TowerCardCatalog {
    public enum Kind {
        ALL,
        OWNED,
        RARITY,
        GENERIC_MYTHIC,
        ALL_CAREER_MYTHIC,
        JOB
    }

    public record Category(String id, String label, Kind kind, TowerCard.Rarity rarity, String jobId) {
        public boolean isJob() { return kind == Kind.JOB; }
    }

    public record Entry(
        int index,
        TowerCard card,
        String plainDescription,
        String rarityLabel,
        String jobTitle,
        String fixedSkill,
        String searchText
    ) {}

    private static final List<Entry> ALL_ENTRIES = buildEntries();
    private static final Map<String, Entry> BY_ID = indexEntries(ALL_ENTRIES);
    private static final List<String> CAREER_JOBS = discoverJobs(ALL_ENTRIES);

    private TowerCardCatalog() {}

    public static List<Entry> all() {
        return ALL_ENTRIES;
    }

    public static Entry find(String cardId) {
        return cardId == null ? null : BY_ID.get(cardId);
    }

    public static List<Category> categories(String currentJobId) {
        var result = new ArrayList<Category>(7 + CAREER_JOBS.size());
        result.add(new Category("all", "全部", Kind.ALL, null, ""));
        result.add(new Category("owned", "已持有", Kind.OWNED, null, ""));
        result.add(new Category("common", "普通", Kind.RARITY, TowerCard.Rarity.COMMON, ""));
        result.add(new Category("rare", "稀有", Kind.RARITY, TowerCard.Rarity.RARE, ""));
        result.add(new Category("epic", "史诗", Kind.RARITY, TowerCard.Rarity.EPIC, ""));
        result.add(new Category("generic_mythic", "通用神话", Kind.GENERIC_MYTHIC, TowerCard.Rarity.MYTHIC, ""));
        result.add(new Category("career_mythic", "职业神话", Kind.ALL_CAREER_MYTHIC, TowerCard.Rarity.MYTHIC, ""));

        var orderedJobs = new ArrayList<String>(CAREER_JOBS.size());
        if (currentJobId != null && CAREER_JOBS.contains(currentJobId)) {
            orderedJobs.add(currentJobId);
        }
        for (String job : CAREER_JOBS) {
            if (!job.equals(currentJobId)) orderedJobs.add(job);
        }
        for (String job : orderedJobs) {
            result.add(new Category("job:" + job, jobTitle(job), Kind.JOB, TowerCard.Rarity.MYTHIC, job));
        }
        return List.copyOf(result);
    }

    public static boolean matchesCategory(Entry entry, Category category, Set<String> heldIds) {
        if (category == null) return true;
        TowerCard card = entry.card();
        return switch (category.kind()) {
            case ALL -> true;
            case OWNED -> heldIds != null && heldIds.contains(card.id());
            case RARITY -> card.rarity() == category.rarity() && !card.careerMythic();
            case GENERIC_MYTHIC -> card.rarity() == TowerCard.Rarity.MYTHIC && !card.careerMythic();
            case ALL_CAREER_MYTHIC -> card.careerMythic();
            case JOB -> card.careerMythic() && card.job().equals(category.jobId());
        };
    }

    public static boolean matchesQuery(Entry entry, String query, TowerCard heldInstance) {
        if (query == null || query.isBlank()) return true;
        String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String extra = "";
        if (heldInstance != null && (!heldInstance.variant().isEmpty() || !heldInstance.variantTitle().isEmpty())) {
            extra = " " + heldInstance.variant().toLowerCase(Locale.ROOT) + " "
                + heldInstance.variantTitle().toLowerCase(Locale.ROOT) + " "
                + heldInstance.description().plain().toLowerCase(Locale.ROOT);
        }
        String haystack = extra.isEmpty() ? entry.searchText() : entry.searchText() + extra;
        for (String token : tokens) {
            if (!token.isEmpty() && !haystack.contains(token)) return false;
        }
        return true;
    }

    public static int count(Category category, Set<String> heldIds) {
        int total = 0;
        for (Entry entry : ALL_ENTRIES) {
            if (matchesCategory(entry, category, heldIds)) total++;
        }
        return total;
    }

    public static List<Entry> filter(Category category, String query, String currentJobId,
                                     Set<String> heldIds, Map<String, TowerCard> heldCards) {
        var result = new ArrayList<Entry>();
        for (Entry entry : ALL_ENTRIES) {
            if (!matchesCategory(entry, category, heldIds)) continue;
            TowerCard held = heldCards == null ? null : heldCards.get(entry.card().id());
            if (!matchesQuery(entry, query, held)) continue;
            result.add(entry);
        }
        String activeJob = currentJobId == null ? "" : currentJobId;
        result.sort(Comparator
            .comparingInt((Entry e) -> groupOrder(e, activeJob))
            .thenComparingInt(e -> jobOrder(e.card().job(), activeJob))
            .thenComparingInt(Entry::index));
        return List.copyOf(result);
    }

    public static String jobTitle(String jobId) {
        if (jobId == null || jobId.isEmpty()) return "";
        try {
            return JobCatalog.require(jobId).title();
        } catch (IllegalArgumentException ignored) {
            return jobId;
        }
    }

    private static int groupOrder(Entry entry, String activeJob) {
        TowerCard card = entry.card();
        if (card.careerMythic()) {
            return card.job().equals(activeJob) ? 5 : 6;
        }
        return switch (card.rarity()) {
            case MYTHIC -> 0;
            case EPIC -> 1;
            case RARE -> 2;
            case COMMON -> card.milk() ? 4 : 3;
        };
    }

    private static int jobOrder(String jobId, String activeJob) {
        if (jobId == null || jobId.isEmpty()) return -1;
        if (jobId.equals(activeJob)) return 0;
        int idx = CAREER_JOBS.indexOf(jobId);
        return idx < 0 ? 1000 : idx + 1;
    }

    private static List<Entry> buildEntries() {
        var cards = TowerCard.catalog();
        var entries = new ArrayList<Entry>(cards.size());
        for (int index = 0; index < cards.size(); index++) {
            TowerCard card = cards.get(index);
            String plain = card.description().plain();
            String rarityLabel = switch (card.rarity()) {
                case COMMON -> card.milk() ? "保底 普通" : "普通";
                case RARE -> "稀有";
                case EPIC -> "史诗";
                case MYTHIC -> card.careerMythic() ? "职业神话 神话" : "通用神话 神话";
            };
            String jobTitle = jobTitle(card.job());
            TowerBattleCard battle = TowerBattleCard.find(card.id());
            String fixedSkill = battle == null ? "" : battle.fixedSkill();
            String bindingLabel = card.requiresWeaponBinding() ? "特定武器类型 武器"
                : card.skillBinding().label();
            String search = (card.name() + " " + card.id() + " " + plain + " "
                + rarityLabel + " " + jobTitle + " " + card.job() + " "
                + fixedSkill + " " + bindingLabel).toLowerCase(Locale.ROOT);
            entries.add(new Entry(index, card, plain, rarityLabel, jobTitle, fixedSkill, search));
        }
        return List.copyOf(entries);
    }

    private static Map<String, Entry> indexEntries(List<Entry> entries) {
        var map = new LinkedHashMap<String, Entry>(entries.size());
        for (Entry entry : entries) map.putIfAbsent(entry.card().id(), entry);
        return Map.copyOf(map);
    }

    private static List<String> discoverJobs(List<Entry> entries) {
        var present = new LinkedHashSet<String>();
        for (Entry entry : entries) {
            if (entry.card().careerMythic()) present.add(entry.card().job());
        }
        var ordered = new ArrayList<String>(present.size());
        for (var job : JobCatalog.all()) {
            if (present.remove(job.id())) ordered.add(job.id());
        }
        ordered.addAll(present);
        return List.copyOf(ordered);
    }
}
