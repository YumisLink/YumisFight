package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.modules.character.api.CareerProfiles;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.presentation.api.RichText;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.*;

/** The shared promotion quest definition and its saved personal clear facts. */
public final class PromotionQuest {
    public static final String ACTION = "promotionQuest";
    public static final int LEVEL = 20;
    public static final Difficulty DIFFICULTY = Difficulty.KING;
    public static final List<String> DUNGEONS = List.of("goblin_camp", "dragon_tower", "castle_palace");
    /** Same gold-green gradient as the awakening badge. */
    public static final RichText BADGE = new RichText(List.of(new RichText.Span("转职试炼", List.of("#F6D46C", "#76D68A"))));
    private static final Gson JSON = new Gson();
    private PromotionQuest() {}

    /** KING story clears count at any level; the altar unlock still requires the level. */
    public record WorldProgress(Map<String, Integer> clears) {
        public static final WorldProgress EMPTY = new WorldProgress(Map.of());

        public WorldProgress { clears = Map.copyOf(clears); }

        public int clears(String dungeon) { return clears.getOrDefault(dungeon, 0); }

        public WorldProgress clear(String dungeon) {
            if (!DUNGEONS.contains(dungeon)) throw new IllegalArgumentException("未知转职试炼副本");
            var next = new LinkedHashMap<>(clears);
            next.put(dungeon, Math.incrementExact(clears(dungeon)));
            return new WorldProgress(next);
        }

        public boolean complete() { return DUNGEONS.stream().allMatch(dungeon -> clears(dungeon) >= 1); }
    }

    /** Snapshot equality keeps the owner working on the same stored facts. */
    public static void checkSnapshots(Progress before, WorldProgress snapshot, JsonObject request) {
        if (!JSON.toJsonTree(before).equals(request.get("beforeProgress"))) {
            throw new IllegalArgumentException("角色资料已更新，请重试");
        }
        if (!JSON.toJsonTree(snapshot).equals(request.get("beforeWorld"))) {
            throw new IllegalArgumentException("转职试炼进度已变化，请重试");
        }
    }

    /** The unlock itself stays owner-authored; this only checks facts, level and the single quest addition. */
    public static Progress unlock(Progress before, WorldProgress snapshot, JsonObject request) {
        if (before.jobChangeUnlocked()) throw new IllegalArgumentException("已经拥有通用转职资格");
        if (before.level < LEVEL) throw new IllegalArgumentException("转职试炼需要20级");
        if (!snapshot.complete()) throw new IllegalArgumentException("王者级三大副本尚未全部通关");
        Progress next = JSON.fromJson(request.get("progress"), Progress.class);
        CareerProfiles.storedProgress(next);
        if (!next.jobChangeUnlocked() || !next.completedJobQuests.containsAll(before.completedJobQuests)
                || next.completedJobQuests.size() != before.completedJobQuests.size() + 1) {
            throw new IllegalArgumentException("转职试炼结果格式无效");
        }
        return next;
    }
}
