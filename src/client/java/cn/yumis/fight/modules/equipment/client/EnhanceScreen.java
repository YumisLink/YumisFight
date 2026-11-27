package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.client.AttributeLabels;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** 凯莉的强化炉：上方炉子、下方背包；拖入装备后本机预演并确认强化或增幅。 */
public final class EnhanceScreen extends ThemedContainerScreen<EnhanceMenu> {
    private Gear gear;
    private int target;
    private String awaiting="";
    private String notice="";
    private int noticeColor;
    private long noticeUntil;
    private long burstUntil;
    private Button minus,plus,confirm;

    public EnhanceScreen(EnhanceMenu menu,Inventory inventory,Component title){super(menu,inventory,title,176,282);inventoryLabelY=188;}

    @Override protected void init(){
        super.init();
        minus=addRenderableWidget(Button.builder(Component.literal("−"),b->shiftTarget(-1)).bounds(leftPos+8,topPos+164,20,20).build(ThemedButton::new));
        plus=addRenderableWidget(Button.builder(Component.literal("+"),b->shiftTarget(1)).bounds(leftPos+32,topPos+164,20,20).build(ThemedButton::new));
        confirm=addRenderableWidget(Button.builder(Component.literal("强化"),b->submit()).bounds(leftPos+96,topPos+164,72,20).build(ThemedButton::new));
        refreshControls();
    }

    private boolean amplified(){return gear!=null&&EnhancementRules.contaminated(gear);}
    private String verb(){return amplified()?"增幅":"强化";}
    private int current(){return gear==null?0:EnhancementRules.level(gear);}

    private void shiftTarget(int delta){
        if(gear==null)return;
        target=Math.clamp(target+delta,Math.min(EnhancementRules.MAXIMUM,current()+1),EnhancementRules.MAXIMUM);
    }

    @Override protected void containerTick(){
        super.containerTick();
        var stack=menu.slots.get(0).getItem();
        Gear next=stack.isEmpty()?null:EquipmentItems.gear(stack);
        if(next==null){
            gear=null;
        }else if(gear==null||!next.instance.equals(gear.instance)||next.revision!=gear.revision){
            gear=next;target=Math.min(EnhancementRules.MAXIMUM,EnhancementRules.level(gear)+1);
        }
        if(!awaiting.isEmpty()){
            var report=EnhanceClient.take(awaiting);
            if(report!=null){
                awaiting="";
                if(!report.rejected())show(report.outcome());
            }
        }
        refreshControls();
    }

    private void show(EquipmentChanges.EnhanceOutcome outcome){
        if(outcome==null)return;
        burstUntil=ClientUi.now()+2400;
        String verb=outcome.amplified()?"增幅":"强化";
        if(outcome.shattered()){
            notice=verb+"失败：装备在烈焰中粉碎！";noticeColor=0xff5c7a;
        }else if(outcome.toLevel()>outcome.fromLevel()){
            notice=verb+"成功：+"+outcome.fromLevel()+" → +"+outcome.toLevel()+(outcome.failures()>0?"（途中失败"+outcome.failures()+"次）":"");
            noticeColor=outcome.amplified()?EnhancementRules.AMPLIFY_PINK:UiTheme.SUCCESS;
        }else if(outcome.toLevel()<outcome.fromLevel()){
            notice=verb+"失败：+"+outcome.fromLevel()+" → +"+outcome.toLevel();noticeColor=UiTheme.ERROR;
        }else{
            notice=verb+"未能突破，等级保持 +"+outcome.toLevel();noticeColor=UiTheme.ERROR;
        }
        noticeUntil=ClientUi.now()+4000;
    }

    private long gold(){var account=EquipmentActions.accountView();return account.has("gold")?account.get("gold").getAsLong():0L;}
    private long fuel(boolean amplified){
        var player=Minecraft.getInstance().player;
        if(player==null)return 0;
        return EquipmentChanges.fuelCount(player,BagSnapshotClient.items(menu),amplified);
    }
    private String fuelName(boolean amplified){return amplified?"强烈的气息":"煤/木炭";}

