package cn.yumis.fight.testing;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 正式测试连击记录（经 META-INF/services 自动注册，junit-platform.properties 开启扩展自动发现）。
 *
 * 规则（用户 2026-11-22 确认）：每个测试方法节点在 temp/test-streaks.json 记录连续通过与历史失败；
 * 达到删除门槛（失败 0 次→连续 10 次，1 次→15 次，2 次及以上→25 次）时自动删除该测试方法，
 * 所在类因此没有测试方法时连同类文件删除。台账与报告都在 temp 下，本机不提交。
 */
public final class TestStreakExtension implements BeforeAllCallback, AfterAllCallback, TestWatcher {
    private static final Object LOCK = new Object();
    private static Path projectRoot;
    private static TestStreakLedger ledger;
    private static int sessionPass;
    private static int sessionFail;
    private static final List<String> sessionDeletions = new ArrayList<>();

    private static Path projectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir", "."));
        Path candidate = dir;
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("src").resolve("test").resolve("java"))) return candidate;
            candidate = candidate.getParent();
        }
        return dir;
    }

    private static TestStreakLedger ledger() {
        synchronized (LOCK) {
            if (ledger == null) {
                projectRoot = projectRoot();
                ledger = TestStreakLedger.load(projectRoot.resolve("temp").resolve("test-streaks.json"));
            }
            return ledger;
        }
    }

    private static String nodeKey(ExtensionContext context) {
        if (context.getTestClass().isEmpty() || context.getTestMethod().isEmpty()) return null;
        return context.getTestClass().get().getName() + '#' + context.getTestMethod().get().getName();
    }

    @Override public void beforeAll(ExtensionContext context) { ledger(); }

    @Override public void testSuccessful(ExtensionContext context) {
        record(context, true);
    }

    @Override public void testFailed(ExtensionContext context, Throwable cause) {
        record(context, false);
    }

    /** 假设失败与禁用不计入连击。 */
    @Override public void testAborted(ExtensionContext context, Throwable cause) { }
    @Override public void testDisabled(ExtensionContext context, Optional<String> reason) { }

    private static void record(ExtensionContext context, boolean passed) {
        String key = nodeKey(context);
        if (key == null) return;
        synchronized (LOCK) {
            TestStreakLedger.Node node = ledger().node(key);
            node.runs++;
            if (passed) { node.streak++; sessionPass++; }
            else { node.streak = 0; node.failures++; sessionFail++; }
            ledger().save();
        }
    }

    @Override public void afterAll(ExtensionContext context) {
        if (context.getTestClass().isEmpty()) return;
        String className = context.getTestClass().get().getName();
        synchronized (LOCK) {
            TestStreakLedger book = ledger();
            for (String key : book.keysOf(className)) {
                TestStreakLedger.Node node = book.get(key);
                if (node == null || !node.ready()) continue;
                String method = key.substring(className.length() + 1);
                TestSourcePruner.Result result = TestSourcePruner.prune(projectRoot, className, method);
                if (result == TestSourcePruner.Result.IO_ERROR || result == TestSourcePruner.Result.SOURCE_ROOT_MISSING) continue;
                book.remove(key);
                if (result == TestSourcePruner.Result.METHOD_REMOVED || result == TestSourcePruner.Result.CLASS_REMOVED) {
                    String line = key + " 连续" + node.streak + "次/历史失败" + node.failures + "次/门槛" + node.threshold()
                            + " → " + (result == TestSourcePruner.Result.CLASS_REMOVED ? "已删方法并删除空类文件" : "已删方法");
                    sessionDeletions.add(line);
                    System.out.println("[test-streaks] " + line);
                }
            }
            book.save();
            writeReport();
        }
    }

    private static void writeReport() {
        StringBuilder report = new StringBuilder();
        report.append("正式测试连击台账报告\n");
        report.append("更新：").append(LocalDateTime.now().withNano(0)).append('\n');
        report.append("规则：历史失败 0 次 → 连续 10 次通过自动删除；失败 1 次 → 连续 15 次；失败 2 次及以上 → 连续 25 次。\n");
        report.append("台账与报告都在 temp 下，本机不提交；清理 temp 会从零重新计数。\n");
        report.append("本次运行：通过 ").append(sessionPass).append("，失败 ").append(sessionFail).append('\n');
        if (!sessionDeletions.isEmpty()) {
            report.append("\n[本次运行自动删除]\n");
            for (String line : sessionDeletions) report.append("  ").append(line).append('\n');
        }
        List<Map.Entry<String, TestStreakLedger.Node>> pending = new ArrayList<>();
        for (Map.Entry<String, TestStreakLedger.Node> entry : ledger().nodes().entrySet())
            if (!entry.getValue().ready()) pending.add(entry);
        pending.sort(Comparator.comparingInt(entry -> entry.getValue().threshold() - entry.getValue().streak));
        report.append("\n[未达标节点 差值升序 前40]\n");
        int shown = 0;
        for (Map.Entry<String, TestStreakLedger.Node> entry : pending) {
            if (shown++ >= 40) break;
            TestStreakLedger.Node node = entry.getValue();
            report.append(String.format("  %s  连续%d/门槛%d  历史失败%d  还差%d%n",
                    entry.getKey(), node.streak, node.threshold(), node.failures, node.threshold() - node.streak));
        }
        try {
            Files.createDirectories(projectRoot.resolve("temp"));
            Files.writeString(projectRoot.resolve("temp").resolve("test-streaks-report.txt"),
                    report.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 报告写不进去不影响测试本身。
        }
    }
}
