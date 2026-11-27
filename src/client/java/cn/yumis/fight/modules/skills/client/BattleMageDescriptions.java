package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

/** Dynamic tooltip descriptions for all 10 Battle Mage skills + Dragon Fang linkage & morph presets. */
final class BattleMageDescriptions {
    private BattleMageDescriptions() {}

    private static Stats currentStats() {
        return SkillsClient.current().ready() ? SkillController.INSTANCE.snapshot() : null;
    }

    private static boolean hasCard(TowerBattleCard card) {
        return card.held(currentStats());
    }

    static void install() {
        SkillDescriptions.register(BattleMageSkills.BATTLE_GROOVE, c -> {
            int rank = c.parameters().rank();
            var p = c.parameters();
            Stats snap = currentStats();
            boolean niu = TowerBattleCard.BM_NIU_MASTERY.held(snap);
            boolean unbound = TowerBattleCard.BM_GROOVE_UNBOUND.held(snap);
            int cap = (int) Math.max(1, p.get("comboStageCap", 6.0));
            double perMut = BattleMageRules.groovePerStageMut(rank, p.get("grooveBonusScale", 1.0)) * 100.0;
            double perCrit = p.get("perStageCrit", 0.015) * 100.0;
            double interval = BattleMageRules.autoChaserIntervalSeconds(rank, p, snap);
            double spearAspd = 10.0 + (niu ? TowerBattleCard.BM_NIU_MASTERY.value("spearspeed") : 0.0);
            double clubCdr = BattleMageRules.clubCdRecovery(rank) * (niu ? 1.0 + TowerBattleCard.BM_NIU_MASTERY.ratio("mastery") : 1.0) * 100.0;
            StringBuilder sb = new StringBuilder();
            sb.append("转职自动习得1级，整合战法武器精通＋斗神连击成长＋自动炫纹＋尼巫三段近战普攻。\n");
            sb.append("武器精通（随主手切换）：\n");
            sb.append("· 矛：物攻+").append(number(BattleMageRules.spearPhysMut(rank, p.get("spearBonusScale", 1.0)) * 100))
                .append("%、攻速+").append(number(spearAspd)).append("%，体术范围×").append(number(p.get("spearRangeScale", 1.15)))
                .append("、蓄力耗时×").append(number(p.get("spearChargeScale", 0.85))).append("。\n");
            sb.append("· 棍棒：物魔双攻+").append(number(BattleMageRules.clubDualMut(rank, p.get("clubBonusScale", 1.0)) * 100))
                .append("%、攻速+8%、冷却效率+").append(number(clubCdr))
                .append("%，自动炫纹间隔×").append(number(p.get("clubAutoChaserScale", 0.80)))
                .append("、炫纹范围×").append(number(p.get("clubChaserRadiusScale", 1.15)))
                .append(niu ? "，全部近战普攻" + number(TowerBattleCard.BM_NIU_MASTERY.value("basicchance")) + "%概率产球。" : "。").append("\n");
            sb.append("· 法杖／魔杖：魔攻+").append(number(BattleMageRules.staffMagicMut(rank, p.get("staffBonusScale", 1.0)) * 100))
                .append("%，炫纹系冷却×").append(number(p.get("staffChaserCdScale", 0.85)))
                .append(niu ? "，每次耗球回复" + number(TowerBattleCard.BM_NIU_MASTERY.value("manaregen")) + "%最大魔力。" : "。").append("\n");
            sb.append("连击成长：最高").append(cap).append("阶，每阶提升").append(number(perMut))
                .append("%伤害（MUT）与").append(number(perCrit)).append("%暴击率（满阶共+")
                .append(number(perMut * cap)).append("%伤害 / +").append(number(perCrit * cap)).append("%暴击）。\n");
            sb.append("自动炫纹：战斗中每").append(number(interval)).append("秒自动生成1颗随机五色炫纹；近战普攻第3段与体术命中均随机产球。");
            if (p.get("forcedChaserEnabled", 0.0) >= 1.0) {
                int ft = Math.clamp((int) Math.round(p.get("forcedChaserType", 0.0)), 0, 4);
                sb.append("\n单色锁定：当前强制产出【").append(BattleMageRules.chaserName(ft, false)).append("】。");
            }
            if (unbound) {
                sb.append("\n职业神话·斗神无双：连击上限").append(TowerBattleCard.BM_GROOVE_UNBOUND.count("cap"))
                    .append("阶、阈值降低").append(number(TowerBattleCard.BM_GROOVE_UNBOUND.value("threshold")))
                    .append("%、每阶加成提高").append(number(TowerBattleCard.BM_GROOVE_UNBOUND.value("bonus")))
                    .append("%、保持").append(number(TowerBattleCard.BM_GROOVE_UNBOUND.value("hold")))
                    .append("秒、每次命中额外+").append(TowerBattleCard.BM_GROOVE_UNBOUND.count("hits")).append("连击。");
            }
            if (niu) {
                sb.append("\n职业神话·尼巫极意：矛／棍／杖三系武器精通已全面强化。");
            }
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.CHASER_LAUNCHER, c -> {
            int rank = c.parameters().rank();
            var p = c.parameters();
            Stats snap = currentStats();
            StringBuilder sb = new StringBuilder();
            sb.append("纯魔法伤害 · 0公共冷却零硬直插帧施放（可在任意体术与普攻动作中瞬发）。\n");
            sb.append("优先锁敌轰击最近").append(number(p.get("lockWindowSeconds", 1.2))).append("秒内命中的目标，否则朝准星发射追踪炫纹（上限")
                .append((int) Math.round(p.get("maxChasers", 7.0))).append("颗，爆炸半径").append(number(p.get("radius", 2.4))).append("格）。\n");
            sb.append("五色独立Buff（持续").append(number(p.get("buffDuration", 20.0))).append("秒，互不冲突）：\n");
            sb.append("无:移速+").append(number(BattleMageRules.neutralMoveSpeed(rank, p, snap) * 100))
                .append("%  光:攻速+").append(number(BattleMageRules.lightAttackSpeed(rank, p, snap) * 100))
                .append("%  火:全伤+").append(number(BattleMageRules.fireDamageMut(rank, p, snap) * 100)).append("%\n");
            sb.append("冰:减伤").append(number(BattleMageRules.iceDamageReduction(rank, p, snap) * 100))
                .append("%  暗:暴击+").append(number(BattleMageRules.darkCritBonus(rank, p, snap) * 100)).append("%");
            if (p.get("autoFireOnHit", 0.0) >= 1.0) {
                sb.append("\n自动追击已激活：体术与普攻命中自动发射炫纹。");
            }
            if (TowerBattleCard.BM_CHASER_OVERFLOW.held(snap)) {
                sb.append("\n职业神话·溢流炫纹：队列满球后新生成的溢出炫纹自动零硬直无消耗追踪发射（造成原炫纹")
                    .append(number(TowerBattleCard.BM_CHASER_OVERFLOW.value("overflow"))).append("%伤害），范围+")
                    .append(number(TowerBattleCard.BM_CHASER_OVERFLOW.value("radius"))).append("%，五色Buff效果+")
                    .append(number(TowerBattleCard.BM_CHASER_OVERFLOW.value("buff"))).append("%、持续+")
                    .append(number(TowerBattleCard.BM_CHASER_OVERFLOW.value("duration"))).append("%。");
            }
            appendMorph(sb, BattleMageSkills.CHASER_LAUNCHER, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.PALM_BLAST, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("纯物理体术 · 命中随机产球并开放柔化取消窗口。").append(chargeLine(p)).append("\n");
            sb.append("霸体向前突进").append(number(p.get("dashDistance", 4.5))).append("格并掌劲冲击半径")
                .append(number(p.get("radius", 2.6))).append("格");
            if (p.get("knockbackDistance", 3.5) > 0) {
                sb.append("，击退普通敌人").append(number(p.get("knockbackDistance", 3.5))).append("格");
            }
            sb.append("。\n变身贝亚娜期间自动升格为「苍天击」（范围、距离与威力大幅强化）。");
            appendMorph(sb, BattleMageSkills.PALM_BLAST, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.FULL_SWING, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("纯物理体术（柔化中枢） · 命中随机产球并开放").append(number(p.get("cancelWindowSeconds", 0.45))).append("秒体术柔化窗口。\n");
            sb.append("挥舞长兵横扫").append(number(p.get("arcDegrees", 210.0))).append("°、半径")
                .append(number(p.get("radius", 4.5))).append("格范围敌人。变身贝亚娜期间升格为「天地碎霸」。");
            if (p.get("chaserSlash", 0.0) > 0) {
                sb.append("\n炫纹附魔：挥扫消耗1颗炫纹并追加一道同属性魔法剑气。");
            }
            if (hasCard(TowerBattleCard.BM_SWING_EMPRESS)) {
                sb.append("\n职业神话·闪击碎霸：前冲").append(number(TowerBattleCard.BM_SWING_EMPRESS.value("dash")))
                    .append("格并以").append(number(TowerBattleCard.BM_SWING_EMPRESS.value("arc"))).append("°全周斩裂，物理伤害+")
                    .append(number(TowerBattleCard.BM_SWING_EMPRESS.value("damage"))).append("%，命中产")
                    .append(TowerBattleCard.BM_SWING_EMPRESS.count("chasers")).append("球。");
            }
            if (hasCard(TowerBattleCard.BM_SWING_ARCANUM)) {
                sb.append("\n职业神话·万象魔刃：范围+").append(number(TowerBattleCard.BM_SWING_ARCANUM.value("range")))
                    .append("%，最多消耗").append(TowerBattleCard.BM_SWING_ARCANUM.count("max"))
                    .append("球向前斩出推进").append(number(TowerBattleCard.BM_SWING_ARCANUM.value("distance")))
                    .append("格的同属性魔法月牙剑气（每道").append(number(TowerBattleCard.BM_SWING_ARCANUM.value("ratio")))
                    .append("%基准倍率，空球仍斩1道无属性魔刃）。");
            }
            appendMorph(sb, BattleMageSkills.FULL_SWING, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.CHASER_FUSION, c -> {
            int rank = c.parameters().rank();
            var p = c.parameters();
            Stats snap = currentStats();
            StringBuilder sb = new StringBuilder();
            sb.append("纯魔法炫纹技 · 消耗").append((int) Math.round(p.get("consumeCount", 2.0)))
                .append("颗炫纹（继承其元素并集）在前方引爆半径").append(number(p.get("radius", 4.2))).append("格融合魔爆。\n");
            sb.append("融合共鸣：命中后").append(number(p.get("fusionBuffDuration", 15.0)))
                .append("秒内物攻与魔攻+").append(number(BattleMageRules.fusionAttackBonus(rank, p, snap) * 100))
                .append("%、暴击+").append(number(p.get("fusionCritBonus", 0.06) * 100)).append("%。");
            if (TowerBattleCard.BM_FUSION_INSTANT.held(snap)) {
                sb.append("\n职业神话·炫纹簇：0硬直脱手在").append(number(TowerBattleCard.BM_FUSION_INSTANT.value("range")))
                    .append("格内锁定目标脚下瞬爆，范围+").append(number(TowerBattleCard.BM_FUSION_INSTANT.value("radius")))
                    .append("%、伤害+").append(number(TowerBattleCard.BM_FUSION_INSTANT.value("damage"))).append("%，空球亦可满威力触发。");
            }
            if (TowerBattleCard.BM_FUSION_OVERLOAD.held(snap)) {
                sb.append("\n职业神话·双生共鸣：共鸣持续").append(number(TowerBattleCard.BM_FUSION_OVERLOAD.value("duration")))
                    .append("秒且加成+").append(number(TowerBattleCard.BM_FUSION_OVERLOAD.value("bonus")))
                    .append("%，期间所有炫纹与炫纹系技能自动附带全四元素。");
            }
            appendMorph(sb, BattleMageSkills.CHASER_FUSION, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.CHASER_PRESS, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("纯魔法炫纹核爆 · 消耗当前队列全部炫纹（继承全部消耗炫纹元素并集，自动取最高属强）。\n");
            sb.append("基准半径").append(number(p.get("radius", 3.5))).append("格，每多耗1颗炫纹额外扩大")
                .append(number(p.get("radiusPerChaser", 0.45))).append("格并大幅暴涨威力，附带")
                .append(number(p.get("controlSeconds", 0.80))).append("秒震地控制。");
            if (p.get("pullRadius", 0.0) > 0) {
                sb.append("\n引爆前先吸附半径").append(number(p.get("pullRadius", 7.5))).append("格内普通敌人。");
            }
            if (hasCard(TowerBattleCard.BM_PRESS_COLLAPSE)) {
                sb.append("\n职业神话·引力坍缩：").append(number(TowerBattleCard.BM_PRESS_COLLAPSE.value("pull")))
                    .append("格聚怪、控制延长至").append(number(TowerBattleCard.BM_PRESS_COLLAPSE.value("control")))
                    .append("秒，每颗炫纹的额外伤害与范围加成提高").append(number(TowerBattleCard.BM_PRESS_COLLAPSE.value("bonus"))).append("%。");
            }
            appendMorph(sb, BattleMageSkills.CHASER_PRESS, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.PUNTO_WAVE, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("纯物理高速连刺 · 霸体向前刺出").append((int) Math.round(p.get("slashes", 12.0)))
                .append("段（射程").append(number(p.get("range", 5.5))).append("格，吃攻速加速，随玩家转头实时转向），每")
                .append((int) Math.round(p.get("chaserEveryHits", 3.0))).append("段自动产1球，末段半径")
                .append(number(p.get("finisherRadius", 5.5))).append("格终结横扫。\n");
            sb.append("连刺途中可随时穿插「炫纹发射」，再次按下技能键立即打出终结横扫，或用其他体术柔化／按跳跃或Esc彻底打断。");
            if (hasCard(TowerBattleCard.BM_PUNTO_ENDLESS)) {
                sb.append("\n职业神话·无尽雷连：段数扩至").append(TowerBattleCard.BM_PUNTO_ENDLESS.count("slashes"))
                    .append("段且持续同步延长、连刺伤害降低").append(number(TowerBattleCard.BM_PUNTO_ENDLESS.value("reduction")))
                    .append("%、终结伤害提高").append(number(TowerBattleCard.BM_PUNTO_ENDLESS.value("finisher")))
                    .append("%，可持续连刺至打满或主动取消。");
            }
            if (hasCard(TowerBattleCard.BM_PUNTO_CRESCENDO)) {
                sb.append("\n职业神话·千击终响：前置连刺每次命中使终结横扫伤害提高")
                    .append(number(TowerBattleCard.BM_PUNTO_CRESCENDO.value("perhit"))).append("%。");
            }
            appendMorph(sb, BattleMageSkills.PUNTO_WAVE, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.DRAGON_STRIKE, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("纯物理爆发大招 · ").append(chargeLine(p)).append("\n");
            sb.append("霸体极速穿刺").append(number(p.get("dashDistance", 9.0)))
                .append("格拖拽路径敌人，终点爆发半径").append(number(p.get("blastRadius", 5.0))).append("格流星冲击");
            if (p.get("dashCount", 1.0) >= 2.0) {
                sb.append("后折返再次穿刺并引发二次流星爆发");
            } else if (p.get("returnToOrigin", 1.0) >= 1.0) {
                sb.append("后闪回原点");
            } else {
                sb.append("并留在目标身侧续招");
            }
            sb.append("，命中产").append((int) Math.round(p.get("chaserGen", 2.0))).append("球。");
            if (hasCard(TowerBattleCard.BM_STRIKE_TWIN)) {
                sb.append("\n职业神话·双星折返：往返").append(TowerBattleCard.BM_STRIKE_TWIN.count("dashes"))
                    .append("次穿刺与终点爆炸（每次造成原伤害的").append(number(TowerBattleCard.BM_STRIKE_TWIN.value("ratio")))
                    .append("%），冷却增加").append(number(TowerBattleCard.BM_STRIKE_TWIN.value("cooldown"))).append("%。");
            }
            appendMorph(sb, BattleMageSkills.DRAGON_STRIKE, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.DRAGON_SPEAR, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            if (hasCard(TowerBattleCard.BM_SPEAR_CLEAVE)) {
                sb.append("纯物理大招（煌龙天临形态 · 冷却-").append(number(TowerBattleCard.BM_SPEAR_CLEAVE.value("cdr")))
                    .append("%） · 跃起0.4秒瞬发煌龙巨刃重劈斩地，原魔法段全额并入物理重劈，终爆半径")
                    .append(number(p.get("blastRadius", 6.5))).append("格，总伤害+")
                    .append(number(TowerBattleCard.BM_SPEAR_CLEAVE.value("damage"))).append("%。");
            } else {
                sb.append("物魔混合大招 · 物理突刺挑控（").append(number(p.get("range", 7.5))).append("格，控敌")
                    .append(number(p.get("holdSeconds", 1.4))).append("秒）＋连续引爆")
                    .append((int) Math.round(p.get("dragonChaserCount", 8.0))).append("枚全四元素魔法「龙之炫纹」＋半径")
                    .append(number(p.get("blastRadius", 6.5))).append("格物魔双段煌龙终爆。\n");
                sb.append("物理矛法吃突刺与物理终爆，魔法球法吃龙之炫纹与魔法终爆，双修流全段吃满！");
                if (hasCard(TowerBattleCard.BM_SPEAR_DETACH)) {
                    sb.append("\n职业神话·煌龙乱舞：0.4秒脱手原地倾泻").append(TowerBattleCard.BM_SPEAR_DETACH.count("balls"))
                        .append("枚满倍率龙之炫纹并终爆，范围扩大").append(number(TowerBattleCard.BM_SPEAR_DETACH.value("radius"))).append("%。");
                }
            }
            appendMorph(sb, BattleMageSkills.DRAGON_SPEAR, p);
            return sb.toString();
        });

        SkillDescriptions.register(BattleMageSkills.TEANA_TRANSFORMATION, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("觉醒技（物魔混合） · 化身太古战神「贝亚娜斗神」");
            if (p.get("transformSeconds", 30.0) > 0) {
                sb.append("持续").append(number(p.get("transformSeconds", 30.0))).append("秒，起手触发半径")
                    .append(number(p.get("auraRadius", 5.5))).append("格物魔双段金光爆气并获")
                    .append(number(p.get("teanaShieldRatio", 0.25) * 100)).append("%最大生命护盾。\n");
                sb.append("变身强化：攻速+").append(number(p.get("teanaAttackSpeed", 0.20) * 100))
                    .append("%、移速+").append(number(p.get("teanaMoveSpeed", 0.20) * 100))
                    .append("%、暴击+").append(number(p.get("teanaCritBonus", 0.12) * 100))
                    .append("%；全技能可用，普攻变黄金战矛连刺，炫纹升格全四元素「贝亚娜炫纹」（×")
                    .append(number(p.get("teanaChaserScale", 1.45))).append("），碎霸／落花掌升格「天地碎霸」「苍天击」（×")
                    .append(number(p.get("teanaSkillScale", 1.30))).append("）。\n");
                sb.append("二段终结：变身期间再按本技能键释放「使徒穿刺」高爆发物魔终结（短时无敌定身）。");
            } else {
                sb.append("：浓缩为瞬间无敌定身太古神枪贯穿与原初炫纹大爆（半径")
                    .append(number(p.get("finisherRadius", 8.5))).append("格，物魔双段）。");
            }
            appendMorph(sb, BattleMageSkills.TEANA_TRANSFORMATION, p);
            return sb.toString();
        });
    }

    static String dragonFangNote(SkillDescriptions.Context c) {
        if (!SkillsClient.current().progress().hasJob(JobCatalog.BATTLE_MAGE)) return "";
        StringBuilder sb = new StringBuilder("\n战斗法师特化：命中随机生成");
        sb.append((int) Math.round(c.parameters().get("chaserGen", 1.0))).append("颗炫纹，并开放体术柔化窗口。");
        if (hasCard(TowerBattleCard.BM_FANG_TWIN_SHRED)) {
            sb.append("\n职业神话·破甲双牙：高速").append(TowerBattleCard.BM_FANG_TWIN_SHRED.count("hits"))
                .append("连突刺且每段不衰减、每段产").append(TowerBattleCard.BM_FANG_TWIN_SHRED.count("chasers"))
                .append("球、冷却-").append(number(TowerBattleCard.BM_FANG_TWIN_SHRED.value("cdr")))
                .append("%，命中降双防").append(number(TowerBattleCard.BM_FANG_TWIN_SHRED.value("shred")))
                .append("%（").append(number(TowerBattleCard.BM_FANG_TWIN_SHRED.value("duration"))).append("秒）。");
        }
        if (hasCard(TowerBattleCard.BM_FANG_WAVE)) {
            sb.append("\n职业神话·炫纹牙刃：延伸至").append(number(TowerBattleCard.BM_FANG_WAVE.value("range")))
                .append("格宽幅穿透气刃、伤害+").append(number(TowerBattleCard.BM_FANG_WAVE.value("damage")))
                .append("%、每穿").append(TowerBattleCard.BM_FANG_WAVE.count("per")).append("敌额外产")
                .append(TowerBattleCard.BM_FANG_WAVE.count("chasers")).append("球，柔化下个技能伤害总增")
                .append(number(TowerBattleCard.BM_FANG_WAVE.value("cancel"))).append("%。");
        }
        appendMorph(sb, MageSkills.FANG, c.parameters());
        return sb.toString();
    }

    private static String chargeLine(SkillParameters.Snapshot p) {
        double base = p.get("chargeSeconds", 0.0);
        if (base <= 0) return "瞬发无蓄力。";
        double eff = BattleMageRules.effectiveChargeSeconds(base, SkillController.INSTANCE.snapshot());
        return "可瞬点即出或长按蓄力" + number(eff) + "秒（满蓄伤害×" + number(p.get("chargeDamageMultiplier", 1.30))
            + "、突进×" + number(p.get("chargeDashMultiplier", 1.50)) + "；起手扣蓝进冷却，中断不返还）。";
    }

    private static void appendMorph(StringBuilder sb, String skillId, SkillParameters.Snapshot p) {
        int mode = BattleMageRules.morphMode(p);
        if (mode > 0) {
            var preset = BattleMageMorphs.current().find(skillId, mode);
            if (preset != null) {
                sb.append("\n[形态 ").append(mode == 1 ? "A" : "B").append(" · ").append(preset.title()).append("] ")
                    .append(preset.summary());
            }
        }
    }
}
