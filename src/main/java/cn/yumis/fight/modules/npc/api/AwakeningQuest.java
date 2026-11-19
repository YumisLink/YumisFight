package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.modules.presentation.api.RichText;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** The shared first-awakening quest definition and its saved personal progress. */
public final class AwakeningQuest {
    public static final String ACTION = "awakeningQuest";
    public static final String CHANT_PAGE = "unfinished_chant_page";
    public static final String RELIQUARY = "white_reliquary";
    private static final Identifier ALTAR = Identifier.fromNamespaceAndPath(ItemData.NS, "demon_altar");

    public enum Stage {
        LOCKED, MATERIALS, CONFESSION, TOWER, ELAINA, COMPLETE;

        public Stage next() {
            return switch (this) {
                case LOCKED -> MATERIALS;
                case MATERIALS -> CONFESSION;
                case CONFESSION -> TOWER;
                case TOWER -> ELAINA;
                case ELAINA -> COMPLETE;
                case COMPLETE -> throw new IllegalStateException("觉醒任务已经完成");
            };
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Material(String id, String name, int count) {
        public Material {
            if (!validId(id) || name == null || name.isBlank() || count < 1) {
                throw new IllegalArgumentException("觉醒材料定义无效");
            }
        }
    }

    public record Boss(String id, String name, int level, int clears, List<Material> materials) {
        public Boss {
            if (!validId(id) || name == null || name.isBlank() || level < 1 || clears < 1
                    || materials == null || materials.isEmpty()) {
                throw new IllegalArgumentException("觉醒首领目标无效");
            }
            materials = List.copyOf(materials);
        }
    }

    public record Definition(int level, RichText badge, List<Boss> bosses,
                             String confession, String towerGoal, List<RichText> dialogue) {
        public Definition {
            if (level < 1 || badge == null || badge.plain().isBlank() || bosses == null || bosses.isEmpty()
                    || !validId(confession) || !validId(towerGoal) || dialogue == null || dialogue.isEmpty()) {
                throw new IllegalArgumentException("觉醒任务定义不完整");
            }
            bosses = List.copyOf(bosses);
            dialogue = List.copyOf(dialogue);
            var bossIds = new HashSet<String>();
            var materialIds = new HashSet<String>();
            for (Boss boss : bosses) {
                if (!bossIds.add(boss.id()) || boss.id().equals(confession)) {
                    throw new IllegalArgumentException("觉醒首领目标重复");
                }
                for (Material material : boss.materials()) {
                    if (!materialIds.add(material.id())) throw new IllegalArgumentException("觉醒材料重复");
                }
            }
        }

        public boolean tracks(String bossId) {
            return confession.equals(bossId) || bosses.stream().anyMatch(boss -> boss.id().equals(bossId));
        }

        public List<Material> materials() {
            return bosses.stream().flatMap(boss -> boss.materials().stream()).toList();
        }
    }

    public record State(Stage stage, int confessionBaseline, int towerBaseline) {
        public static final State INITIAL = new State(Stage.LOCKED, 0, 0);

        public State {
            Objects.requireNonNull(stage);
            if (confessionBaseline < 0 || towerBaseline < 0) throw new IllegalArgumentException("觉醒任务快照无效");
        }

        public State advance(WorldProgress progress, Definition definition) {
            int confession = stage == Stage.MATERIALS ? progress.clears(definition.confession()) : confessionBaseline;
            int tower = stage == Stage.CONFESSION ? progress.towerClears() : towerBaseline;
            return new State(stage.next(), confession, tower);
        }
    }

    public record WorldProgress(Map<String, Integer> altarClears, boolean chantPageAwarded, int towerClears) {
        public static final WorldProgress EMPTY = new WorldProgress(Map.of(), false, 0);

        public WorldProgress { altarClears = Map.copyOf(altarClears); }

        public int clears(String bossId) { return altarClears.getOrDefault(bossId, 0); }

        public WorldProgress altarClear(String bossId, boolean capture) {
            var next = new LinkedHashMap<>(altarClears);
            next.put(bossId, Math.incrementExact(clears(bossId)));
            return new WorldProgress(next, chantPageAwarded || grantsChantPage(bossId, capture), towerClears);
        }

        public boolean grantsChantPage(String bossId, boolean capture) {
            return RELIQUARY.equals(bossId) && (capture || !chantPageAwarded && clears(bossId) >= 1);
        }

        public WorldProgress towerClear() {
            return new WorldProgress(altarClears, chantPageAwarded, Math.incrementExact(towerClears));
        }
    }

    private AwakeningQuest() {}

    public static boolean isAltar(BlockState state) {
        return ALTAR.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static Definition definition() {
        var elaina = NpcDefinitions.current().npcs.get(NpcTutorial.NPC);
        return elaina != null && elaina.awakening() != null ? elaina.awakening() : Defaults.DEFINITION;
    }

    private static final class Defaults {
        static final Definition DEFINITION = Objects.requireNonNull(NpcDefinitions.defaults().require(NpcTutorial.NPC).awakening());
    }

    private static boolean validId(String id) { return id != null && id.matches("[a-z0-9_]+"); }
}
