package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import java.util.*;

/** Pointer selection delegates to the shared WorldInteractionMenu; private speech bubbles stay owned by NPC presentation. */
public final class NpcClient {
    private record Bubble(int entity, Component text, long until, boolean continuesIntroduction) {}
    private static final Map<UUID,Bubble> bubbles=new LinkedHashMap<>();
    private static final Map<UUID,Integer> lastLine=new HashMap<>();
    private static final Random RANDOM=new Random();
    private static LivingEntity target;
    private static Object world;
    private NpcClient(){}

    public static void registerWorldMenu(){
        WorldInteractionMenu.register("npc", 10, NpcClient::queryMenu);
    }

    public static void clear(){target=null;bubbles.clear();lastLine.clear();world=null;}

    private static LivingEntity pointed(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isAlive()||mc.gui.screen()!=null||!ClientUi.current().ready())return null;
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity entity&&entity.isAlive()
            &&mc.player.isWithinEntityInteractionRange(entity,0)&&mc.player.hasLineOfSight(entity)
            &&NpcInteractionComponent.query(entity)!=null)return entity;
        return null;
    }

    private static WorldInteractionMenu.Target queryMenu(){
        target=pointed();
        if(target==null)return null;
        var entity=target;
        var definition=NpcInteractionComponent.query(entity);
        if(definition==null||definition.actions().isEmpty())return null;
        var options=new ArrayList<WorldInteractionMenu.Option>(definition.actions().size());
        for(var action:definition.actions()){
            String hint=NpcInteractionComponent.clientContinues(entity,action)?"右键继续":WorldInteractionMenu.DEFAULT_HINT;
            options.add(new WorldInteractionMenu.Option(action.id(),action.label(),hint,()->execute(entity,definition,action)));
        }
        return new WorldInteractionMenu.Target(entity.getUUID(),definition.name(),options);
    }

    private static void execute(LivingEntity entity,NpcDefinitions.Definition definition,NpcDefinitions.Action action){
        if(action.type().equals("chat")){
            if(definition.dialogue().isEmpty())return;
            int line=RANDOM.nextInt(definition.dialogue().size());
            var old=lastLine.get(entity.getUUID());
            if(old!=null&&line==old&&definition.dialogue().size()>1)line=(line+1)%definition.dialogue().size();
            lastLine.put(entity.getUUID(),line);
            bubbles.put(entity.getUUID(),new Bubble(entity.getId(),Component.literal(definition.dialogue().get(line)),ClientUi.now()+5000,false));
        }else{
            if(NpcInteractionComponent.clientAction(entity,action))return;
            var request=ClientUi.current().operation("npcAction");
            request.addProperty("entity",entity.getStringUUID());
            request.addProperty("action",action.id());
            ClientUi.current().send("npcAction",request);
        }
    }

    public static void tick(){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}
        target=pointed();
        long now=ClientUi.now();
        bubbles.values().removeIf(b->now>=b.until()||mc.level==null||!(mc.level.getEntity(b.entity()) instanceof LivingEntity e)||!e.isAlive()||NpcInteractionComponent.query(e)==null);
    }

    public static void render(GuiGraphicsExtractor g){
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        target = pointed();
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
            if (entity != target && (!visible || Math.abs(projected.x) > 1 || Math.abs(projected.y) > 1)) continue;

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
