package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.character.api.CareerProfiles;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.npc.api.PromotionQuest;
import cn.yumis.fight.modules.npc.api.PromotionQuest.WorldProgress;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.Supplier;

/** The owning client checks the promotion goals and submits the altar unlock. */
final class PromotionQuestClient {
    private static final Gson JSON = new Gson();
    private static Supplier<Progress> growth;
    private static JsonObject view;
    private static WorldProgress world = WorldProgress.EMPTY;
    private static boolean pending;

    private PromotionQuestClient() {}

    static void install(CommandRouter<Void, JsonObject> preparations, Supplier<Progress> progress) {
        growth = progress;
        preparations.register("npc", PromotionQuest.ACTION, (ignored, request) -> prepare(request));
    }

    static void receive(JsonObject profile) {
        var incoming = profile.getAsJsonObject("promotionQuest");
        if (Objects.equals(view, incoming)) return;
        view = incoming;
        world = view == null ? WorldProgress.EMPTY : JSON.fromJson(view.get("world"), WorldProgress.class);
        TutorialPage.refresh();
    }

    static void clear() {
        view = null;
        world = WorldProgress.EMPTY;
        pending = false;
    }

    private static boolean available() { return view != null && ClientUi.current().ready(); }

    static void submit() {
        if (!available() || pending) return;
        var ui = ClientUi.current();
        var request = ui.operation("unlock");
        request.addProperty("retryStorage", true);
        pending = true;
        ui.send(PromotionQuest.ACTION, request);
        TutorialPage.refresh();
    }

    private static void prepare(JsonObject request) {
        if (!available()) throw new IllegalArgumentException("转职试炼尚未就绪");
        var player = Minecraft.getInstance().player;
        var progress = growth.get();
        if (progress.jobChangeUnlocked()) throw new IllegalArgumentException("已经拥有通用转职资格");
        if (progress.level < PromotionQuest.LEVEL) throw new IllegalArgumentException("转职试炼需要20级");
        if (!world.complete()) throw new IllegalArgumentException("王者级三大副本尚未全部通关");
        var altar = AwakeningQuestClient.nearbyAltar();
        if (altar == null) throw new IllegalArgumentException("请靠近魔王祭坛后提交");
        var next = CareerProfiles.copy(progress);
        next.unlockJobChange();
        request.addProperty("altar", altar.asLong());
        request.addProperty("dimension", player.level().dimension().identifier().toString());
        request.add("beforeProgress", JSON.toJsonTree(progress));
        request.add("progress", JSON.toJsonTree(next));
        request.add("beforeWorld", JSON.toJsonTree(world));
    }

    static void committed(JsonObject request, boolean received) {
        pending = false;
        if (received) ClientUi.current().message("转职试炼完成！通用转职资格已解锁，打开 I → 职业技能选择进阶职业。");
        TutorialPage.refresh();
    }

    static void render(PlayerPageSurface page) {
        if (view == null) return;
        var progress = growth.get();
        boolean unlocked = progress.jobChangeUnlocked();
        if (!unlocked && progress.level < PromotionQuest.LEVEL && world.clears().isEmpty()) return;
        if (!TutorialPage.questHeader(page, PromotionQuest.ACTION, PromotionQuest.BADGE.plain(), TutorialPage.QuestType.CAREER)) return;
        if (unlocked) {
            page.line("转职试炼已完成 · 通用转职资格已解锁", UiTheme.ACCENT);
            page.gap();
            return;
        }
        for (String dungeon : PromotionQuest.DUNGEONS) {
            int clears = world.clears(dungeon);
            page.line("王者级 " + DungeonCatalog.current().dungeon(dungeon).name() + " · 通关 " + Math.min(clears, 1) + "/1",
                    clears >= 1 ? UiTheme.ACCENT : UiTheme.TEXT);
        }
        if (progress.level < PromotionQuest.LEVEL) {
            page.line("20级后可触碰魔王祭坛完成试炼。", UiTheme.MUTED);
            page.gap();
            return;
        }
        if (!world.complete()) {
            page.line("完成三处通关后，靠近魔王祭坛打开任务追踪器提交。", UiTheme.MUTED);
            page.gap();
            return;
        }
        boolean near = AwakeningQuestClient.nearbyAltar() != null;
        if (!near) page.line("三处通关已完成，靠近任意魔王祭坛提交。", UiTheme.MUTED);
        var button = page.button(pending ? "正在提交…" : "触碰魔王祭坛解锁转职", 0, page.cursor(), page.contentWidth() - 24, PromotionQuestClient::submit);
        button.active = near && !pending && available();
        page.cursor(page.cursor() + 25);
        page.gap();
    }
}
