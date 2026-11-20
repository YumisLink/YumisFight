package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.character.api.CareerProfiles;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.npc.api.AwakeningQuest;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.Stage;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.State;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.WorldProgress;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.npc.api.NpcTutorial;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Supplier;

/** The owning client checks quest goals and plans the inventory and role changes. */
final class AwakeningQuestClient {
    private static final Gson JSON = new Gson();
    private static Supplier<Progress> growth;
    private static Supplier<JsonObject> profile;
    private static JsonObject view;
    private static State state = State.INITIAL;
    private static WorldProgress world = WorldProgress.EMPTY;
    private static boolean pending;
    private static long retryAt;

    private record MaterialSource(String area, int slot, ItemStack stack) {}

    private AwakeningQuestClient() {}

    static void install(CommandRouter<Void, JsonObject> preparations, Supplier<Progress> progress,
                        Supplier<JsonObject> profileView) {
        growth = progress;
        profile = profileView;
        preparations.register("npc", AwakeningQuest.ACTION, (ignored, request) -> prepare(request));
    }

    static void receive(JsonObject profile) {
        var incoming = profile.getAsJsonObject("awakeningQuest");
        if (Objects.equals(view, incoming)) return;
        view = incoming;
        state = view == null ? State.INITIAL : JSON.fromJson(view.get("state"), State.class);
        world = view == null ? WorldProgress.EMPTY : JSON.fromJson(view.get("world"), WorldProgress.class);
        TutorialPage.refresh();
    }

    static void clear() {
        view = null;
        state = State.INITIAL;
        world = WorldProgress.EMPTY;
        pending = false;
        retryAt = 0;
    }

    private static boolean available() { return view != null && ClientUi.current().ready() && TutorialClient.alive(); }

    private static boolean reachedLevel(AwakeningQuest.Definition definition) {
        if (growth.get().level >= definition.level()) return true;
        var book = profile.get().getAsJsonObject("careerProfiles");
        if (book == null) return false;
        for (var row : book.getAsJsonArray("entries")) {
            if (row.getAsJsonObject().get("level").getAsInt() >= definition.level()) return true;
        }
        return false;
    }

    private static boolean towerOpen() { return view != null && view.get("towerOpen").getAsBoolean(); }

    static void tick() {
        if (!available() || pending || ClientUi.now() < retryAt) return;
        var definition = AwakeningQuest.definition();
        boolean advance = switch (state.stage()) {
            case LOCKED -> reachedLevel(definition);
            case CONFESSION -> world.clears(definition.confession()) > state.confessionBaseline();
            case TOWER -> towerOpen() && world.towerClears() > state.towerBaseline();
            default -> false;
        };
        if (advance) send(null);
    }

    private static void send(LivingEntity elaina) {
        if (!available() || pending) return;
        var ui = ClientUi.current();
        var request = ui.operation("advanceAwakening");
        request.addProperty("stage", state.stage().name());
        request.addProperty("retryStorage", true);
        if (elaina != null) request.addProperty("elaina", elaina.getId());
        pending = true;
        ui.send(AwakeningQuest.ACTION, request);
        TutorialPage.refresh();
    }

    static boolean readyToReturn() { return available() && state.stage() == Stage.ELAINA; }
    static boolean atMaterialStage() { return available() && state.stage() == Stage.MATERIALS; }

    static void finishDialogue(LivingEntity entity) {
        if (readyToReturn() && NpcTutorial.NPC.equals(NpcInteractionComponent.id(entity))) send(entity);
    }

