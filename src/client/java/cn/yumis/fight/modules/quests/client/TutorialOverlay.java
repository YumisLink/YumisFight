package cn.yumis.fight.modules.quests.client;

import cn.yumis.fight.modules.npc.api.NpcDefinitions;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.quests.api.NpcTutorial;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.RichTextDrawing;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** The guide's introduction plays as overhead bubbles; the quest owns its own presentation state. */
public final class TutorialOverlay {
    private record Bubble(int entity, Component text, long until, boolean continuesIntroduction) {}
    private static final Map<UUID,Bubble> bubbles=new LinkedHashMap<>();
    private static boolean useHeld;
    private static Object world;
    private TutorialOverlay(){}

    /** Registered as the tutorial NPC action's client handler; returns true so the shared HUD stays silent. */
    static boolean interact(LivingEntity speaker,NpcDefinitions.Action action){
        if (useHeld) return true;
        useHeld = true;

        boolean awakening = NpcTutorial.NPC.equals(NpcInteractionComponent.id(speaker))
                && AwakeningQuestClient.readyToReturn();
        UUID id = speaker.getUUID();
        if (awakening || TutorialClient.state().started) {
            bubbles.remove(id);
            Minecraft.getInstance().gui.setScreen(new NpcDialogueScreen(speaker, awakening));
            return true;
        }
        if (TutorialClient.starting()) return true;

        String key = TutorialClient.dialogueKey();
        var pages = TutorialClient.dialogue();
        long now = ClientUi.now();
        int page = Math.clamp(TutorialClient.savedPage(key), 0, pages.size() - 1);
        var previous = bubbles.get(id);
        if (previous != null && previous.entity() == speaker.getId()
                && previous.until() > now && previous.continuesIntroduction()) {
            page = Math.min(page + 1, pages.size() - 1);
        }
        boolean more = page + 1 < pages.size();
        bubbles.put(id, new Bubble(speaker.getId(), pages.get(page).component(), now + 10_000, more));
        TutorialClient.savePage(key, page);
        if (!more) TutorialClient.start();
        return true;
    }

    /** The shared action panel asks the quest whether a held conversation wants the continue hint. */
    static boolean continues(LivingEntity target){
        var bubble=bubbles.get(target.getUUID());
        return bubble!=null&&bubble.continuesIntroduction()&&bubble.until()>ClientUi.now();
    }

    public static void tick(){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}
        if(!mc.options.keyUse.isDown())useHeld=false;
        long now=ClientUi.now();
        bubbles.values().removeIf(b->now>=b.until()||mc.level==null||!(mc.level.getEntity(b.entity()) instanceof LivingEntity e)||!e.isAlive()||NpcInteractionComponent.query(e)==null);
    }

    static void clear(){bubbles.clear();useHeld=false;world=null;}

    public static void render(GuiGraphicsExtractor g){
        var mc=Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        var font = UiFonts.font();
        var camera = mc.gameRenderer.mainCamera();
        var forward = camera.forwardVector();
        for (var bubble : bubbles.values()) {
            var entity = mc.level.getEntity(bubble.entity());
            if (entity == null) continue;
            var point = entity.position().add(0, entity.getBbHeight() + .55, 0);
            var relative = point.subtract(camera.position());
            boolean inFront = relative.x * forward.x() + relative.y * forward.y() + relative.z * forward.z() > 0;
            var projected = inFront ? mc.gameRenderer.projectPointToScreen(point) : null;
            boolean visible = projected != null && Double.isFinite(projected.x) && Double.isFinite(projected.y);
            boolean pointed = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity().getId() == bubble.entity();
            if (!pointed && (!visible || Math.abs(projected.x) > 1 || Math.abs(projected.y) > 1)) continue;

            var lines = font.split(bubble.text(), Math.max(1, Math.min(220, g.guiWidth() - 24)));
            int w = lines.stream().mapToInt(font::width).max().orElse(1) + 16;
            int h = lines.size() * 12 + 12;
            double px = visible ? Math.clamp(projected.x, -1, 1) : 0;
            double py = visible ? Math.clamp(projected.y, -1, 1) : 1;
            int x = Math.clamp((int) ((px + 1) * .5 * g.guiWidth()) - w / 2, 4, Math.max(4, g.guiWidth() - w - 4));
            int y = Math.clamp((int) ((1 - py) * .5 * g.guiHeight()) - h, 4, Math.max(4, g.guiHeight() - h - 4));
            UiTheme.hud(g, x, y, w, h);
            for (int i = 0; i < lines.size(); i++) {
                RichTextDrawing.line(g, font, lines.get(i), x + 8, y + 6 + i * 12, UiTheme.HUD_TEXT);
            }
            g.fill(x + w / 2 - 2, y + h, x + w / 2 + 3, y + h + 3, UiTheme.HUD_PANEL);
        }
    }
}
