package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Personal rewards from committed world events. */
public final class RewardRules {
    public record Context(String id,UUID killer,Target target,Dungeon encounter,RewardSettings settings,String dimension,double x,double y,double z,Set<UUID> nearby,PracticeReward practice,RitualReward ritual,Supply supply,List<Integer> experiencePercentages,String worldBossType){
        public Context(String id,UUID killer,Target target,Dungeon encounter,RewardSettings settings,String dimension,double x,double y,double z,Set<UUID> nearby,PracticeReward practice,RitualReward ritual,Supply supply){
            this(id,killer,target,encounter,settings,dimension,x,y,z,nearby,practice,ritual,supply,null,null);
        }
        public Context(String id,UUID killer,Target target,Dungeon encounter,RewardSettings settings,String dimension,double x,double y,double z,Set<UUID> nearby,PracticeReward practice){
            this(id,killer,target,encounter,settings,dimension,x,y,z,nearby,practice,null,null);
        }
        public static Context experience(String id,List<Integer> percentages){
            return new Context(id,null,null,null,null,"",0,0,0,Set.of(),null,null,null,List.copyOf(percentages),null);
        }
        public boolean experienceOnly(){return experiencePercentages!=null;}
        public String instance(){return practice!=null?practice.instance():target==null?"":target.instance;}
    }
    public record PracticeReward(String instance,PracticeRoom room,int playerLevel,int presentPlayers,double dropRate,long seed){}
    public record RitualReward(String material,String secondaryMaterial){}
    public record Supply(String item,int count){}
    public record Drop(ItemStack item,boolean privateLoot){}
    public record Result(Progress progress,long experience,long gold,List<Drop> drops){}
    private static final double PRACTICE_CURRENCY_CHANCE=.20;
    private RewardRules(){}
    public static Result reward(Progress current,Stats stats,UUID player,Context context,Random random){
        if(context.experienceOnly())return experience(current,context.experiencePercentages());
        var progress=CareerProfiles.copy(current);
        if(context.practice()!=null)return practice(progress,player,context);
        var target=context.target();var encounter=context.encounter();boolean instance=!target.instance.isEmpty(),boss=!target.kind.equals("NORMAL");var drops=new ArrayList<Drop>();
        boolean ritual=context.ritual()!=null, personal=instance||ritual, practiceMob=encounter!=null&&encounter.practice!=null&&encounter.practice.kind()==PracticeRoom.Kind.MOB;
        boolean participant=encounter!=null&&encounter.roster.contains(player)&&!encounter.withdrawn.contains(player);
        if(target.noRewards)return new Result(progress,0,0,List.of());
        if(boss&&player.equals(context.killer()))appendBossDrops(context,drops,random);
        if(instance&&(!participant||!context.nearby().contains(player))||!instance&&!player.equals(context.killer()))return new Result(progress,0,0,List.copyOf(drops));
        if(instance&&boss)for(var jar:JarDefinitions.current().jars.entrySet())if(random.nextDouble()<jar.getValue().bossDropChance())drops.add(new Drop(LootJarItem.create(jar.getKey(),1),true));
        var stone=encounter!=null&&encounter.waystone!=null?encounter.waystone.stone:null;
        double experienceMultiplier=stone==null?1:stone.experienceMultiplier,goldMultiplier=stone==null?1:stone.goldMultiplier,quantityMultiplier=stone==null?1:stone.quantityMultiplier;
        double luck=stone==null?stats.get(Stat.DROP_RATE):stone.effectiveDropRate(stats.get(Stat.DROP_RATE));
        int level=target.level(progress.level,instance?encounter.syncFor(player):progress.sync);
        long xp=progress.earnExperience(CurrencyRules.roundedReward((10d+2d*level)*(boss?10:target.elite?3:1)*target.difficulty.xp*context.settings().combatExperienceMultiplier()*stats.get(Stat.EXPERIENCE_GAIN)*experienceMultiplier*(practiceMob?PracticeRoom.MOB_EXPERIENCE_MULTIPLIER:1)));
        long gold=CurrencyRules.killGold(Math.clamp(level,1,80),target.elite,boss,target.difficulty,context.settings().killGoldMultiplier()*goldMultiplier);
        int count=multiplied(boss?3:random.nextDouble()<(target.elite?.45:.05)?1:0,quantityMultiplier,random);
        for(int i=0;i<count;i++){
            var rarity=LootOdds.rarity(target.difficulty,boss,boss&&!instance,luck,random);var eligible=new ArrayList<>(List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING));for(var slot:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))if(progress.level>=slot.minimum()&&level>=slot.minimum())eligible.add(slot);
            double group=random.nextDouble();Slot slot=rarity==Rarity.MYTHIC?(group<.5?Slot.WEAPON:eligible.get(random.nextInt(eligible.size()))):group<.2?Slot.WEAPON:group<.6?Slot.values()[1+random.nextInt(4)]:eligible.get(random.nextInt(eligible.size()));if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            var gear=EquipmentTemplates.createStandard(slot,Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,Math.clamp(level,1,80),random);gear.source=target.life.target+"/"+target.life.generation;drops.add(new Drop(EquipmentItems.gear(gear),personal));
        }
        var currency=CurrencyRules.roll(luck,random);if(currency!=null)for(int i=0,n=multiplied(1,quantityMultiplier,random);i<n;i++)drops.add(new Drop(ItemRegistry.item(currency.id(),1),personal));
        if(ritual){
            drops.add(new Drop(ItemRegistry.item(context.ritual().material(),1+random.nextInt(3)),true));
            if(!context.ritual().secondaryMaterial().isEmpty())drops.add(new Drop(ItemRegistry.item(context.ritual().secondaryMaterial(),1),true));
        }else{
            var gem=CurrencyRules.rollGem(target.elite,boss,luck,random);if(gem!=null)drops.add(new Drop(Gemstones.item(gem),instance));
        }
        if(context.supply()!=null)drops.add(new Drop(ItemRegistry.item(context.supply().item(),context.supply().count()),true));
        // Plain practice-room mobs roll materials from the dungeon each mob was borrowed from.
        if(instance){
            String materialDungeon=practiceMob?target.originDungeon:encounter.dungeon;
            if(!materialDungeon.isEmpty()){var def=DungeonCatalog.current().dungeon(materialDungeon);int rolls=multiplied(boss?4:target.elite?2:random.nextDouble()<.2?1:0,quantityMultiplier,random);for(int i=0;i<rolls;i++)drops.add(new Drop(EquipmentItems.material(def.materials().get(random.nextInt(def.materials().size())),1),true));}
        }
        if(boss&&level==80&&encounter!=null&&encounter.treasure==null&&encounter.waystone==null&&random.nextDouble()<WaystoneRules.current().starterChance){var rarity=LootOdds.rarity(target.difficulty,true,false,stats.get(Stat.DROP_RATE),random);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;drops.add(new Drop(Waystone.generate(1,progress.level,rarity,random).item(),true));}
        return new Result(progress,xp,gold,List.copyOf(drops));
    }

    private static void appendBossDrops(Context context,List<Drop> drops,Random random){
        var encounter=context.encounter();
        String ordinary=context.worldBossType(),rare=ordinary;
        if(!context.instance().isEmpty()&&encounter!=null){
            ordinary=encounter.treasure==null?encounter.dungeon:"treasure_"+encounter.treasure.theme;
            rare=encounter.treasure==null?encounter.dungeon:null;
        }
        var definitions=ChestDefinitions.current();
        if(ordinary!=null)appendBossDrop(drops,definitions.bossPools.get(ordinary),random,context.id());
        if(rare!=null&&context.target().boss())appendBossDrop(drops,definitions.bossRarePools.get(rare),random,context.id());
    }

    private static void appendBossDrop(List<Drop> drops,ChestDefinitions.SpecialPool pool,Random random,String source){
        if(pool==null||random.nextDouble()>=pool.chance())return;
        var entry=ChestDefinitions.draw(pool.entries(),random);
        if(entry!=null)drops.add(new Drop(ChestRewards.item(entry,random,source),false));
    }

    /** Player-inherent experience rate for display hints: server config times the experience stat. Dungeon-side factors such as waystones and practice rooms settle inside each kill and never appear here. */
    public static double experienceMultiplier(double combatExperienceMultiplier,Stats stats){
        return combatExperienceMultiplier*stats.get(Stat.EXPERIENCE_GAIN);
    }

    private static Result experience(Progress current,List<Integer> percentages){
        var progress=CareerProfiles.copy(current);
        long total=0;
        for(int percentage:percentages){
            long base=CurrencyRules.roundedReward(CharacterRules.nextExperience(progress.level)*percentage/100d);
            total=Math.addExact(total,progress.earnExperience(base));
        }
        return new Result(progress,total,0,List.of());
    }

    private static Result practice(Progress progress,UUID player,Context context){
        var fact=context.practice();
        if(fact.room().kind()==PracticeRoom.Kind.GOLD){
            if(fact.presentPlayers()<1)return new Result(progress,0,0,List.of());
            long singleKill=CurrencyRules.killGold(Math.clamp(fact.playerLevel(),1,80),false,false, Difficulty.NORMAL,context.settings().killGoldMultiplier());
            long gold=Math.multiplyExact(singleKill,PracticeRoom.WAVE_SIZE)/fact.presentPlayers();
            return new Result(progress,0,gold,List.of());
        }
        if(!player.equals(context.killer()))return new Result(progress,0,0,List.of());
        // Use the drop table, luck and random sequence captured when the enemy died.
        var random=new Random(fact.seed());
        if(fact.room().kind()==PracticeRoom.Kind.CURRENCY){
            if(random.nextDouble()>=PRACTICE_CURRENCY_CHANCE)return new Result(progress,0,0,List.of());
            var currency=CurrencyRules.rollGuaranteed(fact.dropRate(),random);
            return new Result(progress,0,0,List.of(new Drop(ItemRegistry.item(currency.id(),1),false)));
        }
        var pool=fact.room().materials();
        return new Result(progress,0,0,List.of(new Drop(EquipmentItems.material(pool.get(random.nextInt(pool.size())),1),false)));
    }

    private static int multiplied(int count,double multiplier,Random random){if(count==0||multiplier==1)return count;double expected=count*multiplier;if(!Double.isFinite(expected)||expected>Integer.MAX_VALUE)throw new IllegalArgumentException("掉落数量超出可计算范围");int whole=(int)Math.floor(expected);return whole+(random.nextDouble()<expected-whole?1:0);}
}
