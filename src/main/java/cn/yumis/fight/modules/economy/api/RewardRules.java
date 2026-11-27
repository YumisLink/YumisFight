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
    public static final String NO_BONUS_EFFECTS="noBonusEffects";
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
        if(target.abyss==AbyssParty.Enemy.PILLAR){
            if(!participant||!context.nearby().contains(player))return new Result(progress,0,0,List.of());
            return abyss(progress,stats,context,random);
        }
        if(!sharesReward(instance,participant,context.nearby(),player,context.killer()))return new Result(progress,0,0,List.of());
        if(boss)appendBossDrops(context,drops,random);
        if(instance&&boss&&encounter!=null
                &&AwakeningTower.find(encounter.dungeon)==AwakeningTower.SEAL
                &&encounter.tower!=null&&encounter.tower.floor==TowerClimbRules.FLOOR_COUNT){
            Gear hat=NamedEquipment.create("named/elaina_witch_hat",random);
            hat.source=context.id();
            drops.add(new Drop(EquipmentItems.gear(hat),true));
            // TODO: 待「伊蕾娜的法杖」神话模板与独特词条设计批准后，在此追加 Drop(EquipmentItems.gear(NamedEquipment.create("named/elaina_staff", random)), true)
            drops.add(new Drop(ItemRegistry.item("elaina_token",1),true));
        }
        if(instance&&boss)for(var jar:JarDefinitions.current().jars.entrySet())if(random.nextDouble()<jar.getValue().bossDropChance())drops.add(new Drop(LootJarItem.create(jar.getKey(),1),true));
        var stone=encounter!=null&&encounter.waystone!=null?encounter.waystone.stone:null;
        var ecoBalance=EconomyBalanceRules.current();
        double experienceMultiplier=(stone==null?1:stone.experienceMultiplier)*ecoBalance.killExperienceMultiplier(),goldMultiplier=(stone==null?1:stone.goldMultiplier)*ecoBalance.killGoldMultiplier(),quantityMultiplier=(stone==null?1:stone.quantityMultiplier)*ecoBalance.dropQuantityMultiplier();
        double luck=stone==null?stats.get(Stat.DROP_RATE):stone.effectiveDropRate(stats.get(Stat.DROP_RATE));
        int level=target.level(progress.level,instance?encounter.syncFor(player):progress.sync);
        long xp=progress.earnExperience(killExperience(progress,level,target,context.settings(),stats,experienceMultiplier,practiceMob));
        long gold=CurrencyRules.killGold(Math.clamp(level,1,80),target.elite,boss,target.difficulty,context.settings().killGoldMultiplier()*goldMultiplier);
        int count=multiplied(boss?3:random.nextDouble()<(target.elite?.45:.05)?1:0,quantityMultiplier,random);
        for(int i=0;i<count;i++){
            var rarity=LootOdds.rarity(target.difficulty,boss,boss&&!instance,luck,random);
            drops.add(equipmentDrop(rarity,level,progress.level,personal,target.life.target+"/"+target.life.generation,random));
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
            var materials=materialDungeon.isEmpty()?List.<String>of():DungeonCatalog.current().dungeon(materialDungeon).materials();
            if(!materials.isEmpty()){
                int rolls=multiplied(boss?4:target.elite?2:random.nextDouble()<.2?1:0,quantityMultiplier,random);
                for(int i=0;i<rolls;i++)drops.add(new Drop(EquipmentItems.material(materials.get(random.nextInt(materials.size())),1),true));
            }
        }
        if(boss&&level==80&&encounter!=null&&encounter.treasure==null&&encounter.waystone==null&&random.nextDouble()<WaystoneRules.current().starterChance){var rarity=LootOdds.rarity(target.difficulty,true,false,stats.get(Stat.DROP_RATE),random);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;drops.add(new Drop(Waystone.generate(1,progress.level,rarity,random).item(),true));}
        return new Result(progress,xp,gold,List.copyOf(drops));
    }

    private static Result abyss(Progress progress,Stats stats,Context context,Random random){
        int level=context.target().abyssRewardLevel;
        var pool=ChestDefinitions.current().abyssPools.get(context.encounter().dungeon);
        var drops=new ArrayList<Drop>();
        for(int i=0;i<8;i++){
            var drop=abyssRareDrop(pool,level,10,2,context.id(),random);
            if(drop==null){
                var rarity=LootOdds.rarity(context.target().difficulty,true,true,stats.get(Stat.DROP_RATE),random);
                drop=equipmentDrop(rarity,level,progress.level,true,context.id(),random);
            }
            drops.add(drop);
        }
        var bonus=abyssRareDrop(pool,level,25,5,context.id(),random);
        if(bonus!=null)drops.add(bonus);
        return new Result(progress,0,0,List.copyOf(drops));
    }

    private static Drop abyssRareDrop(ChestDefinitions.AbyssPool pool,int level,int epicPercent,int mythicPercent,String source,Random random){
        int roll=random.nextInt(100);
        if(pool==null||roll>=epicPercent+mythicPercent)return null;
        var entries=roll<epicPercent?pool.epic():pool.mythic();
        var eligible=entries.stream().filter(entry->NamedEquipment.find(entry.template()).level()<=level).toList();
        var entry=ChestDefinitions.draw(eligible,random);
        return entry==null?null:new Drop(ChestRewards.item(entry,random,source),true);
    }

    private static Drop equipmentDrop(Rarity rarity,int level,int playerLevel,boolean personal,String source,Random random){
        var eligible=new ArrayList<>(List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING));
        for(var slot:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))if(playerLevel>=slot.minimum()&&level>=slot.minimum())eligible.add(slot);
        double group=random.nextDouble();
        Slot slot=rarity==Rarity.MYTHIC?(group<.5?Slot.WEAPON:eligible.get(random.nextInt(eligible.size()))):group<.2?Slot.WEAPON:group<.6?Slot.values()[1+random.nextInt(4)]:eligible.get(random.nextInt(eligible.size()));
        if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
        var gear=EquipmentTemplates.createStandard(slot,Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,Math.clamp(level,1,80),random);
        gear.source=source;
        return new Drop(EquipmentItems.gear(gear),personal);
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

    /** Combat kill experience: level curve, difficulty, server config, experience stat, the newcomer doubling at or below level 10, then waystone and practice factors. */
    public static long killExperience(Progress progress,int targetLevel,Target target,RewardSettings settings,Stats stats,double stoneExperienceMultiplier,boolean practiceMob){
        return CurrencyRules.roundedReward((10d+2d*targetLevel)*(target.boss()?10:target.elite?3:1)*target.difficulty.effectiveXp()*settings.combatExperienceMultiplier()*stats.get(Stat.EXPERIENCE_GAIN)*CharacterRules.newbieExperienceFactor(progress.level)*stoneExperienceMultiplier*(practiceMob?EnemyBalanceRules.current().practice.mobExperienceMultiplier():1));
    }

    /** Player-inherent experience rate for display hints: server config times the experience stat, doubled by the newcomer rule at or below level 10. Dungeon-side factors such as waystones and practice rooms settle inside each kill and never appear here. */
    public static double experienceMultiplier(double combatExperienceMultiplier,Stats stats,int level){
        return combatExperienceMultiplier*stats.get(Stat.EXPERIENCE_GAIN)*CharacterRules.newbieExperienceFactor(level);
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

    /** 范围内共享：副本内为在场参与者，野外为击杀者本人。 */
    public static boolean sharesReward(boolean instance,boolean participant,Set<UUID> nearby,UUID player,UUID killer){return instance?participant&&nearby.contains(player):player.equals(killer);}

    private static Result practice(Progress progress,UUID player,Context context){
        var fact=context.practice();
        if(fact.room().kind()==PracticeRoom.Kind.GOLD){
            if(fact.presentPlayers()<1)return new Result(progress,0,0,List.of());
            long singleKill=CurrencyRules.killGold(Math.clamp(fact.playerLevel(),1,80),false,false, Difficulty.NORMAL,context.settings().killGoldMultiplier());
            long gold=Math.multiplyExact(singleKill,PracticeRoom.WAVE_SIZE)/fact.presentPlayers();
            return new Result(progress,0,gold,List.of());
        }
        // 范围内共享：所有在场参与者各自独立结算；种子混入玩家保持每人重试幂等。
        var random=new Random(fact.seed()^player.getMostSignificantBits());
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
