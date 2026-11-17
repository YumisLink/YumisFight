package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

final class ArkScene {
    private static final int BLUE=0x80D8FF,GRAY=0x8E969E;
    private final ServerLevel level;
    private final ArkDisplays.Label[] traits=new ArkDisplays.Label[4];
    private final List<List<ArkDisplays.Label>> rows=new ArrayList<>();
    private final List<List<ArkDisplays.Label>> information=new ArrayList<>();
    private final List<Entity> towers=new ArrayList<>();
    private final Map<Integer,String> signs=new HashMap<>();
    private boolean guideAnchored;
    ArkScene(ServerLevel level){
        this.level=level;for(int i=0;i<4;i++){traits[i]=new ArkDisplays.Label();rows.add(new ArrayList<>());information.add(new ArrayList<>());}
        for(int side:new int[]{-1,1})for(int y=0;y<=3;y++)towers.add(ArkDisplays.block(level,Vec3.atLowerCornerOf(ArkWar.ORIGIN.offset(side*12,y,84)),y==3?"minecraft:amethyst_block":y==2?"minecraft:sea_lantern":"minecraft:polished_deepslate",1));
    }
    void update(ArkBattle battle,Set<UUID> present,long now){
        if(towers.stream().anyMatch(Entity::isRemoved)){towers.forEach(Entity::discard);towers.clear();for(int side:new int[]{-1,1})for(int y=0;y<=3;y++)towers.add(ArkDisplays.block(level,Vec3.atLowerCornerOf(ArkWar.ORIGIN.offset(side*12,y,84)),y==3?"minecraft:amethyst_block":y==2?"minecraft:sea_lantern":"minecraft:polished_deepslate",1));}
        anchorGuide();
        for(var plot:ArkWar.PLOTS){
            var seat=battle==null?null:battle.seats.stream().filter(s->s.index==plot.index()).findFirst().orElse(null);
            for(Entity entity:level.getAllEntities())if(entity instanceof ItemFrame frame){
                int slot=plot.frame(frame.position());if(slot<0)continue;
                var item=offer(battle,seat,slot);if(!ItemStack.isSameItemSameComponents(frame.getItem(),item))frame.setItem(item,false);
                frame.setInvulnerable(true);frame.setGlowingTag(seat!=null&&slot<5&&slot<seat.roster.shop.size()&&!seat.roster.shop.get(slot).isBlank()&&seat.roster.owns(seat.roster.shop.get(slot)));
            }
            Component text=Component.literal(seat==null?"空闲阵地":seat.name+" · 羁绊").withColor(0xD9E5EB);
            var rowTexts=new ArrayList<Component>();
            if(seat!=null){
                var counts=battle.editing()?ArkTraits.counts(seat.roster,battle.rules):seat.counts;
                List<String> ids=ArkTraits.present(battle.editing()?seat.roster:seat.opening,battle.rules);
                for(String id:ids){
                    int tier=ArkTraits.tier(id,counts,battle.rules),l=seat.layers.getOrDefault(id,0);
                    if(tier>0&&(id.equals("arcane")||id.equals("brute"))&&l>120)tier=2;
                    if(tier>0&&id.equals("precision")&&l>=120)tier=2;
                    if(tier>0&&(id.equals("chronology")||id.equals("winter"))&&l>=40)tier=2;
                    rowTexts.add(Component.literal(battle.rules.trait(id).name()+"（"+tier+"级） "+l+"层").withColor(tier>0?BLUE:GRAY));
                }
            }
            traits[plot.index()].set(level,plot.traitOrigin(),-plot.facing(),text,.45f,200);
            var labels=rows.get(plot.index());while(labels.size()<rowTexts.size())labels.add(new ArkDisplays.Label());
            for(int i=0;i<labels.size();i++)if(i<rowTexts.size())labels.get(i).set(level,plot.traitOrigin().add(0,-.12*(i+1),0),-plot.facing(),rowTexts.get(i),.45f,200);else labels.get(i).clear();
            information(plot,battle,seat,present,now);backing(plot);sign(plot,battle,present,now);
        }
    }
    private void information(ArkWar.Plot plot,ArkBattle battle,ArkBattle.Seat seat,Set<UUID> present,long now){
        var text=new ArrayList<Component>();
        if(battle==null){
            text.add(Component.literal("方舟战争 · 空闲阵地").withColor(0xE8D39B));
            text.add(Component.literal("请到水晶旁报名").withColor(BLUE));
            text.add(Component.literal("全员确认后进入准备").withColor(GRAY));
        }else{
            text.add(Component.literal("第"+battle.wave+"／20波 · "+(battle.editing()?"准备":"战斗")).withColor(0xE8D39B));
            text.add(Component.literal("核心 "+ArkRules.number(battle.core.target.health(1)*battle.core.target.life.q)+"／"+ArkRules.number(battle.core.target.health(1))).withColor(BLUE));
            text.add(Component.literal(battle.boss()&&!battle.editing()?"Boss战不限时":"剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒").withColor(BLUE));
            text.add(Component.literal(battle.editing()?"已准备 "+battle.readyCount(present)+"／"+battle.presentCount(present):"场内 "+battle.actors.stream().filter(a->a.enemy&&a.alive()).count()+" · 待出场 "+battle.seats.stream().mapToInt(s->s.queue.size()-s.spawned).sum()).withColor(0xD9E5EB));
            if(seat!=null){
                text.add(Component.literal(seat.name+" · 商店"+seat.roster.level+"级").withColor(0xD9E5EB));
                text.add(Component.literal("人口 "+seat.roster.population()+"／"+battle.rules.populations[seat.roster.level-1]+" · 方舟币 "+seat.roster.coins).withColor(BLUE));
            }
            if(battle.editing()||battle.wave<20){
                int wave=battle.editing()?battle.wave:battle.wave+1;boolean dragon=battle.dragon[wave],boss=wave%5==0;var e=battle.rules.enemies;
                text.add(Component.literal((battle.editing()?"本波":"下波")+"预告 · "+(dragon?"龙人军团":"哥布林军团")).withColor(0xE8D39B));
                text.add(Component.literal(boss?(dragon?"鲁卡斯":"落雷凯诺")+"×1 · 护卫×"+(dragon?e.dragonGuards:e.goblinGuards):"每线 "+e.count(wave,dragon)+"名 · 每秒1名").withColor(0xD9E5EB));
                text.add(Component.literal((boss?"Boss":"普通")+"生命 "+ArkRules.number(boss?e.bossHealth(wave):e.health(wave,dragon))).withColor(BLUE));
                text.add(Component.literal((boss?"Boss":"普通")+"攻击 "+ArkRules.number(boss?e.bossAttack(wave):e.attack(wave,dragon))).withColor(BLUE));
                text.add(Component.literal("物防 "+ArkRules.number(e.physicalDefense(wave,dragon))+" · 魔防 "+ArkRules.number(e.magicalDefense(wave))).withColor(BLUE));
                double[] resist=e.resistances(dragon);text.add(Component.literal("光／火／冰／暗抗").withColor(GRAY));
                text.add(Component.literal(ArkRules.number(resist[0])+"／"+ArkRules.number(resist[1])+"／"+ArkRules.number(resist[2])+"／"+ArkRules.number(resist[3])).withColor(BLUE));
                if(!boss){
                    text.add(Component.literal(dragon?"龙人／翼龙／蓝龙人／蓝翼龙":"哥布林／投石／队长").withColor(GRAY));
                    text.add(Component.literal(dragon?"生命倍率 1／0.8／1.2／0.9":"生命倍率 1／0.8／1.6").withColor(BLUE));
                    text.add(Component.literal(dragon?"攻击倍率 1／1.2／1.1／1.3":"攻击倍率 1／1.2／1.4").withColor(BLUE));
                }
            }
        }
        var labels=information.get(plot.index());while(labels.size()<text.size())labels.add(new ArkDisplays.Label());
        for(int i=0;i<labels.size();i++)if(i<text.size())labels.get(i).set(level,plot.informationOrigin().add(0,-.13*i,0),-plot.facing(),text.get(i),.42f,210);else labels.get(i).clear();
    }
    private ItemStack offer(ArkBattle battle,ArkBattle.Seat seat,int slot){
        if(seat==null)return ItemStack.EMPTY;
        if(slot<5){
            String id=seat.roster.shop.get(slot);if(id.isBlank())return ItemStack.EMPTY;var unit=battle.rules.unit(id);
            var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath(ItemData.NS,"ark/card/"+id));
            stack.set(DataComponents.CUSTOM_NAME,ModText.of(unit.name()+" ★ · "+unit.cost()+"方舟币").withColor(BLUE));
            stack.set(DataComponents.LORE,new ItemLore(unit.description().stream().map(unit::describe).map(ModText::of).map(c->(Component)c).toList()));return stack;
        }
        var stack=new ItemStack(slot==5?Items.ENDER_PEARL:slot==6?Items.EXPERIENCE_BOTTLE:Items.BOOK);
        String title=slot==5?"刷新商店 · "+battle.rules.refreshCost+"方舟币":slot==6?(seat.roster.level>=7?"商店已满级":"升级商店 · "+seat.roster.upgradePrice(battle.rules)+"方舟币"):"阵容信息 · "+seat.roster.population()+"/"+battle.rules.populations[seat.roster.level-1]+"人口";
        stack.set(DataComponents.CUSTOM_NAME,ModText.of(title).withColor(BLUE));return stack;
    }
    private void backing(ArkWar.Plot plot){
        int x=plot.side()<0?-5:5;
        for(int offset:new int[]{-8,6})for(int z=plot.centerZ()+offset;z<=plot.centerZ()+offset+1;z++)for(int y=0;y<3;y++){var pos=ArkWar.ORIGIN.offset(x,y,z);var state=level.getBlockState(pos);if(state.isAir()||state.canBeReplaced())level.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),3);}
    }
    private void sign(ArkWar.Plot plot,ArkBattle battle,Set<UUID> present,long now){
        String value;
        if(battle==null)value="方舟战争\n水晶旁报名\n全员确认开始\n本阵地尚未分配";
        else if(battle.editing())value="第"+battle.wave+"波准备\n剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒\n已准备 "+battle.readyCount(present)+"/"+battle.presentCount(present)+"\n右键准备／取消";
        else value="第"+battle.wave+"波战斗\n"+(battle.boss()?"Boss战不限时":"剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒")+"\n战斗中锁定阵容\n等待本波结束";
        var pos=plot.readySign();
        if(!level.getBlockState(pos).is(Blocks.OAK_WALL_SIGN)){level.setBlock(pos,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,plot.side()<0?Direction.WEST:Direction.EAST),3);signs.remove(plot.index());}
        if(value.equals(signs.get(plot.index())))return;
        if(level.getBlockEntity(pos) instanceof SignBlockEntity sign){
            var text=new SignText();String[] lines=value.split("\n");for(int i=0;i<4;i++)text=text.setMessage(i,ModText.of(lines[i]).withColor(i==0?0xE8D39B:BLUE));
            sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.setChanged();level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),3);signs.put(plot.index(),value);
        }
    }
    private void anchorGuide(){
        if(guideAnchored)return;
        for(Entity raw:level.getAllEntities())if(raw instanceof NpcEntity npc&&!(npc instanceof ArkRoleEntity)&&NpcInteractionComponent.id(npc).equals("ark_war_guide")){
            npc.relocate(new Vec3(ArkWar.ORIGIN.getX()-3.5,ArkWar.ORIGIN.getY()+3,ArkWar.ORIGIN.getZ()+85.5),0);guideAnchored=true;return;
        }
    }
    void close(){for(var label:traits)label.clear();rows.forEach(list->list.forEach(ArkDisplays.Label::clear));information.forEach(list->list.forEach(ArkDisplays.Label::clear));towers.forEach(Entity::discard);towers.clear();signs.clear();}
}