    private void refreshControls(){
        boolean busy=!awaiting.isEmpty();
        boolean ready=gear!=null&&!busy&&!maxed();
        if(minus!=null)minus.active=ready&&target>current()+1;
        if(plus!=null)plus.active=ready&&target<EnhancementRules.MAXIMUM;
        if(confirm!=null){
            boolean amplified=amplified();
            confirm.active=ready&&gold()>=EnhancementRules.goldCost(gear,current()+1,amplified)&&fuel(amplified)>=EnhancementRules.fuel(current()+1);
            String label=maxed()?"已达 +"+EnhancementRules.MAXIMUM:verb()+(target==current()+1?" +"+target:"到 +"+target);
            if(!confirm.getMessage().getString().equals(label))confirm.setMessage(Component.literal(label));
        }
    }
    private boolean maxed(){return current()>=EnhancementRules.MAXIMUM;}

    private void submit(){
        if(gear==null||!awaiting.isEmpty())return;
        var j=ClientUi.current().operation("enhance");
        j.addProperty("menu",menu.containerId);
        j.addProperty("slot",0);
        j.addProperty("equipment",gear.instance.toString());
        j.addProperty("revision",gear.revision);
        j.addProperty("target",target);
        awaiting=j.get("operation").getAsString();
        refreshControls();
        ClientUi.current().send("enhance",j);
    }

