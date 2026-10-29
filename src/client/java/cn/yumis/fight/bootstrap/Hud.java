package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.economy.api.ItemRarity;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.platform.ItemData;
import static cn.yumis.fight.modules.character.api.CharacterRules.nextExperience;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.dungeons.api.Target;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ScreenEvent;
public final class Hud {
    // Unmodified RarityCore white texture, tinted with this project's rarity colors.
    // Source and asset license: resources/licenses/raritycore/NOTICE.txt.
    private static final Identifier RARITY_BACKGROUND=Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/rarity/background.png");
    private Hud(){
    }
    private static void bar(GuiGraphicsExtractor g,int x,int y,int width,int height,double q,int color){
        g.fill(x,y,x+width,y+height,UiTheme.HUD_PANEL);
        g.fill(x+1,y+1,x+1+(int)((width-2)*Math.clamp(q,0,1)),y+height-1,color);
    }
    private static String amount(double value){
        return value<10000?Long.toString((long)Math.ceil(value)):String.format(java.util.Locale.ROOT,value<1000000?"%.1fk":"%.1fm",value/(value<1000000?1000:1000000));
    }
    public static void render(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();
        UiTheme.check();
        var s=ClientSession.INSTANCE;
        if(!s.ready||mc.player==null||mc.gui.hud.isHidden())return;
        if(mc.gui.screen() instanceof FightScreen)return;
        gold(g,s);
        var font=UiFonts.font();
        int width=g.guiWidth(),height=g.guiHeight(),center=width/2;
        damageMeter(g,s);
        cn.yumis.fight.modules.combat.client.EquipmentBuffHud.render(g,s.resources,ClientSession.now(),width-6,height-70);
        long now=ClientSession.now();
        double health=s.predicted(s.own);
        var currentStats=SkillController.INSTANCE.snapshot();
        int extra=(mc.player.isUnderWater()||mc.player.getAirSupply()<mc.player.getMaxAirSupply()?10:0)+(mc.player.isPassenger()?10:0),mpY=height-51-extra;
        bar(g,center-91,height-39,81,10,health,SkillController.INSTANCE.invulnerable()?0xFFF0C34F:0xCCBC4859);
        g.text(font,amount(health*currentStats.get(Stat.HEALTH))+" / "+amount(currentStats.get(Stat.HEALTH)),center-88,height-38,UiTheme.HUD_TEXT,true);
        bar(g,center+10,mpY,81,10,s.resources.training?1:s.resources.mp/Math.max(1,currentStats.get(Stat.MP)),0xCC416FA7);
        g.text(font,s.resources.training?"MP ∞":"MP "+amount(s.resources.mp)+" / "+amount(currentStats.get(Stat.MP)),center+13,mpY+1,UiTheme.HUD_TEXT,true);
        if(currentStats.get(Stat.SHIELD)>0){
            bar(g,center-91,mpY,81,10,s.resources.shield/currentStats.get(Stat.SHIELD),0xCC417F8C);
            g.text(font,"盾 "+amount(s.resources.shield),center-88,mpY+1,UiTheme.HUD_TEXT,true);
        }
        if(!(mc.gui.screen() instanceof ChatScreen)){
            int modifier=ClientBootstrap.layer(),group=Math.max(0,modifier),cell=Math.min(21,(width-20)/8),start=center-cell*4,y=mpY-35;
            cn.yumis.fight.modules.skills.client.CareerHud.render(g,center,y-28);
            for(int i=0;i<8;i++){
                int x=start+i*cell;
                g.fill(x,y,x+cell-1,y+32,UiTheme.HUD_PANEL);
                String key=cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].getTranslatedKeyMessage().getString();
                g.text(font,font.plainSubstrByWidth(key,cell-3),x+2,y+2,UiTheme.HUD_MUTED,true);
                String binding=s.progress.slots[group*8+i];
                if(!binding.isEmpty()&&SkillCatalog.contains(binding)){
                    SkillDefinition skill=SkillCatalog.require(binding);
                    int icon=cell-3,iconX=x+1,iconY=y+12;
                    g.blit(ClientBootstrap.id("textures/gui/skills/"+skill.icon()+".png"),iconX,iconY,iconX+icon,iconY+icon,0,1,0,1);
                    long remaining=s.resources.cooldowns.getOrDefault(binding,0L)-now;
                    if(remaining>0){
                        g.fill(iconX,iconY,iconX+icon,iconY+icon,0x90101010);
                        String cooldown=Integer.toString((int)Math.ceil(remaining/1000d));g.text(font,cooldown,iconX+(icon-font.width(cooldown))/2,iconY+(icon-8)/2,UiTheme.HUD_GOLD,true);
                    }
                }
                if(modifier<0)g.fill(x,y,x+cell-1,y+32,0x99101010);
            }
            g.text(font,modifier<0?"多修饰键 · 已禁用":new String[]{
                "普通","Ctrl","Alt"
            }
            [group],start,y-11,UiTheme.HUD_MUTED,false);
            int available=center-98;
            if(available>=48){
                int xpWidth=Math.min(160,available-6),xpY=height-29;
                String label="Lv."+s.progress.level+(s.progress.level==80?" MAX":" "+s.progress.experience+"/"+nextExperience(s.progress.level));
                g.fill(6,xpY,6+xpWidth,xpY+18,UiTheme.HUD_PANEL);
                g.text(font,font.plainSubstrByWidth(label,xpWidth-8),10,xpY+3,UiTheme.HUD_GOLD,false);
                bar(g,9,xpY+13,xpWidth-6,3,s.progress.level==80?1:s.progress.experience/(double)nextExperience(s.progress.level),UiTheme.HUD_GOLD);
            }
        }
        if(mc.gui.screen()!=null)return;
        g.nextStratum();
        var numbers=new java.util.ArrayList<>(s.prediction.numbers());
        numbers.addAll(cn.yumis.fight.modules.skills.api.client.TrainingCombat.numbers());
        cn.yumis.fight.modules.combat.client.DamageNumbers.render(g,mc,now,s.dimension,numbers);
        cn.yumis.fight.modules.skills.client.CareerHud.renderGauge(g,width,height,now);
        if(mc.hitResult instanceof EntityHitResult hit){
            var entity=hit.getEntity();
            if(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part)entity=part.parentMob;
            if(entity instanceof LivingEntity living&&!(entity instanceof net.minecraft.world.entity.player.Player)){
                Target target=s.context(living);
                if(target!=null)target(g,living,target);
            }
        }
    }
    private static void damageMeter(GuiGraphicsExtractor g,ClientSession state){
        if(state.dungeon==null||!state.damageMeter.has("rows")||!state.damageMeter.has("instance")||!state.damageMeter.get("instance").getAsString().equals(state.dungeon.id.toString()))return;
        var rows=state.damageMeter.getAsJsonArray("rows");var font=UiFonts.font();int x=6,w=Math.min(250,Math.max(120,g.guiWidth()/2-12)),y=ClientSettings.targetPosition.equals("top_left")?(int)(65*ClientSettings.targetScale)+16:50;
        g.text(font,state.damageMeter.get("completed").getAsBoolean()?"本次副本输出 · 已结算":"本次副本输出",x+3,y,UiTheme.HUD_GOLD,false);y+=13;
        for(var value:rows){
            var row=value.getAsJsonObject();String damage=damageAmount(row.get("damage").getAsDouble()),dps=damageAmount(row.get("dps").getAsDouble())+"/s",share=String.format(java.util.Locale.ROOT,"%.1f%%",row.get("share").getAsDouble()*100);boolean narrow=font.width(damage+"  "+dps)>w-8;int height=narrow?41:29;
            if(y+height>g.guiHeight()-100)break;g.fill(x,y,x+w,y+height,UiTheme.HUD_PANEL);g.fill(x,y,x+(int)(w*row.get("bar").getAsDouble()),y+height,UiTheme.DAMAGE_BAR);
            g.text(font,font.plainSubstrByWidth(row.get("name").getAsString(),Math.max(12,w-font.width(share)-14)),x+3,y+3,UiTheme.HUD_TEXT,true);g.text(font,share,x+w-font.width(share)-3,y+3,UiTheme.HUD_TEXT,true);
            g.text(font,damage,x+3,y+16,UiTheme.HUD_TEXT,true);g.text(font,dps,narrow?x+3:x+w-font.width(dps)-3,y+(narrow?28:16),UiTheme.HUD_TEXT,true);y+=height+2;
        }
    }
    public static void gold(GuiGraphicsExtractor g,ClientSession state){
        var font=UiFonts.font();String text="金币 "+String.format(java.util.Locale.ROOT,"%,d",state.gold.balance);
        int w=font.width(text)+12,x=g.guiWidth()-w-6,y=g.guiHeight()-23;
        if(x<g.guiWidth()/2+95)y-=35;
        g.fill(x,y,x+w,y+17,UiTheme.HUD_PANEL);g.text(font,text,x+6,y+4,UiTheme.HUD_GOLD,true);
        long now=ClientSession.now();int lane=0;
        for(var gain:state.gold.gains(now)){
            double t=Math.clamp((now-gain.started())/450d,0,1),ease=t*t;String label="+"+gain.amount();
            int alpha=(int)(255*Math.min(1,(1-t)*5));int fy=y-26-(lane++%3)*10+(int)(ease*25);
            g.text(font,label,x+w-font.width(label)-6,fy,(alpha<<24)|(UiTheme.HUD_GOLD&0xffffff),true);
            if(t>.75)g.fill(x,y,x+w,y+1,((int)(150*(1-t)*4)<<24)|(UiTheme.HUD_GOLD&0xffffff));
        }
    }
    private static String damageAmount(double value){return cn.yumis.fight.modules.presentation.api.client.NumberText.damage(value);}
    private static void target(GuiGraphicsExtractor g,LivingEntity e,Target t){
        var s=ClientSession.INSTANCE;
        int level=t.level(s.progress.level,s.encounterSync());
        double maximum=t.health(level),q=s.predicted(t.life),scale=ClientSettings.targetScale;
        if(184*scale>g.guiWidth()-12)scale=1;
        int width=(int)(184*scale),x=ClientSettings.targetPosition.equals("top_left")?6:(g.guiWidth()-width)/2;
        g.pose().pushMatrix();
        g.pose().translate(x,6);
        g.pose().scale((float)scale,(float)scale);
        g.fill(0,0,184,42,0xff101010);
        g.text(UiFonts.font(),e.getName().getString()+" Lv."+level,5,4,t.elite?0xffffce7d:0xffecf1f5);
        g.text(UiFonts.font(),t.difficulty.title()+" · "+(t.kind.equals("BOSS")?"Boss":t.elite?"精英":"普通")+(t.elitePower.isEmpty()?"":" · "+switch(t.elitePower){case "strong"->"强壮";case "rage"->"狂暴";case "armor"->"坚韧";case "elemental"->"元素守护";default->"";}),5,16,0xffaab8ce);
        bar(g,5,29,174,9,q,t.kind.equals("BOSS")?0xffdfb545:0xffbe485a);
        g.text(UiFonts.font(),cn.yumis.fight.modules.presentation.api.client.NumberText.number(q*maximum)+" / "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(maximum),8,29,0xffffffff);
        g.pose().popMatrix();
    }
    public static void equipment(ScreenEvent.Render.Background event){
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        for(var slot:screen.getMenu().slots){
            if(!slot.isActive())continue;
            int x=screen.getLeftPos()+slot.x,y=screen.getTopPos()+slot.y;
            if(x+16<=0||y+16<=0||x>=screen.width||y>=screen.height)continue;
            var stack=slot.getItem();
            if(stack.isEmpty()||!ItemData.own(stack))continue;
            var rarity=ItemRarity.rarity(stack);
            // COMMON is also the fallback for unrelated items; only decorate known gear/currency.
            if(rarity==Rarity.COMMON&&EquipmentItems.view(stack)==null&&CurrencyRules.find(ItemData.id(stack))==null)continue;
            int color=0xff000000|rarity.color();
            var g=event.getGuiGraphics();
            g.blit(RenderPipelines.GUI_TEXTURED,RARITY_BACKGROUND,x,y,0.0F,0.0F,16,16,16,16,color);
            // Former [x-1,x+17) / [y-1,y+17) frame moves inward one pixel on every side.
            g.fill(x,y,x+16,y+1,color);
            g.fill(x,y+15,x+16,y+16,color);
            g.fill(x,y+1,x+1,y+15,color);
            g.fill(x+15,y+1,x+16,y+15,color);
        }
    }
}
