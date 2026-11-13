package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Personal kill rewards from an already committed world death. */
public final class RewardRules {
    public record Context(String id,UUID killer,Target target,Dungeon encounter,RewardSettings settings,String dimension,double x,double y,double z,Set<UUID> nearby){}
    public record Drop(ItemStack item,boolean privateLoot){}
    public record Result(Progress progress,long experience,long gold,List<Drop> drops){}
    private RewardRules(){}
    public static Result kill(Progress current,Stats stats,UUID player,Context context,Random random){
        var progress=CareerProfiles.copy(current);var target=context.target();var encounter=context.encounter();boolean instance=!target.instance.isEmpty(),boss=!target.kind.equals("NORMAL");var drops=new ArrayList<Drop>();
        boolean participant=encounter!=null&&encounter.roster.contains(player)&&!encounter.withdrawn.contains(player);
        if(target.noRewards)return new Result(progress,0,0,List.of());
        if(instance&&boss&&player.equals(context.killer())&&encounter!=null){String poolId=encounter.treasure==null?encounter.dungeon:"treasure_"+encounter.treasure.theme;var pool=ChestDefinitions.current().bossPools.get(poolId);if(pool!=null&&random.nextDouble()<pool.chance()){var entry=ChestDefinitions.draw(pool.entries(),random);if(entry!=null)drops.add(new Drop(ChestRewards.item(entry),false));}}
        if(instance&&target.boss()&&player.equals(context.killer())&&encounter!=null&&encounter.treasure==null){var pool=ChestDefinitions.current().bossRarePools.get(encounter.dungeon);if(pool!=null&&random.nextDouble()<pool.chance()){var entry=ChestDefinitions.draw(pool.entries(),random);if(entry!=null)drops.add(new Drop(ChestRewards.item(entry),false));}}
        if(instance&&(!participant||!context.nearby().contains(player))||!instance&&!player.equals(context.killer()))return new Result(progress,0,0,List.copyOf(drops));
        if(instance&&boss)for(var jar:JarDefinitions.current().jars.entrySet())if(random.nextDouble()<jar.getValue().bossDropChance())drops.add(new Drop(LootJarItem.create(jar.getKey(),1),true));
        var stone=encounter!=null&&encounter.waystone!=null?encounter.waystone.stone:null;
        double experienceMultiplier=stone==null?1:stone.experienceMultiplier,goldMultiplier=stone==null?1:stone.goldMultiplier,quantityMultiplier=stone==null?1:stone.quantityMultiplier;
        double luck=stone==null?stats.get(Stat.DROP_RATE):stone.effectiveDropRate(stats.get(Stat.DROP_RATE));
        int level=target.level(progress.level,instance?encounter.syncFor(player):progress.sync);
        long xp=CurrencyRules.roundedReward((10d+2d*level)*(boss?10:target.elite?3:1)*target.difficulty.xp*context.settings().combatExperienceMultiplier()*stats.get(Stat.EXPERIENCE_GAIN)*experienceMultiplier);progress.grant(xp);
        long gold=CurrencyRules.killGold(Math.clamp(level,1,80),target.elite,boss,target.difficulty,context.settings().killGoldMultiplier()*goldMultiplier);
        int count=multiplied(boss?3:random.nextDouble()<(target.elite?.45:.05)?1:0,quantityMultiplier,random);
        for(int i=0;i<count;i++){
            var rarity=LootOdds.rarity(target.difficulty,boss,boss&&!instance,luck,random);var eligible=new ArrayList<>(List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING));for(var slot:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))if(progress.level>=slot.minimum()&&level>=slot.minimum())eligible.add(slot);
            double group=random.nextDouble();Slot slot=rarity==Rarity.MYTHIC?(group<.5?Slot.WEAPON:eligible.get(random.nextInt(eligible.size()))):group<.2?Slot.WEAPON:group<.6?Slot.values()[1+random.nextInt(4)]:eligible.get(random.nextInt(eligible.size()));if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            var gear=EquipmentTemplates.createStandard(slot,Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,Math.clamp(level,1,80),random);gear.source=target.life.target+"/"+target.life.generation;drops.add(new Drop(EquipmentItems.gear(gear),instance));
        }
        var currency=CurrencyRules.roll(luck,random);if(currency!=null)for(int i=0,n=multiplied(1,quantityMultiplier,random);i<n;i++)drops.add(new Drop(ItemRegistry.item(currency.id(),1),instance));
        var gem=CurrencyRules.rollGem(target.elite,boss,luck,random);if(gem!=null)drops.add(new Drop(Gemstones.item(gem),instance));
        if(instance){var def=DungeonCatalog.current().dungeon(encounter.dungeon);int rolls=multiplied(boss?4:target.elite?2:random.nextDouble()<.2?1:0,quantityMultiplier,random);for(int i=0;i<rolls;i++)drops.add(new Drop(EquipmentItems.material(def.materials().get(random.nextInt(def.materials().size())),1),true));}
        if(boss&&level==80&&encounter!=null&&encounter.treasure==null&&encounter.waystone==null&&random.nextDouble()<WaystoneRules.current().starterChance){var rarity=LootOdds.rarity(target.difficulty,true,false,stats.get(Stat.DROP_RATE),random);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;drops.add(new Drop(Waystone.generate(1,progress.level,rarity,random).item(),true));}
        return new Result(progress,xp,gold,List.copyOf(drops));
    }
    private static int multiplied(int count,double multiplier,Random random){if(count==0||multiplier==1)return count;double expected=count*multiplier;if(!Double.isFinite(expected)||expected>Integer.MAX_VALUE)throw new IllegalArgumentException("掉落数量超出可计算范围");int whole=(int)Math.floor(expected);return whole+(random.nextDouble()<expected-whole?1:0);}
}
