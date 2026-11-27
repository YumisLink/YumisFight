package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

/** Dynamic Chinese tooltip descriptions for all 10 Witch skills and the Improved Magic Star upgrade. */
final class WitchDescriptions {
    private WitchDescriptions() {}

    private static boolean hasCard(TowerBattleCard card) {
        return SkillsClient.current().ready() && card.held(SkillController.INSTANCE.snapshot());
    }

    static String magicStarNote(SkillDescriptions.Context c) {
        if (!SkillsClient.current().progress().hasJob(JobCatalog.WITCH)) return "";
        var p = c.parameters();
        String repeat = hasCard(TowerBattleCard.WITCH_STAR) ? "（职业神话：可重复弹射至已命中的敌人）" : "";
        return "\n【魔道改良】自动升级为光属性追踪星弹（冷却1.5秒，支持空中施放），命中后产生半径"
            + number(p.get("witchBlastRadius", 2.2)) + "格光电爆裂并弹射至多"
            + (int) Math.round(p.get("witchBounceCount", 3.0)) + "次" + repeat + "；单体目标自动回弹造成"
            + number(p.get("witchSoloEchoRatio", 0.60) * 100.0) + "%追加星弹伤害。";
    }

    static void install() {
        SkillDescriptions.register(WitchSkills.WITCHCRAFT_MASTERY, c -> {
            int rank = c.parameters().rank();
            var p = c.parameters();
            double scale = p.get("broomBonusScale", 1.0);
            double cast = WitchRules.broomCastSpeedPercent(rank, scale);
            double atk = WitchRules.broomAttackSpeedPercent(rank, scale);
            double cd = WitchRules.broomCooldownRecovery(rank, scale) * 100.0;
            double mut = WitchRules.broomDamageMut(rank, scale) * 100.0;
            double elem = WitchRules.familiarAllElem(rank, p.get("familiarBaseElem", 6.0));
            double conv = p.get("buildupBonusRatio", 0.35) * 100.0;
            double res = p.get("ailmentResonancePerKind", 0.025) * 100.0;
            return "转职自动获得1级，可消耗SP升级；整合扫把掌握、稳定大成功与四大使魔亲和。\n"
                + "扫把掌握：解锁魔道专属武器「扫把」；装备扫把时普攻转为纯魔法150°广域扫击，并获得施放+" + number(cast)
                + "%、攻速+" + number(atk) + "%、冷却恢复+" + number(cd) + "%、最终增伤+" + number(mut) + "%（非扫把仅享受一半冷却与增伤）。\n"
                + "鞘翅式骑扫把滑翔：装备扫把在空中再次按跳跃进入乘骑滑翔（俯冲加速、抬头拉升、免疫摔伤），滑翔中可按冲刺键消耗体力进行至多"
                + (int) Math.round(p.get("broomAirDashCount", 2.0)) + "段空中疾驰，并可空中施放星弹、斗篷、熔岩与酸雨。\n"
                + "稳定大成功与苦涩炸炉：炼金与装置默认100%触发大成功；骑乘装置引导中再次按下技能键可主动「苦涩炸炉」，按65%剩余引导伤＋100%终爆瞬间引爆脱手。\n"
                + "四使魔亲和：常驻全属强+" + number(elem) + "，四系本源异常积累效率+" + number(conv)
                + "%；攻击处于元素异常的目标时，每种异常额外提供+" + number(res) + "%最终增伤（至多4种+" + number(res * 4.0) + "%）。";
        });

        SkillDescriptions.register(WitchSkills.SHADOW_CLOAK, c -> {
            var p = c.parameters();
            return "固有属性：暗 · 瞬发无敌聚怪（支持空中施放）。\n"
                + "掀开暗影斗篷获得" + number(p.get("invulnerableMillis", 650.0) / 1000.0)
                + "秒无敌，抓取并聚拢前方半径" + number(p.get("pullRadius", 5.0))
                + "格内非首领敌人施加" + number(p.get("controlSeconds", 0.65))
                + "秒束缚，随后引爆半径" + number(p.get("radius", 4.5)) + "格暗影爆破。";
        });

        SkillDescriptions.register(WitchSkills.GIANT_FLYSWATTER, c -> {
            var p = c.parameters();
            String trigger = hasCard(TowerBattleCard.WITCH_SWAT) ? "无论是否命中均弹出" : "命中弹出";
            return "固有属性：暗（魔偶按四系随机）· 巨拍重击＋祝福＋魔偶召唤。\n"
                + "挥舞巨型苍蝇拍重砸前方半径" + number(p.get("radius", 3.8)) + "格范围；命中即刷新"
                + number(p.get("buffDuration", 15.0)) + "秒「幸运糖果祝福」（最终魔伤+"
                + number(p.get("buffDamageMut", 0.06) * 100.0) + "%、施放+"
                + number(p.get("buffCastSpeed", 0.12) * 100.0) + "%、移速+"
                + number(p.get("buffMoveSpeed", 0.10) * 100.0) + "%）。\n"
                + "经典召唤彩蛋：" + trigger + (int) Math.round(p.get("summonBaseCount", 2.0))
                + "只（击杀或命中≥3目标加弹1只）随机小使魔（光电鳗／杰克／雪人／夜猫）蹦跳追踪自爆并叠加对应元素异常。";
        });

        SkillDescriptions.register(WitchSkills.LAVA_POTION, c -> {
            var p = c.parameters();
            String shred = hasCard(TowerBattleCard.WITCH_LAVA)
                ? "\n职业神话：池内与受影响敌人的火属性抗性降低" + number(TowerBattleCard.WITCH_LAVA.value("shred")) + "点。"
                : "";
            return "固有属性：火 · 脱手地面选点（最远" + number(p.get("range", 16.0)) + "格，支持骑扫把空中抛投）。\n"
                + "瓶碎后形成持续" + number(p.get("duration", 8.0)) + "秒、半径" + number(p.get("radius", 7.68))
                + "格的沸腾熔岩池，每" + number(p.get("interval", 0.50)) + "秒灼烧一次（共"
                + (int) Math.round(p.get("slashes", 16.0)) + "段）。\n"
                + "稳定大成功：存续期间向池内敌人密集处喷发" + (int) Math.round(p.get("geyserCount", 3.0))
                + "次炽热熔岩火柱（半径" + number(p.get("geyserRadius", 5.12)) + "格），极速叠加点燃魔法易伤。" + shred;
        });

        SkillDescriptions.register(WitchSkills.ACID_RAIN, c -> {
            var p = c.parameters();
            return "固有属性：冰＋光（落雷）· 脱手高空铺场（最远" + number(p.get("range", 16.0)) + "格，支持骑扫把空中施放）。\n"
                + "在目标上空凝聚持续" + number(p.get("duration", 8.0)) + "秒、半径" + number(p.get("radius", 8.32))
                + "格的魔道酸雨云，倾泻" + (int) Math.round(p.get("slashes", 16.0)) + "段冰属性酸雨叠加冻结／冰缓。\n"
                + "稳定大成功：云端额外劈落" + (int) Math.round(p.get("boltCount", 4.0))
                + "道光属性高能闪电（半径" + number(p.get("boltRadius", 5.12)) + "格），双系同步叠满异常。";
        });

        SkillDescriptions.register(WitchSkills.ANTI_GRAVITY, c -> {
            var p = c.parameters();
            if (hasCard(TowerBattleCard.WITCH_ANTIG)) {
                return "固有属性：暗 · 脱手地面选点（最远" + number(p.get("range", 15.0)) + "格）。\n"
                    + "职业神话：布设重力坍缩发生器，" + number(p.get("liftDelay", 0.35)) + "秒后改为将半径"
                    + number(p.get("radius", 5.5)) + "格内非首领敌人向中心强力聚拢并束缚"
                    + number(p.get("controlSeconds", 0.50)) + "秒（不再击飞），随后在中心引发半径"
                    + number(p.get("blastRadius", 6.2)) + "格巨型暗影爆破。";
            }
            return "固有属性：暗 · 脱手地面选点（最远" + number(p.get("range", 15.0)) + "格）。\n"
                + "布设反重力发生器，" + number(p.get("liftDelay", 0.35)) + "秒后在半径"
                + number(p.get("radius", 5.5)) + "格内升起反重力力场托举并束缚非首领敌人"
                + number(p.get("controlSeconds", 0.50)) + "秒；随后重力反转猛砸地面引发半径"
                + number(p.get("blastRadius", 6.2)) + "格巨型暗影爆破。";
        });

        SkillDescriptions.register(WitchSkills.RAGING_FLAME_FURNACE, c -> {
            var p = c.parameters();
            if (hasCard(TowerBattleCard.WITCH_FURNACE)) {
                return "固有属性：火 · 职业神话脱手自动装置。\n"
                    + "部署爆炎加热炉由使魔自动操作，持续" + number(p.get("duration", 5.60)) + "秒，向半径"
                    + number(p.get("radius", 7.5)) + "格高速喷射" + (int) Math.round(p.get("slashes", 28.0))
                    + "波熔岩火球，结束引发半径" + number(p.get("blastRadius", 6.5)) + "格炉体终爆；不再能主动苦涩炸炉。";
            }
            return "固有属性：火 · 骑乘装置（霸体＋" + number(p.get("channelProtection", 0.55) * 100.0) + "%减伤）。\n"
                + "骑乘爆炎加热炉持续" + number(p.get("duration", 2.80)) + "秒，稳定大成功向半径"
                + number(p.get("radius", 7.5)) + "格高速喷射" + (int) Math.round(p.get("slashes", 14.0))
                + "波熔岩火球，结束引发半径" + number(p.get("blastRadius", 6.5)) + "格炉体终爆。\n"
                + "苦涩炸炉：引导期间再次按下该技能键立刻引爆炉体，保留"
                + number(p.get("bitterKeepRatio", 0.65) * 100.0) + "%剩余伤害＋完整终爆，并轻快反冲腾空方便衔接骑扫把滑翔。";
        });

        SkillDescriptions.register(WitchSkills.MEGA_DRILL, c -> {
            var p = c.parameters();
            String shatter = hasCard(TowerBattleCard.WITCH_DRILL)
                ? "\n职业神话：冻结异常积累提高" + number(TowerBattleCard.WITCH_DRILL.value("buildup"))
                    + "%；命中冻结目标时立刻结束冻结并对其造成一次钻孔车爆炸破冰伤害。"
                : "";
            return "固有属性：冰 · 自由驾驶装置（霸体＋" + number(p.get("channelProtection", 0.60) * 100.0) + "%减伤）。\n"
                + "驾驶冰霜钻孔车持续" + number(p.get("duration", 3.20)) + "秒，期间可自由移动转向并强力吸附半径"
                + number(p.get("pullRadius", 7.5)) + "格内非首领敌人至钻头前方进行" + (int) Math.round(p.get("slashes", 16.0))
                + "段高频冰霜绞杀，结束引爆半径" + number(p.get("blastRadius", 6.8)) + "格暴风雪终爆。\n"
                + "苦涩炸炉：引导期间再次按下该技能键立刻令钻车前冲"
                + number(p.get("bitterDashDistance", 3.0)) + "格自爆，保留"
                + number(p.get("bitterKeepRatio", 0.65) * 100.0) + "%剩余伤害＋完整终爆并瞬间脱手。" + shatter;
        });

        SkillDescriptions.register(WitchSkills.FLORAE_COLLIDER, c -> {
            var p = c.parameters();
            if (hasCard(TowerBattleCard.WITCH_COLLIDER)) {
                return "固有属性：光 · 职业神话脱手自动强控装置。\n"
                    + "部署电鳗碰撞机由使魔自动操作，伤害降低" + number(TowerBattleCard.WITCH_COLLIDER.value("reduction"))
                    + "%，持续" + number(p.get("duration", 12.00)) + "秒，展开半径"
                    + number(p.get("radius", 8.5)) + "格高压雷牢释放" + (int) Math.round(p.get("slashes", 48.0))
                    + "段高频连锁闪电持续压制普通／精英敌人，结束引爆超导雷爆。";
            }
            return "固有属性：光 · 超大范围强控装置（霸体＋" + number(p.get("channelProtection", 0.55) * 100.0) + "%减伤）。\n"
                + "操作电鳗碰撞机持续" + number(p.get("duration", 3.00)) + "秒，展开半径"
                + number(p.get("radius", 8.5)) + "格高压雷牢释放" + (int) Math.round(p.get("slashes", 12.0))
                + "段高频连锁闪电持续压制普通／精英敌人，结束引爆超导雷爆。\n"
                + "苦涩炸炉：引导期间再次按下该技能键立刻令电网向心坍缩瞬爆，保留"
                + number(p.get("bitterKeepRatio", 0.65) * 100.0) + "%剩余伤害＋完整终爆并附带"
                + number(p.get("bitterControlSeconds", 0.80)) + "秒残留麻痹。";
        });

        SkillDescriptions.register(WitchSkills.FUSION_CRAFT, c -> {
            var p = c.parameters();
            return "魔术师 Lv50 觉醒技 · 全程无敌四系合体装置。\n"
                + "四大使魔齐力拼装终极炼金压路机持续" + number(p.get("duration", 3.60)) + "秒，吸附半径"
                + number(p.get("pullRadius", 9.5)) + "格内敌人并以火／冰／光／暗四系轮番轰炸碾压"
                + (int) Math.round(p.get("slashes", 16.0)) + "段（直接拉满四元素异常），终段引爆半径"
                + number(p.get("finisherRadius", 10.5)) + "格全元素融合核爆（自动取最高有效属强）。\n"
                + "终极苦涩过载：引导期间再次按下该技能键即可立刻引爆，保留"
                + number(p.get("bitterKeepRatio", 0.65) * 100.0) + "%剩余碾压伤害＋完整全元素核爆。";
        });
    }
}
