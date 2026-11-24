package cn.yumis.fight.modules.dungeons.api;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared tower progress. The player's card contents are kept in that player's private view. */
public final class TowerClimbRun {
    public enum Stage { SELECTING, FIGHTING, RECOVERING, COMPLETED }

    public Stage stage = Stage.SELECTING;
    public int floor = 1;
    public int floorStarts;
    public int checkpointFloor = 1;
    public int offer;
    public String boss = "";
    public UUID bossEntity;
    public Set<String> clearedRandomBosses = new LinkedHashSet<>();
    public Map<UUID, Integer> selected = new LinkedHashMap<>();
    public Set<UUID> keepingCards = new LinkedHashSet<>();

    public boolean hasSelected(UUID player) {
        return keepingCards.contains(player) || selected.getOrDefault(player, -1) >= offer;
    }

    public boolean fighting() {
        return stage == Stage.FIGHTING;
    }

    public boolean canRecover(UUID player) {
        return !fighting() || !hasSelected(player);
    }
}
