package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ScreenEvent;
public final class Hud {
    private Hud(){
    }
    private static void bar(GuiGraphicsExtractor g,int x,int y,int width,int height,double q,int color){
        g.fill(x,y,x+width,y+height,UiTheme.PANEL);
        g.fill(x+1,y+1,x+1+(int)((width-2)*Math.clamp(q,0,1)),y+height-1,color);
    }
    private static String amount(double value){
        return value<10000?Long.toString((long)Math.ceil(value)):String.format(java.util.Locale.ROOT,value<1000000?"%.1fk":"%.1fm",value/(value<1000000?1000:1000000));
    }
    public static void render(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();
        var s=ClientState.INSTANCE;
        if(!s.ready||mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen() instanceof FightScreen)return;
        var font=UiFonts.font();
        int width=g.guiWidth(),height=g.guiHeight(),center=width/2;
        long now=ClientState.now();
        double health=s.predicted(s.own);
        int extra=(mc.player.isUnderWater()||mc.player.getAirSupply()<mc.player.getMaxAirSupply()?10:0)+(mc.player.isPassenger()?10:0),mpY=height-51-extra;
        bar(g,center-91,height-39,81,10,health,ClientCombat.INSTANCE.invulnerable()?0xFFF0C34F:0xCCBC4859);
        g.text(font,amount(health*s.stats.get(Stat.HEALTH))+" / "+amount(s.stats.get(Stat.HEALTH)),center-88,height-38,UiTheme.TEXT,true);
        bar(g,center+10,mpY,81,10,s.resources.training?1:s.resources.mp/Math.max(1,s.stats.get(Stat.MP)),0xCC416FA7);
        g.text(font,s.resources.training?"MP ∞":"MP "+amount(s.resources.mp)+" / "+amount(s.stats.get(Stat.MP)),center+13,mpY+1,UiTheme.TEXT,true);
        if(s.stats.get(Stat.SHIELD)>0){
            bar(g,center-91,mpY,81,10,s.resources.shield/s.stats.get(Stat.SHIELD),0xCC417F8C);
            g.text(font,"盾 "+amount(s.resources.shield),center-88,mpY+1,UiTheme.TEXT,true);
        }
        if(!(mc.gui.screen() instanceof ChatScreen)){
            int modifier=ClientBootstrap.layer(),group=Math.max(0,modifier),cell=Math.min(21,(width-20)/8),start=center-cell*4,y=mpY-35;
            for(int i=0;i<8;i++){
                int x=start+i*cell;
                g.fill(x,y,x+cell-1,y+32,UiTheme.PANEL);
                String key=ClientBootstrap.SKILLS[i].getTranslatedKeyMessage().getString();
                g.text(font,font.plainSubstrByWidth(key,cell-3),x+2,y+2,UiTheme.MUTED,true);
                String binding=s.progress.slots[group*8+i];
                if(!binding.isEmpty()){
                    Skill skill=Skill.valueOf(binding);
                    g.blit(ClientBootstrap.id("textures/gui/skills/"+skill.icon()+".png"),x+2,y+13,x+10,y+21,0,1,0,1);
                    long remaining=s.resources.cooldowns.getOrDefault(binding,0L)-now;
                    if(remaining>0){
                        g.fill(x+1,y+12,x+cell-2,y+23,0xB0101010);
                        g.text(font,Integer.toString((int)Math.ceil(remaining/1000d)),x+2,y+23,UiTheme.GOLD,true);
                    }
                }
                if(modifier<0)g.fill(x,y,x+cell-1,y+32,0x99101010);
            }
            g.text(font,modifier<0?"多修饰键 · 已禁用":new String[]{
                "普通","Ctrl","Alt"
            }
            [group],start,y-11,UiTheme.MUTED,false);
            int available=center-98;
            if(available>=48){
                int xpWidth=Math.min(160,available-6),xpY=height-29;
                String label="Lv."+s.progress.level+(s.progress.level==80?" MAX":" "+s.progress.experience+"/"+nextExperience(s.progress.level));
                g.fill(6,xpY,6+xpWidth,xpY+18,UiTheme.PANEL);
                g.text(font,font.plainSubstrByWidth(label,xpWidth-8),10,xpY+3,UiTheme.GOLD,false);
                bar(g,9,xpY+13,xpWidth-6,3,s.progress.level==80?1:s.progress.experience/(double)nextExperience(s.progress.level),UiTheme.GOLD);
            }
        }
        if(mc.gui.screen()!=null)return;
        g.nextStratum();
        var camera=mc.gameRenderer.mainCamera();
        var forward=camera.forwardVector();
        for(var number:s.numbers){
            if(!number.dimension().equals(s.dimension))continue;
            var relative=number.position().subtract(camera.position());
            if(relative.x*forward.x()+relative.y*forward.y()+relative.z*forward.z()<=0)continue;
            var screen=mc.gameRenderer.projectPointToScreen(number.position());
            if(!Double.isFinite(screen.x)||!Double.isFinite(screen.y)||Math.abs(screen.x)>1||Math.abs(screen.y)>1)continue;
            double age=Math.clamp(1-(number.expires()-now)/1100d,0,1);
            int x=(int)Math.round((screen.x+1)*.5*width+number.lane()*(6+age*10)),y=(int)Math.round((1-screen.y)*.5*height-age*24),alpha=(int)(255*Math.min(1,(1-age)*4));
            var text=net.minecraft.network.chat.Component.literal(FightScreen.number(number.amount()));
            g.pose().pushMatrix();
            g.pose().translate(x,y);
            g.pose().scale(ClientSettings.damageScale,ClientSettings.damageScale);
            g.text(font,text,-font.width(text)/2,0,(alpha<<24)|number.color(),true);
            g.pose().popMatrix();
        }
        var cast=ClientCombat.INSTANCE.cast;
        if(cast!=null&&(cast.skill==Skill.TRIPLE||cast.skill==Skill.DRAGON)){
            int remaining=(int)cast.parameters.get("slashes",3)-cast.segment;
            if(remaining>0){
                int x=(int)(width*.8),y=(int)(height*.2);
                g.blit(ClientBootstrap.id("textures/gui/skills/"+cast.skill.icon()+".png"),x-16,y-16,x+16,y+16,0,1,0,1);
                if(now-cast.stageStart<(cast.skill==Skill.TRIPLE?200:250))g.fill(x-16,y-16,x+16,y+16,0x70000000);
                g.nextStratum();
                g.fill(x-9,y-11,x+9,y+11,0xB0101010);
                g.nextStratum();
                g.pose().pushMatrix();
                g.pose().translate(x,y-9);
                g.pose().scale(2,2);
                String text=Integer.toString(remaining);
                g.text(font,text,-font.width(text)/2,0,UiTheme.GOLD,true);
                g.pose().popMatrix();
            }
        }
        if(mc.hitResult instanceof EntityHitResult hit){
            var entity=hit.getEntity();
            if(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part)entity=part.parentMob;
            if(entity instanceof LivingEntity living&&!(entity instanceof net.minecraft.world.entity.player.Player)){
                Target target=s.context(living);
                if(target!=null)target(g,living,target);
            }
        }
    }
    private static void target(GuiGraphicsExtractor g,LivingEntity e,Target t){
        var s=ClientState.INSTANCE;
        int level=t.level(s.progress.level,s.dungeon==null?s.progress.sync:s.dungeon.sync);
        double maximum=t.health(level),q=s.predicted(t.life),scale=ClientSettings.targetScale;
        if(184*scale>g.guiWidth()-12)scale=1;
        int width=(int)(184*scale),x=ClientSettings.targetPosition.equals("top_left")?6:(g.guiWidth()-width)/2;
        g.pose().pushMatrix();
        g.pose().translate(x,6);
        g.pose().scale((float)scale,(float)scale);
        g.fill(0,0,184,42,0xff101010);
        g.text(UiFonts.font(),e.getName().getString()+" Lv."+level,5,4,t.elite?0xffffce7d:0xffecf1f5);
        g.text(UiFonts.font(),t.difficulty.title()+" · "+(t.kind.equals("BOSS")?"Boss":t.elite?"精英":"普通"),5,16,0xffaab8ce);
        bar(g,5,29,174,9,q,t.kind.equals("BOSS")?0xffdfb545:0xffbe485a);
        g.text(UiFonts.font(),FightScreen.number(q*maximum)+" / "+FightScreen.number(maximum),8,29,0xffffffff);
        g.pose().popMatrix();
    }
    public static void equipment(ScreenEvent.Render.Background event){
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        for(var slot:screen.getMenu().slots){
            var rarity=GameItems.rarity(slot.getItem());
            if(slot.isActive()&&rarity!=Rarity.COMMON){
                int x=screen.getLeftPos()+slot.x,y=screen.getTopPos()+slot.y,color=0xff000000|rarity.color();
                var g=event.getGuiGraphics();
                g.fill(x-1,y-1,x+17,y,color);
                g.fill(x-1,y+16,x+17,y+17,color);
                g.fill(x-1,y,x,y+16,color);
                g.fill(x+16,y,x+17,y+16,color);
            }
        }
    }
}