    static void committed(JsonObject request, boolean received) {
        pending = false;
        retryAt = received ? 0 : ClientUi.now() + 1000;
        if (received) {
            var stage = Stage.valueOf(request.get("stage").getAsString());
            String message = switch (stage) {
                case LOCKED -> "觉醒任务已接取。按 I 打开任务追踪器。";
                case MATERIALS -> "祭坛献礼已完成。获得大师水晶 ×1。";
                case CONFESSION -> "千室告解已完成。获得大师水晶 ×1。";
                case TOWER -> "登塔试炼已完成。获得大师水晶 ×1。";
                case ELAINA -> "觉醒成功。获得大师水晶 ×1。";
                case COMPLETE -> "";
            };
            if (!message.isEmpty()) ClientUi.current().message(message);
        }
        TutorialPage.refresh();
    }

    private static void prepare(JsonObject request) {
        if (!available() || !state.stage().name().equals(request.get("stage").getAsString())) {
            throw new IllegalArgumentException("觉醒任务已更新，请重新查看");
        }
        var definition = AwakeningQuest.definition();
        var player = Minecraft.getInstance().player;
        switch (state.stage()) {
            case LOCKED -> {
                if (!reachedLevel(definition)) throw new IllegalArgumentException("达到 50 级后可接取觉醒任务");
            }
            case MATERIALS -> {
                if (!clearsReady(definition)) throw new IllegalArgumentException("祭坛讨伐次数不足");
                BlockPos altar = nearbyAltar();
                if (altar == null) throw new IllegalArgumentException("请靠近魔王祭坛后提交");
                request.addProperty("altar", altar.asLong());
                request.addProperty("dimension", player.level().dimension().identifier().toString());
                request.add("inventoryChanges", materialEdits(definition, materialSources()));
            }
            case CONFESSION -> {
                if (world.clears(definition.confession()) <= state.confessionBaseline()) {
                    throw new IllegalArgumentException("请在材料提交后通关千室告解");
                }
            }
            case TOWER -> {
                if (!towerOpen() || world.towerClears() <= state.towerBaseline()) {
                    throw new IllegalArgumentException("尚未完成登塔试炼");
                }
            }
            case ELAINA -> {
                if (!request.has("elaina") || !(player.level().getEntity(request.get("elaina").getAsInt()) instanceof LivingEntity npc)
                        || !npc.isAlive() || !NpcTutorial.NPC.equals(NpcInteractionComponent.id(npc))
                        || !player.isWithinEntityInteractionRange(npc, 0)) {
                    throw new IllegalArgumentException("请回到出生点与伊蕾娜交谈");
                }
                var progress = growth.get();
                var awakened = CareerProfiles.copy(progress);
                awakened.awakened = true;
                request.add("beforeProgress", JSON.toJsonTree(progress));
                request.add("progress", JSON.toJsonTree(awakened));
            }
            case COMPLETE -> throw new IllegalArgumentException("觉醒任务已经完成");
        }
        request.add("beforeQuest", JSON.toJsonTree(state));
        request.add("beforeWorld", JSON.toJsonTree(world));
        request.add("quest", JSON.toJsonTree(state.advance(world, definition)));
        if (state.stage() != Stage.LOCKED) {
            request.add("reward", InventoryEdits.encode(player, ItemRegistry.item(EquipmentRules.MASTER_CRYSTAL, 1)));
        }
    }

