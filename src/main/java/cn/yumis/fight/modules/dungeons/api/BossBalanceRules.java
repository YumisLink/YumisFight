package cn.yumis.fight.modules.dungeons.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Dungeons-owned boss panel and per-move parameter rules from rules/balance/bosses/{dungeon,tower}_bosses.json. */
public final class BossBalanceRules {

    public record DungeonBossEntry(
        String title,
        double healthMultiplier,
        double attackMultiplier,
        double physicalDefenseBase,
        double magicalDefenseBase,
        double[] resists,
        Map<String, Double> params,
        Map<String, EnemyBalanceRules.TimedMoveData> moves
    ) {
        public DungeonBossEntry {
            if (!Double.isFinite(healthMultiplier) || healthMultiplier <= 0)
                throw new IllegalArgumentException("dungeonBoss.healthMultiplier must be > 0");
            if (!Double.isFinite(attackMultiplier) || attackMultiplier < 0)
                throw new IllegalArgumentException("dungeonBoss.attackMultiplier must be >= 0");
            if (resists != null) resists = resists.clone();
            params = Map.copyOf(Objects.requireNonNull(params));
            moves = Map.copyOf(Objects.requireNonNull(moves));
        }
        @Override public double[] resists() { return resists == null ? null : resists.clone(); }
        public double param(String key, double fallback) { return params.getOrDefault(key, fallback); }
        public EnemyBalanceRules.TimedMoveData move(String key) { return moves.get(key); }
    }

    public record TowerBossMember(String title, double healthMultiplier, String attackGrade) {
        public TowerBossMember {
            if (!Double.isFinite(healthMultiplier) || healthMultiplier <= 0)
                throw new IllegalArgumentException("towerBossMember.healthMultiplier must be > 0");
            Objects.requireNonNull(attackGrade);
        }
        public double attackMultiplier() {
            return EnemyBalanceRules.current().towerClimb.gradeMultiplier(attackGrade, TowerBossGrade.valueOf(attackGrade).multiplier());
        }
    }

    public record TowerBossEntry(
        String title,
        String healthGrade,
        double healthScale,
        String attackGrade,
        double physicalDefenseBase,
        double magicalDefenseBase,
        double[] resists,
        Map<String, TowerBossMember> members,
        Map<String, Long> skillCooldowns,
        Map<String, Double> numbers,
        Map<String, double[]> arrays
    ) {
        public TowerBossEntry {
            if (!Double.isFinite(healthScale) || healthScale <= 0)
                throw new IllegalArgumentException("towerBoss.healthScale must be > 0");
            if (resists != null) resists = resists.clone();
            members = Map.copyOf(Objects.requireNonNull(members));
            skillCooldowns = Map.copyOf(Objects.requireNonNull(skillCooldowns));
            numbers = Map.copyOf(Objects.requireNonNull(numbers));
            Map<String, double[]> copiedArrays = new LinkedHashMap<>();
            for (var e : arrays.entrySet()) copiedArrays.put(e.getKey(), e.getValue().clone());
            arrays = Map.copyOf(copiedArrays);
        }
        @Override public double[] resists() { return resists == null ? null : resists.clone(); }

        public double healthMultiplier() {
            if (healthGrade == null || healthGrade.isEmpty()) return healthScale;
            return EnemyBalanceRules.current().towerClimb.gradeMultiplier(healthGrade, TowerBossGrade.valueOf(healthGrade).multiplier()) * healthScale;
        }

        public double attackMultiplier() {
            if (attackGrade == null || attackGrade.isEmpty()) return 1.0;
            return EnemyBalanceRules.current().towerClimb.gradeMultiplier(attackGrade, TowerBossGrade.valueOf(attackGrade).multiplier());
        }

        public double num(String key, double fallback) { return numbers.getOrDefault(key, fallback); }
        public long ms(String key, long fallback) {
            Double v = numbers.get(key);
            return v == null ? fallback : Math.round(v);
        }
        public int integer(String key, int fallback) {
            Double v = numbers.get(key);
            return v == null ? fallback : (int) Math.round(v);
        }
        public double[] array(String key, double[] fallback) {
            double[] v = arrays.get(key);
            return v == null ? fallback.clone() : v.clone();
        }
        public long skillCooldown(String skillId, long fallback) {
            return skillCooldowns.getOrDefault(skillId, fallback);
        }
        public TowerBossMember member(String id) { return members.get(id); }
    }

    public final Map<String, DungeonBossEntry> dungeonBosses;
    public final Map<String, TowerBossEntry> towerBosses;

    public BossBalanceRules(Map<String, DungeonBossEntry> dungeonBosses, Map<String, TowerBossEntry> towerBosses) {
        this.dungeonBosses = Map.copyOf(Objects.requireNonNull(dungeonBosses));
        this.towerBosses = Map.copyOf(Objects.requireNonNull(towerBosses));
    }

    private static volatile BossBalanceRules DEFAULT;
    private static volatile Supplier<BossBalanceRules> source;

