package cn.yumis.fight.testing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 正式测试连击台账：每个测试节点记录连续通过次数与历史失败次数。数据在 temp 下，本机不提交，清理 temp 则从零重新计数。 */
final class TestStreakLedger {
    /**
     * 删除门槛（用户 2026-11-22 确认）：历史失败 0 次 → 连续 10 次通过；
     * 失败 1 次 → 连续 15 次；失败 2 次及以上 → 连续 25 次。
     */
    static final class Node {
        /** 连续通过次数，任一次失败即归零。 */
        public int streak;
        /** 历史失败总次数，只增不减。 */
        public int failures;
        /** 累计执行次数。 */
        public int runs;

        int threshold() { return failures <= 0 ? 10 : failures == 1 ? 15 : 25; }
        boolean ready() { return streak >= threshold(); }
    }

    private static final Type NODES_TYPE = new TypeToken<Map<String, Node>>() {}.getType();

    private final Path file;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TestStreakLedger(Path file) { this.file = file; }

    static TestStreakLedger load(Path file) {
        TestStreakLedger ledger = new TestStreakLedger(file);
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Node> read = ledger.gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), NODES_TYPE);
                if (read != null) ledger.nodes.putAll(read);
            } catch (IOException | RuntimeException ignored) {
                // 台账损坏时从零开始，不阻断正式测试。
            }
        }
        return ledger;
    }

    Node node(String key) { return nodes.computeIfAbsent(key, k -> new Node()); }
    Node get(String key) { return nodes.get(key); }
    boolean remove(String key) { return nodes.remove(key) != null; }
    Map<String, Node> nodes() { return nodes; }

    /** 该测试类名下的全部节点键（含嵌套类）。 */
    List<String> keysOf(String className) {
        String prefix = className + '#';
        List<String> keys = new ArrayList<>();
        for (String key : nodes.keySet()) if (key.startsWith(prefix)) keys.add(key);
        return keys;
    }

    void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(nodes, NODES_TYPE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 台账写入失败不阻断正式测试，下次运行重新落盘。
        }
    }
}