    static BlockPos nearbyAltar() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        BlockPos center = player.blockPosition();
        int radius = (int) Math.ceil(player.blockInteractionRange()) + 1;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (player.isWithinBlockInteractionRange(pos, 0) && player.level().hasChunkAt(pos)
                    && AwakeningQuest.isAltar(player.level().getBlockState(pos))) return pos.immutable();
        }
        return null;
    }

    private static List<MaterialSource> materialSources() {
        var player = Minecraft.getInstance().player;
        var sources = new ArrayList<MaterialSource>();
        if (player == null) return sources;
        for (int slot = 0; slot < 36; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && ItemData.own(stack)) sources.add(new MaterialSource("inventory", slot, stack));
        }
        if (view.has("bag")) {
            for (var raw : view.getAsJsonArray("bag")) {
                var row = raw.getAsJsonObject();
                var stack = InventoryEdits.decode(player, row.get("source"));
                sources.add(new MaterialSource("bag", row.get("slot").getAsInt(), stack));
            }
        }
        return sources;
    }

    private static JsonArray materialEdits(AwakeningQuest.Definition definition, List<MaterialSource> sources) {
        var player = Minecraft.getInstance().player;
        var edits = new JsonArray();
        for (var material : definition.materials()) {
            int remaining = material.count();
            for (var source : sources) {
                if (remaining == 0) break;
                var stack = source.stack();
                if (!material.id().equals(ItemData.id(stack))) continue;
                int used = Math.min(remaining, stack.getCount());
                var remainingStack = used == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - used);
                edits.add(InventoryEdits.change(player, source.area(), source.slot(), stack, remainingStack));
                remaining -= used;
            }
            if (remaining > 0) throw new IllegalArgumentException("还缺少 " + material.name() + " ×" + remaining);
        }
        return edits;
    }

    private static boolean clearsReady(AwakeningQuest.Definition definition) {
        return definition.bosses().stream().allMatch(boss -> world.clears(boss.id()) >= boss.clears());
    }

    static void render(PlayerPageSurface page) {
        if (view == null || state.stage() == Stage.LOCKED) return;
        var definition = AwakeningQuest.definition();
        page.line(definition.badge(), UiTheme.GOLD);
        if (state.stage() == Stage.COMPLETE) {
            page.line("已觉醒 · 已发放大师水晶 4/4", UiTheme.ACCENT);
            page.gap();
            return;
        }
        switch (state.stage()) {
            case MATERIALS -> renderMaterials(page, definition);
            case CONFESSION -> page.line("第二阶段 · 击败 55 级千室告解 1 次", UiTheme.TEXT);
            case TOWER -> {
                page.line(towerOpen() ? "第三阶段 · 通关任意一座觉醒塔" : "第三阶段 · 登塔试炼（待开放）", UiTheme.TEXT);
                if(towerOpen())page.line("在主世界寻找塔门，右键门前石碑进入对应的觉醒塔。",UiTheme.MUTED);
            }
            case ELAINA -> page.line("第四阶段 · 回出生点与伊蕾娜交谈", UiTheme.TEXT);
            default -> {}
        }
        page.line("本阶段奖励 · 大师水晶 ×1", UiTheme.GOLD);
        page.gap();
    }

    private static void renderMaterials(PlayerPageSurface page, AwakeningQuest.Definition definition) {
        page.line("第一阶段 · 祭坛献礼", UiTheme.TEXT);
        var counts = new HashMap<String, Integer>();
        for (var source : materialSources()) counts.merge(ItemData.id(source.stack()), source.stack().getCount(), Integer::sum);
        for (var boss : definition.bosses()) {
            int clears = world.clears(boss.id());
            page.line("Lv." + boss.level() + " " + boss.name() + " · 通关 " + Math.min(clears, boss.clears()) + "/" + boss.clears(),
                    clears >= boss.clears() ? UiTheme.ACCENT : UiTheme.TEXT);
            for (var material : boss.materials()) {
                int count = counts.getOrDefault(material.id(), 0);
                page.line("  " + material.name() + " " + Math.min(count, material.count()) + "/" + material.count(),
                        count >= material.count() ? UiTheme.ACCENT : UiTheme.MUTED);
            }
        }
        boolean ready = clearsReady(definition) && definition.materials().stream()
                .allMatch(material -> counts.getOrDefault(material.id(), 0) >= material.count());
        boolean near = ready && nearbyAltar() != null;
        if (!near) page.line("集齐材料后，靠近魔王祭坛打开任务追踪器提交。", UiTheme.MUTED);
        var button = page.button(pending ? "正在提交…" : "提交觉醒材料", 0, page.cursor(), page.contentWidth() - 24, () -> send(null));
        button.active = near && !pending && available();
        page.cursor(page.cursor() + 25);
    }
}