    public static BossBalanceRules defaults() {
        BossBalanceRules v = DEFAULT;
        if (v == null) {
            synchronized (BossBalanceRules.class) {
                v = DEFAULT;
                if (v == null) DEFAULT = v = loadDefaults();
            }
        }
        return v;
    }

    public static BossBalanceRules current() {
        var provider = source;
        return provider == null ? defaults() : Objects.requireNonNull(provider.get());
    }

    public static void source(Supplier<BossBalanceRules> provider) {
        source = Objects.requireNonNull(provider);
    }

    public DungeonBossEntry dungeonBoss(String id) { return dungeonBosses.get(id); }
    public TowerBossEntry towerBoss(String id) { return towerBosses.get(id); }

    public double towerNum(String bossId, String key, double fallback) {
        TowerBossEntry e = towerBosses.get(bossId);
        return e == null ? fallback : e.num(key, fallback);
    }

    public long towerMs(String bossId, String key, long fallback) {
        TowerBossEntry e = towerBosses.get(bossId);
        return e == null ? fallback : e.ms(key, fallback);
    }

    public int towerInt(String bossId, String key, int fallback) {
        TowerBossEntry e = towerBosses.get(bossId);
        return e == null ? fallback : e.integer(key, fallback);
    }

    public double[] towerArray(String bossId, String key, double[] fallback) {
        TowerBossEntry e = towerBosses.get(bossId);
        return e == null ? fallback.clone() : e.array(key, fallback);
    }

    public void validate() {
        for (String required : new String[]{"seal_tower_elaina", "theater_tower_liangyue", "frost_tower_frostnova", "orrery_tower_gilgamesh", "forge_tower_sword_masters", "sluice_tower_roxy"}) {
            if (!towerBosses.containsKey(required)) throw new IllegalArgumentException("tower_bosses.json 缺少觉醒塔 Boss：" + required);
        }
        if (!dungeonBosses.containsKey("goblin_camp_keno")) {
            throw new IllegalArgumentException("dungeon_bosses.json 缺少哥布林营地 Boss：goblin_camp_keno");
        }
        if (!dungeonBosses.containsKey("dragon_tower_lucas")) {
            throw new IllegalArgumentException("dungeon_bosses.json 缺少龙人之塔 Boss：dragon_tower_lucas");
        }
    }

