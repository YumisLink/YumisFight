package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Pointer selection and private speech are presentation state, never shared NPC dialogue state. */
public final class NpcClient {
    private record Bubble(int entity,String text,long until) {}
    private static final Map<UUID,Bubble> bubbles=new LinkedHashMap<>();
    private static final Map<UUID,Integer> lastLine=new HashMap<>();
    private static final Random RANDOM=new Random();
    private static LivingEntity target;
    private static int selected;
    private static Object world;
    private static BooleanSupplier blocked=()->false;
    private NpcClient(){}
    public static void blocked(BooleanSupplier value){blocked=value;}
    public static void clear(){target=null;selected=0;bubbles.clear();lastLine.clear();world=null;}
    private static LivingEntity pointed(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isAlive()||mc.gui.screen()!=null||!ClientUi.current().ready()||blocked.getAsBoolean())return null;
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity entity&&entity.isAlive()&&mc.player.isWithinEntityInteractionRange(entity,0)&&mc.player.hasLineOfSight(entity)&&NpcInteractionComponent.query(entity)!=null)return entity;
        return null;
    }
    private static void updateTarget(){var next=pointed();if(next!=target){target=next;selected=0;}if(target!=null)selected=Math.clamp(selected,0,NpcInteractionComponent.query(target).actions().size()-1);}
    public static void tick(){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}updateTarget();long now=ClientUi.now();
        bubbles.values().removeIf(b->now>=b.until()||mc.level==null||!(mc.level.getEntity(b.entity()) instanceof LivingEntity e)||!e.isAlive()||NpcInteractionComponent.query(e)==null);
    }
    public static void scroll(InputEvent.MouseScrollingEvent event){
        updateTarget();if(target==null)return;event.setCanceled(true);int step=Integer.signum(event.getAccumulatedScrollY());if(step==0)return;
        int count=NpcInteractionComponent.query(target).actions().size();selected=Math.floorMod(selected-step,count);
    }
    public static void interact(InputEvent.InteractionKeyMappingTriggered event){
        if(!event.isUseItem())return;updateTarget();if(target==null)return;event.setCanceled(true);event.setSwingHand(false);if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var definition=NpcInteractionComponent.query(target);var action=definition.actions().get(selected);
        if(action.type().equals("chat")){
            if(definition.dialogue().isEmpty())return;int line=RANDOM.nextInt(definition.dialogue().size());var old=lastLine.get(target.getUUID());if(old!=null&&line==old&&definition.dialogue().size()>1)line=(line+1)%definition.dialogue().size();lastLine.put(target.getUUID(),line);bubbles.put(target.getUUID(),new Bubble(target.getId(),definition.dialogue().get(line),ClientUi.now()+5000));
        }else{
            var request=ClientUi.current().operation("npcAction");request.addProperty("entity",target.getStringUUID());request.addProperty("action",action.id());ClientUi.current().send("npcAction",request);
        }
    }
    public static void render(GuiGraphicsExtractor g){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gui.hud.isHidden())return;var font=UiFonts.font();
        for(var bubble:bubbles.values()){
            var entity=mc.level.getEntity(bubble.entity());if(entity==null)continue;var point=entity.position().add(0,entity.getBbHeight()+.55,0);var camera=mc.gameRenderer.mainCamera();var relative=point.subtract(camera.position());var forward=camera.forwardVector();
            if(relative.x*forward.x()+relative.y*forward.y()+relative.z*forward.z()<=0)continue;var projected=mc.gameRenderer.projectPointToScreen(point);if(!Double.isFinite(projected.x)||!Double.isFinite(projected.y)||Math.abs(projected.x)>1||Math.abs(projected.y)>1)continue;
            var lines=font.split(Component.literal(bubble.text()),Math.min(220,g.guiWidth()-24));int w=lines.stream().mapToInt(font::width).max().orElse(1)+16,h=lines.size()*12+12;
            int x=Math.clamp((int)((projected.x+1)*.5*g.guiWidth())-w/2,4,Math.max(4,g.guiWidth()-w-4)),y=Math.max(4,(int)((1-projected.y)*.5*g.guiHeight())-h);
            UiTheme.hud(g,x,y,w,h);for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),x+8,y+6+i*12,UiTheme.HUD_TEXT,false);
            g.fill(x+w/2-2,y+h,x+w/2+3,y+h+3,UiTheme.HUD_PANEL);
        }
        updateTarget();if(target==null)return;var d=NpcInteractionComponent.query(target);int w=Math.min(174,g.guiWidth()-16),h=44+d.actions().size()*18,x=g.guiWidth()-w-8,y=Math.max(8,(g.guiHeight()-h)/2);
        UiTheme.hud(g,x,y,w,h);g.text(font,font.plainSubstrByWidth(d.name(),w-16),x+8,y+8,UiTheme.HUD_GOLD,false);
        for(int i=0;i<d.actions().size();i++){int row=y+25+i*18;if(i==selected)g.fill(x+4,row-2,x+w-4,row+14,UiTheme.SELECTED);g.text(font,(i==selected?"› ":"  ")+d.actions().get(i).label(),x+10,row,i==selected?UiTheme.HUD_GOLD:UiTheme.HUD_TEXT,false);}
        g.text(font,"滚轮选择 · 右键确认",x+8,y+h-13,UiTheme.HUD_MUTED,false);
    }
}