    @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        for(var slot:menu.slots)UiTheme.slot(g,leftPos+slot.x-1,topPos+slot.y-1);
        // 炉体：石框、炉膛与炉口
        int fx=leftPos+34,fy=topPos+14;
        UiTheme.panel(g,fx,fy,108,90);
        g.fill(fx+8,fy+8,fx+100,fy+74,0xff2b2f36);
        g.fill(fx+12,fy+12,fx+96,fy+70,0xff171a1f);
        g.fill(fx+70,fy+6,fx+102,fy+10,0xff4a4f58); // 烟囱
        g.fill(fx+73,fy-6,fx+99,fy+6,0xff3d424b);
        for(int i=0;i<5;i++)g.fill(fx+14+i*17,fy+70,fx+30+i*17,fy+76,0xff565c66); // 炉排
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        g.text(UiFonts.font(),title,8,6,UiTheme.TEXT,false);
        g.text(UiFonts.font(),playerInventoryTitle,8,inventoryLabelY,UiTheme.MUTED,false);
    }

    @Override protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){
        super.drawContents(g,x,y,delta);
        long now=ClientUi.now();
        int fx=leftPos+34,fy=topPos+14;
        drawFlames(g,fx,fy,now);
        if(gear==null){
            g.text(UiFonts.font(),"把装备拖进炉口",leftPos+88-fx0("把装备拖进炉口"),fy+40,UiTheme.MUTED,false);
        }
        if(now<noticeUntil&&!notice.isEmpty()){
            var font=UiFonts.font();
            int ny=fy+24;
            for(var line:font.split(Component.literal(notice),168)){
                g.fill(leftPos+4,ny-2,leftPos+172,ny+11,0x90000000);
                g.text(font,line,leftPos+8,ny,noticeColor,false);
                ny+=12;
            }
        }
        if(gear!=null)drawDetails(g);
    }
    private int fx0(String text){return UiFonts.font().width(text)/2;}

    private void drawFlames(GuiGraphicsExtractor g,int fx,int fy,long now){
        boolean burst=now<burstUntil;
        var random=new Random(now/120);
        int columns=7;
        for(int i=0;i<columns;i++){
            int cx=fx+16+i*12;
            int height=4+random.nextInt(6)+(burst?6:0);
            int color=(now/120+random.nextInt(3))%3==0?0xfff5c542:(now/120+random.nextInt(2))%2==0?0xffe2762d:0xffc8442a;
            g.fill(cx,fy+70-height,cx+8,fy+70,color);
            if(height>7)g.fill(cx+2,fy+70-height+2,cx+6,fy+70-height+6,0xff8a2f22);
        }
        g.fill(fx+14,fy+72,fx+94,fy+74,0xff6b3116);
    }

    private void drawDetails(GuiGraphicsExtractor g){
        var font=UiFonts.font();
        boolean amplified=amplified();
        int level=current(),first=level+1;
        int accent=amplified?EnhancementRules.AMPLIFY_PINK:UiTheme.GOLD;
        int y=topPos+104;
        if(maxed()){
            g.text(font,verb()+" +"+EnhancementRules.MAXIMUM+" · 已达炉火极限",leftPos+8,y,accent,false);
            if(amplified)g.text(font,"异次元"+contaminationTitle()+" +"+EnhancementRules.cumulative(gear.amplifyLevel),leftPos+8,y+12,EnhancementRules.AMPLIFY_PINK,false);
            return;
        }
        String head=verb()+" +"+level+(amplified?"（异次元"+contaminationTitle()+"）":"")+" · 冲+";
        g.text(font,head+first+" 成功率 "+EnhancementRules.successRate(first)+"%",leftPos+8,y,accent,false);
        y+=12;
        String consequence=switch(first<EnhancementRules.MAXIMUM?0:1){
            case 0->first>=20?"失败：降2级"+(first>=24?"":", 消耗照常"):first>=10?"失败：降1级，消耗照常":"失败：仅损失消耗";
            default->"失败：装备粉碎消失！";
        };
        if(first>=EnhancementRules.MAXIMUM){
            var warn=Component.literal("失败后果 · 失败：装备粉碎消失！").withStyle(net.minecraft.ChatFormatting.BOLD);
            g.text(font,warn,leftPos+8,y,0xffff3b5c,false);
        }else{
            String prefix="失败后果 · "+consequence;
            g.text(font,prefix,leftPos+8,y,UiTheme.TEXT,false);
            if(target==EnhancementRules.MAXIMUM){
                var warn=Component.literal("（含冲+25：失败粉碎！）").withStyle(net.minecraft.ChatFormatting.BOLD);
                g.text(font,warn,leftPos+8+font.width(prefix),y,0xffff3b5c,false);
            }
        }
        y+=12;
        long goldCost=EnhancementRules.goldCost(gear,first,amplified);
        int fuelNeed=EnhancementRules.fuel(first);
        String cost="消耗：金币 "+String.format(Locale.ROOT,"%,d",goldCost)+" · "+fuelName(amplified)+" ×"+fuelNeed;
        if(target>first)cost+="（连续到 +"+target+" 按次结算）";
        g.text(font,cost,leftPos+8,y,gold()>=goldCost&&fuel(amplified)>=fuelNeed?UiTheme.TEXT:UiTheme.ERROR,false);
        y+=12;
        String gain=gainPreview(first);
        g.text(font,"本次成功属性："+gain,leftPos+8,y,UiTheme.SUCCESS,false);
        if(amplified){
            y+=12;
            g.text(font,"气息侵染：永久禁止强化，只能增幅",leftPos+8,y,EnhancementRules.AMPLIFY_PINK,false);
        }
        String label="目标 +"+target;
        g.text(font,label,leftPos+56+(36-font.width(label))/2,topPos+169,UiTheme.TEXT,false);
    }

    private String contaminationTitle(){
        var stat=EnhancementRules.contaminationStat(gear);
        return stat==null?"":AttributeLabels.statName(stat);
    }

    private String gainPreview(int targetLevel){
        Map<Stat,Double> now=EnhancementRules.contribution(gear,current());
        Map<Stat,Double> next=EnhancementRules.contribution(gear,targetLevel);
        List<String> parts=new ArrayList<>();
        for(var entry:next.entrySet()){
            double delta=entry.getValue()-now.getOrDefault(entry.getKey(),0d);
            if(delta>0)parts.add(AttributeLabels.statName(entry.getKey())+" +"+trim(delta));
        }
        return parts.isEmpty()?"无":String.join("、",parts);
    }
    private static String trim(double value){
        double rounded=Math.round(value*10)/10d;
        return rounded==Math.floor(rounded)?String.valueOf((long)rounded):String.valueOf(rounded);
    }

    @Override protected void extractTooltip(GuiGraphicsExtractor g,int x,int y){
        if(hoveredSlot!=null&&hoveredSlot.index==0&&!menu.slots.get(0).getItem().isEmpty()){
            var stack=menu.slots.get(0).getItem();
            g.setComponentTooltipForNextFrame(UiFonts.font(),net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(),stack),x,y,stack);
            return;
        }
        super.extractTooltip(g,x,y);
    }
}