    private static BossBalanceRules loadDefaults() {
        try {
            return parseAll(
                readResource("/data/yumis_dungeon_and_fight/rules/balance/bosses/dungeon_bosses.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/bosses/tower_bosses.json")
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readResource(String path) throws IOException {
        try (var in = BossBalanceRules.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static BossBalanceRules parseAll(String dungeonBossesJson, String towerBossesJson) {
        JsonObject dbRoot = JsonParser.parseString(dungeonBossesJson).getAsJsonObject();
        JsonObject tbRoot = JsonParser.parseString(towerBossesJson).getAsJsonObject();
        return new BossBalanceRules(
            parseDungeonBosses(dbRoot.getAsJsonObject("bosses")),
            parseTowerBosses(tbRoot.getAsJsonObject("bosses"))
        );
    }

    public static BossBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return new BossBalanceRules(
            parseDungeonBosses(root.getAsJsonObject("dungeonBosses")),
            parseTowerBosses(root.getAsJsonObject("towerBosses"))
        );
    }

    private static Map<String, DungeonBossEntry> parseDungeonBosses(JsonObject obj) {
        Map<String, DungeonBossEntry> out = new LinkedHashMap<>();
        if (obj == null) return out;
        for (var entry : obj.entrySet()) {
            JsonObject o = entry.getValue().getAsJsonObject();
            String title = o.has("title") ? o.get("title").getAsString() : entry.getKey();
            double hp = o.has("healthMultiplier") ? o.get("healthMultiplier").getAsDouble() : 1.0;
            double atk = o.has("attackMultiplier") ? o.get("attackMultiplier").getAsDouble() : 1.0;
            double pDef = o.has("physicalDefenseBase") ? o.get("physicalDefenseBase").getAsDouble() : -1.0;
            double mDef = o.has("magicalDefenseBase") ? o.get("magicalDefenseBase").getAsDouble() : -1.0;
            double[] resists = readDoubleArray(o.get("resists"));
            Map<String, Double> params = new LinkedHashMap<>();
            if (o.has("params") && o.get("params").isJsonObject()) {
                for (var p : o.getAsJsonObject("params").entrySet()) {
                    if (p.getValue().isJsonPrimitive() && p.getValue().getAsJsonPrimitive().isNumber())
                        params.put(p.getKey(), p.getValue().getAsDouble());
                }
            }
            for (var f : o.entrySet()) {
                String k = f.getKey();
                if (k.equals("title") || k.equals("healthMultiplier") || k.equals("attackMultiplier")
                    || k.equals("physicalDefenseBase") || k.equals("magicalDefenseBase")
                    || k.equals("resists") || k.equals("moves") || k.equals("params")) continue;
                if (f.getValue().isJsonPrimitive() && f.getValue().getAsJsonPrimitive().isNumber()) {
                    params.put(k, f.getValue().getAsDouble());
                }
            }
            Map<String, EnemyBalanceRules.TimedMoveData> moves = new LinkedHashMap<>();
            if (o.has("moves") && o.get("moves").isJsonObject()) {
                for (var m : o.getAsJsonObject("moves").entrySet()) {
                    JsonObject mo = m.getValue().getAsJsonObject();
                    moves.put(m.getKey(), new EnemyBalanceRules.TimedMoveData(
                        mo.has("animation") ? mo.get("animation").getAsString() : "",
                        mo.has("shape") ? mo.get("shape").getAsString() : "",
                        mo.has("windup") ? mo.get("windup").getAsLong() : 0L,
                        mo.has("active") ? mo.get("active").getAsLong() : 0L,
                        mo.has("recovery") ? mo.get("recovery").getAsLong() : 0L,
                        mo.has("duration") ? mo.get("duration").getAsLong() : 0L,
                        mo.has("cooldown") ? mo.get("cooldown").getAsLong() : 0L,
                        mo.has("range") ? mo.get("range").getAsDouble() : 0d,
                        mo.has("coefficient") ? mo.get("coefficient").getAsDouble() : 0d,
                        mo.has("magic") && mo.get("magic").getAsBoolean(),
                        mo.has("element") ? mo.get("element").getAsInt() : -1,
                        mo.has("hits") ? mo.get("hits").getAsInt() : 1,
                        mo.has("interval") ? mo.get("interval").getAsLong() : 0L
                    ));
                }
            }
            out.put(entry.getKey(), new DungeonBossEntry(title, hp, atk, pDef, mDef, resists, params, moves));
        }
        return out;
    }

    private static Map<String, TowerBossEntry> parseTowerBosses(JsonObject obj) {
        Map<String, TowerBossEntry> out = new LinkedHashMap<>();
        if (obj == null) return out;
        for (var entry : obj.entrySet()) {
            JsonObject o = entry.getValue().getAsJsonObject();
            String title = o.has("title") ? o.get("title").getAsString() : entry.getKey();
            String hGrade = o.has("healthGrade") && !o.get("healthGrade").isJsonNull() ? o.get("healthGrade").getAsString() : "";
            double hScale = o.has("healthScale") ? o.get("healthScale").getAsDouble() : 1.0;
            String aGrade = o.has("attackGrade") && !o.get("attackGrade").isJsonNull() ? o.get("attackGrade").getAsString() : "";
            double pDef = o.has("physicalDefenseBase") ? o.get("physicalDefenseBase").getAsDouble() : -1.0;
            double mDef = o.has("magicalDefenseBase") ? o.get("magicalDefenseBase").getAsDouble() : -1.0;
            double[] resists = readDoubleArray(o.get("resists"));
            Map<String, TowerBossMember> members = new LinkedHashMap<>();
            if (o.has("members") && o.get("members").isJsonObject()) {
                for (var m : o.getAsJsonObject("members").entrySet()) {
                    JsonObject mo = m.getValue().getAsJsonObject();
                    members.put(m.getKey(), new TowerBossMember(
                        mo.has("title") ? mo.get("title").getAsString() : m.getKey(),
                        mo.get("healthMultiplier").getAsDouble(),
                        mo.get("attackGrade").getAsString()
                    ));
                }
            }
            Map<String, Long> skillCooldowns = new LinkedHashMap<>();
            if (o.has("skillCooldowns") && o.get("skillCooldowns").isJsonObject()) {
                for (var c : o.getAsJsonObject("skillCooldowns").entrySet()) {
                    skillCooldowns.put(c.getKey(), c.getValue().getAsLong());
                }
            }
            Map<String, Double> numbers = new LinkedHashMap<>();
            Map<String, double[]> arrays = new LinkedHashMap<>();
            JsonObject paramsObj = o.has("params") ? o.getAsJsonObject("params")
                : o.has("numbers") ? o.getAsJsonObject("numbers") : null;
            if (paramsObj != null) {
                for (var p : paramsObj.entrySet()) {
                    if (p.getValue().isJsonPrimitive() && p.getValue().getAsJsonPrimitive().isNumber()) {
                        numbers.put(p.getKey(), p.getValue().getAsDouble());
                    } else if (p.getValue().isJsonArray()) {
                        arrays.put(p.getKey(), readDoubleArray(p.getValue()));
                    }
                }
            }
            if (o.has("arrays") && o.get("arrays").isJsonObject()) {
                for (var a : o.getAsJsonObject("arrays").entrySet()) {
                    arrays.put(a.getKey(), readDoubleArray(a.getValue()));
                }
            }
            out.put(entry.getKey(), new TowerBossEntry(title, hGrade, hScale, aGrade, pDef, mDef, resists, members, skillCooldowns, numbers, arrays));
        }
        return out;
    }

    private static double[] readDoubleArray(JsonElement el) {
        if (el == null || !el.isJsonArray()) return null;
        JsonArray arr = el.getAsJsonArray();
        double[] out = new double[arr.size()];
        for (int i = 0; i < out.length; i++) out[i] = arr.get(i).getAsDouble();
        return out;
    }
}
