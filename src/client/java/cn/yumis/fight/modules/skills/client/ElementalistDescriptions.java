package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

/** Dynamic tooltip descriptions for all 11 Elementalist skills, including morph presets, tower mythic cards and dyeing. */
final class ElementalistDescriptions {
    private ElementalistDescriptions() {}

    private static boolean hasCard(TowerBattleCard card) {
        return SkillsClient.current().ready() && card.held(SkillController.INSTANCE.snapshot());
    }

    static String jackBombNote(SkillDescriptions.Context c) {
        StringBuilder sb = new StringBuilder();
        if (hasCard(TowerBattleCard.ELEM_JACK_TRIPLE)) {
            sb.append("\n职业神话 · 三叉追踪南瓜：三叉发射").append(TowerBattleCard.ELEM_JACK_TRIPLE.count("count"))
                .append("颗追踪南瓜，每颗造成").append(number(TowerBattleCard.ELEM_JACK_TRIPLE.value("damage"))).append("%伤害。");
        }
        if (hasCard(TowerBattleCard.ELEM_JACK_SPLIT)) {
            sb.append("\n职业神话 · 分裂南瓜：爆炸后向周围分裂出").append(TowerBattleCard.ELEM_JACK_SPLIT.count("count"))
                .append("颗小型南瓜再次爆炸，每颗造成").append(number(TowerBattleCard.ELEM_JACK_SPLIT.value("damage"))).append("%伤害。");
        }
        if (hasCard(TowerBattleCard.ELEM_BURN_PRISM)) {
            sb.append("\n【圣灵万象】当前伤害已进化为四元素属性（圣灵色）。");
        }
        return sb.toString();
    }

    static void install() {
        SkillDescriptions.register(ElementalistSkills.MOVING_CAST, c ->
            "转职自动习得（1级上限，不消耗SP）。\n"
            + "主动技能咏唱与蓄力期间均可移动和转向。可蓄力技能（含杰克爆弹）先咏唱读条，持续按住则在咏唱结束后进入蓄力，松手立即释放（咏唱期间松手则咏唱结束直接释放）。\n"
            + "按下即扣除MP并进入冷却，满蓄力后最多保持2秒自动释放；咏唱或蓄力中按Esc或切换技能取消不退还冷却与MP。");

        SkillDescriptions.register(ElementalistSkills.ELEMENTAL_BURN, c -> {
            int rank = c.parameters().rank();
            var p = c.parameters();
            double perLamp = ElementalistRules.perLampMut(rank, p.get("perLampBonusScale", 1.0)) * 100.0;
            double allBonus = p.get("allLampsBonusMut", 0.06) * 100.0;
            double allElem = ElementalistRules.allLampsAllElem(rank, p.get("allLampsAllElem", 12.0));
            double mem = ElementalistRules.memorizeReduction(rank, p.get("memorizeBonus", 0.0)) * 100.0;
            double castSpd = ElementalistRules.castSpeedBonusPercent(rank, p.get("festivalSpeedScale", 1.0));
            double cdRec = ElementalistRules.cooldownRecoveryBonus(rank, p.get("festivalCdRecoveryAdd", 0.0)) * 100.0;
            double dur = p.get("lampDuration", 30.0);
            StringBuilder sb = new StringBuilder();
            sb.append("符文点灯：释放光／火／冰／暗固有属性技能点亮对应符文灯，持续").append(number(dur)).append("秒；觉醒技同时点亮4灯。\n");
            sb.append("点灯增伤：每盏灯使最终伤害提升").append(number(perLamp)).append("%（MUT）；4灯齐亮额外提升")
                .append(number(allBonus)).append("%且全属强+").append(number(allElem)).append("。\n");
            sb.append("魔法记忆：蓄力基础时间缩短").append(number(mem)).append("%（上限60%），最终蓄力不低于0.05秒。\n");
            sb.append("魔法秀常驻：施放速度+").append(number(castSpd)).append("%，冷却恢复速度+").append(number(cdRec)).append("%。\n");
            sb.append("点灯仅看技能固有属性，不受属性染色影响。");
            if (p.get("autoRefreshAllOnUpper", 0.0) >= 1.0) {
                sb.append("\n高阶技能释放时同步刷新所有已点亮符文灯时间。");
            }
            if (hasCard(TowerBattleCard.ELEM_BURN_FOCUS)) {
                sb.append("\n职业神话 · 元素集中：对应元素技能命中时使敌人该属性抗性降低")
                    .append(number(TowerBattleCard.ELEM_BURN_FOCUS.value("shred"))).append("点，持续")
                    .append(number(TowerBattleCard.ELEM_BURN_FOCUS.value("duration"))).append("秒。");
            }
            if (hasCard(TowerBattleCard.ELEM_BURN_PRISM)) {
                sb.append("\n职业神话 · 圣灵万象：所有技能变为四元素属性，施放任意元素技能同时点亮全部4灯，且特效进化为圣灵色；技能额外魔力消耗+")
                    .append(number(TowerBattleCard.ELEM_BURN_PRISM.value("mana"))).append("%，多元素转化异常积累按")
                    .append(number(TowerBattleCard.ELEM_BURN_PRISM.value("buildup"))).append("%分摊。");
            }
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.ICE_WALL, c -> {
            var p = c.parameters();
            var stats = SkillController.INSTANCE.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：冰。").append(chargeLine(c)).append("\n");
            int mode = ElementalistRules.effectiveMorphMode(ElementalistSkills.ICE_WALL, p, stats);
            if (mode == 1) {
                sb.append("向前推出").append(number(p.get("range", 10.0))).append("格寒冰弧波（宽")
                    .append(number(p.get("radius", 6.0))).append("格），沿途推挤并重创敌人。");
                if (hasCard(TowerBattleCard.ELEM_ICE_WALL_MOBILE)) {
                    sb.append("\n职业神话 · 随身冰壁：极寒推进改为连续推出")
                        .append(TowerBattleCard.ELEM_ICE_WALL_MOBILE.count("waves")).append("道冰浪，冻结异常积累+")
                        .append(number(TowerBattleCard.ELEM_ICE_WALL_MOBILE.value("buildup"))).append("%。");
                }
            } else {
                sb.append(hasCard(TowerBattleCard.ELEM_ICE_WALL_MOBILE) ? "围绕自身并随玩家移动形成半径" : "在脚下形成半径")
                    .append(number(p.get("radius", 3.5))).append("格冰墙，持续")
                    .append(number(p.get("duration", 6.0))).append("秒，展开时击退").append(number(p.get("knockbackDistance", 2.5)))
                    .append("格，结束或被击破时半径").append(number(p.get("blastRadius", 4.0))).append("格碎裂爆炸。\n");
                sb.append("站立冰墙内可抵挡总计").append(number(p.get("wallShieldMultiplier", 2.0)))
                    .append("倍最大生命的伤害；击破冰墙的单次溢出伤害也会被完全舍弃。");
                if (p.get("pulseDot", 0.0) > 0) {
                    sb.append("\n持续期间每").append(number(p.get("interval", 0.50))).append("秒向周围释放光属性雷电脉冲。");
                }
                if (hasCard(TowerBattleCard.ELEM_ICE_WALL_MOBILE)) {
                    sb.append("\n职业神话 · 随身冰壁：冰墙跟随自身移动，冻结异常积累+")
                        .append(number(TowerBattleCard.ELEM_ICE_WALL_MOBILE.value("buildup"))).append("%。");
                }
            }
            appendMorphAndDye(sb, ElementalistSkills.ICE_WALL, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.LIGHTNING_VORTEX, c -> {
            var p = c.parameters();
            var stats = SkillController.INSTANCE.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：光。").append(chargeLine(c)).append("\n");
            int mode = ElementalistRules.effectiveMorphMode(ElementalistSkills.LIGHTNING_VORTEX, p, stats);
            if (mode == 1) {
                sb.append("沿准星方向发射").append(number(p.get("range", 14.0))).append("格高速穿透电磁炮，终点引发范围电爆。");
            } else if (mode == 2) {
                sb.append("电球环绕自身持续").append(number(p.get("duration", 4.0))).append("秒，共")
                    .append((int) Math.round(p.get("slashes", 10.0))).append("段范围雷击并刷新光灯。");
            } else {
                sb.append("前方").append(number(p.get("centerOffset", 2.0))).append("格生成")
                    .append((int) Math.round(p.get("orbCount", 4.0))).append("颗环绕电球（满蓄力+1颗），半径")
                    .append(number(p.get("radius", 4.0))).append("格内高速雷击")
                    .append((int) Math.round(p.get("slashes", 3.0))).append("段。");
            }
            if (hasCard(TowerBattleCard.ELEM_VORTEX_CHAIN)) {
                sb.append("\n职业神话 · 连锁高压弧：感电异常积累+")
                    .append(number(TowerBattleCard.ELEM_VORTEX_CHAIN.value("buildup"))).append("%，冷却+")
                    .append(number(TowerBattleCard.ELEM_VORTEX_CHAIN.value("cd"))).append("%，总伤害+")
                    .append(number(TowerBattleCard.ELEM_VORTEX_CHAIN.value("bonus"))).append("%，命中向周围")
                    .append(number(TowerBattleCard.ELEM_VORTEX_CHAIN.value("range"))).append("格最多")
                    .append(TowerBattleCard.ELEM_VORTEX_CHAIN.count("targets")).append("名敌人弹射高压电弧（")
                    .append(number(TowerBattleCard.ELEM_VORTEX_CHAIN.value("damage"))).append("%伤害）。");
            }
            appendMorphAndDye(sb, ElementalistSkills.LIGHTNING_VORTEX, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.VOID_SPHERE, c -> {
            var p = c.parameters();
            var stats = SkillController.INSTANCE.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：暗。").append(chargeLine(c)).append("\n");
            int ticks = ElementalistRules.voidSphereTicks(p);
            int mode = ElementalistRules.effectiveMorphMode(ElementalistSkills.VOID_SPHERE, p, stats);
            if (mode == 1) {
                if (hasCard(TowerBattleCard.ELEM_VOID_CRYSTAL)) {
                    sb.append("前方定点凝结巨型排斥水晶，持续").append(number(p.get("duration", 4.0))).append("秒（约").append(ticks)
                        .append("段），无法手动提前引爆，持续结束时自动引发半径").append(number(p.get("blastRadius", 4.8))).append("格坍缩核爆。");
                } else {
                    sb.append("前方定点生成虚无水晶，持续").append(number(p.get("duration", 4.0))).append("秒（约").append(ticks)
                        .append("段），再次按键可立即引爆并折算100%剩余伤害。");
                }
            } else {
                sb.append("发射慢速前进（").append(number(p.get("projectileSpeed", 3.0))).append("格/秒）的虚无球，持续")
                    .append(number(p.get("duration", 4.0))).append("秒（约").append(ticks).append("段），遇墙停驻旋转并推挤非Boss敌人。");
            }
            if (p.get("maxCharges", 1.0) > 1.0) {
                sb.append("\n拥有").append((int) Math.round(p.get("maxCharges", 2.0))).append("层充能，可连续施放并存。");
            }
            appendMorphAndDye(sb, ElementalistSkills.VOID_SPHERE, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.FLAME_PILLAR, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：火 · 地面瞄准（最远").append(number(p.get("range", 14.0))).append("格）。").append(chargeLine(c)).append("\n");
            int pillars = Math.max(1, (int) Math.round(p.get("pillarCount", 1.0)));
            sb.append("延迟").append(number(p.get("impactDelay", 0.20))).append("秒爆发");
            if (pillars > 1) sb.append(pillars).append("道连环");
            sb.append("火柱（半径").append(number(p.get("radius", 3.0))).append("格），附加")
                .append(number(p.get("controlSeconds", 0.60))).append("秒控制（精英减半、Boss免疫）。");
            if (hasCard(TowerBattleCard.ELEM_FLAME_VOLCANO)) {
                sb.append("\n职业神话 · 虚无火山：首根火柱落点生成持续")
                    .append(number(TowerBattleCard.ELEM_FLAME_VOLCANO.value("duration"))).append("秒的暗火虚无火山口，每")
                    .append(number(TowerBattleCard.ELEM_FLAME_VOLCANO.value("interval"))).append("秒喷射火暗双属性熔岩爆弹（")
                    .append(number(TowerBattleCard.ELEM_FLAME_VOLCANO.value("damage"))).append("%伤害）并微弱吸怪。");
            }
            appendMorphAndDye(sb, ElementalistSkills.FLAME_PILLAR, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.HALLOWEEN_BUSTER, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：火 · 地面瞄准（最远").append(number(p.get("range", 16.0))).append("格）。").append(chargeLine(c)).append("\n");
            int meteors = Math.max(1, (int) Math.round(p.get("meteorCount", 1.0)));
            sb.append("召唤").append(meteors > 1 ? meteors + "颗" : "巨型").append("烈焰南瓜坠落，砸中造成半径")
                .append(number(p.get("radius", 4.5))).append("格核心爆发");
            if (p.get("finisher", 0.30) > 0) {
                sb.append("与半径").append(number(p.get("shockwaveRadius", 7.0))).append("格扩散冲击波");
            }
            if (p.get("emberDuration", 0.0) > 0 && (p.get("emberDot", 0.0) > 0 || hasCard(TowerBattleCard.ELEM_BUSTER_TRIPLE))) {
                sb.append("，并在地面留下").append(number(p.get("emberDuration", 3.0))).append("秒火海灼烧");
            }
            sb.append("。");
            appendMorphAndDye(sb, ElementalistSkills.HALLOWEEN_BUSTER, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.ARCTIC_FEAST, c -> {
            var p = c.parameters();
            var stats = SkillController.INSTANCE.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：冰 · 脱手施放。").append(chargeLine(c)).append("\n");
            int mode = ElementalistRules.effectiveMorphMode(ElementalistSkills.ARCTIC_FEAST, p, stats);
            if (mode == 1) {
                sb.append("凝聚巨型冰锥瞬间贯穿并碎裂，半径").append(number(p.get("radius", 5.5))).append("格内爆发全部伤害并冻结")
                    .append(number(p.get("freezeSeconds", 1.5))).append("秒。");
            } else {
                sb.append("在前方展开半径").append(number(p.get("radius", 5.5))).append("格极冰法阵，持续")
                    .append(number(p.get("duration", 2.0))).append("秒，阵内随机升起").append((int) Math.round(p.get("slashes", 8.0)))
                    .append("波冰柱，最后碎裂并附带").append(number(p.get("freezeSeconds", 1.5))).append("秒冰冻控制。");
            }
            if (p.get("pullRadius", 0.0) > 0) {
                sb.append("\n暴风眼附加半径").append(number(p.get("pullRadius", 7.5))).append("格向心吸附。");
            }
            if (hasCard(TowerBattleCard.ELEM_ARCTIC_VORTEX)) {
                sb.append("\n职业神话 · 极寒暴风眼：冻结异常积累+")
                    .append(number(TowerBattleCard.ELEM_ARCTIC_VORTEX.value("buildup"))).append("%，终结冰爆命中冻结敌人额外造成")
                    .append(number(TowerBattleCard.ELEM_ARCTIC_VORTEX.value("shatter"))).append("%破冰伤害。");
            }
            appendMorphAndDye(sb, ElementalistSkills.ARCTIC_FEAST, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.BLACK_HOLE, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：暗 · 地面瞄准（最远").append(number(p.get("range", 16.0))).append("格）。").append(chargeLine(c)).append("\n");
            sb.append("生成持续").append(number(p.get("duration", 3.0))).append("秒的引力奇点，吸附半径")
                .append(number(p.get("pullRadius", 7.5))).append("格内敌人并造成")
                .append((int) Math.round(p.get("slashes", 10.0))).append("段核心压迫与半径")
                .append(number(p.get("blastRadius", 6.0))).append("格坍缩终结。\n");
            if (p.get("manualDetonate", 1.0) >= 1.0) {
                sb.append("再按本技能键可立即坍缩引爆（0消耗，折算")
                    .append(number(p.get("earlyDetonateKeepRatio", 0.40) * 100.0)).append("%剩余持续伤害）。");
            }
            if (p.get("subHoleDuration", 0.0) > 0) {
                sb.append("\n坍缩后残留").append(number(p.get("subHoleDuration", 2.0))).append("秒引力子黑洞继续聚怪。");
            }
            if (p.get("volcanoDot", 0.0) > 0) {
                sb.append("\n黑洞存在期间同步喷发火属性冥焰熔岩。");
            }
            if (hasCard(TowerBattleCard.ELEM_BH_RESONANCE)) {
                sb.append("\n职业神话 · 奇点共振：黑洞存在期间每次释放其他元素技能，立即触发")
                    .append(number(TowerBattleCard.ELEM_BH_RESONANCE.value("damage"))).append("%坍缩终爆伤害的共振冲击。");
            }
            appendMorphAndDye(sb, ElementalistSkills.BLACK_HOLE, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.LIGHTNING_STRIKE, c -> {
            var p = c.parameters();
            var stats = SkillController.INSTANCE.snapshot();
            StringBuilder sb = new StringBuilder();
            sb.append("固有属性：光。").append(chargeLine(c)).append("\n");
            boolean channeling = p.get("channeling", 1.0) >= 0.5;
            int mode = ElementalistRules.effectiveMorphMode(ElementalistSkills.LIGHTNING_STRIKE, p, stats);
            if (!channeling && mode == 1) {
                sb.append("合并5道天雷之力，在瞄准落点降下半径").append(number(p.get("radius", 5.25))).append("格的毁灭性单发天雷。");
            } else if (!channeling) {
                sb.append("召唤脱手雷云在空中自动搜敌，持续").append(number(p.get("duration", 3.5))).append("秒降下")
                    .append((int) Math.round(p.get("slashes", 6.0))).append("道天雷。");
            } else {
                sb.append("原地引导").append(number(p.get("duration", 1.75))).append("秒，落雷法阵随准星高速移动并降下")
                    .append((int) Math.round(p.get("slashes", 5.0))).append("道天雷（半径").append(number(p.get("radius", 3.5)))
                    .append("格，命中内拉").append(number(p.get("pullOnHit", 1.8))).append("格）。\n");
                sb.append("引导期间锁定移动、获得").append(number(p.get("channelProtection", 0.65) * 100.0))
                    .append("%承伤减免；按Esc或释放其他技能可主动中断。");
            }
            appendMorphAndDye(sb, ElementalistSkills.LIGHTNING_STRIKE, p);
            return sb.toString();
        });

        SkillDescriptions.register(ElementalistSkills.ASTRAL_STORM, c -> {
            var p = c.parameters();
            StringBuilder sb = new StringBuilder();
            sb.append("觉醒技。").append(chargeLine(c)).append("施法完成时点亮全部4盏元素灯。\n");
            boolean channeling = p.get("channeling", 1.0) >= 0.5;
            if (channeling) {
                sb.append("无敌浮空").append(number(p.get("duration", 4.0))).append("秒并展开半径")
                    .append(number(p.get("fieldRadius", 10.0))).append("格星界结界，降下")
                    .append((int) Math.round(p.get("slashes", 16.0))).append("颗元素陨星（按当前四属强权重分配属性），终结引爆四属性共存的混沌核心。");
            } else {
                sb.append("短暂施法后脱手展开半径").append(number(p.get("fieldRadius", 10.0))).append("格星界天幕，持续")
                    .append(number(p.get("duration", 4.0))).append("秒自动降下")
                    .append((int) Math.round(p.get("slashes", 16.0))).append("颗元素陨星与混沌核心终结，期间自由行动。");
            }
            appendMorphAndDye(sb, ElementalistSkills.ASTRAL_STORM, p);
            return sb.toString();
        });
    }

    static String inheritedCastLine(SkillDescriptions.Context c) {
        return ElementalistRules.usesCastTime(c.parameters().skill(), SkillsClient.current().progress()) ? castLine(c) + "\n" : "";
    }

    private static String castLine(SkillDescriptions.Context c) {
        return "咏唱需" + number(ElementalistRules.effectiveCastSeconds(c.parameters().skill(), c.castSpeed())) + "秒。";
    }

    static String chargeLine(SkillDescriptions.Context c) {
        var p = c.parameters();
        double base = p.get("chargeSeconds", 0.0);
        if (base <= 0.0) return castLine(c);
        var stats = SkillController.INSTANCE.snapshot();
        if (ElementalistRules.isInstantFullCharge(p.skill(), stats)) {
            return "咏唱" + number(ElementalistRules.effectiveCastSeconds(p.skill(), c.castSpeed()))
                + "秒后无需蓄力，直接按满蓄力效果释放。";
        }
        double eff = ElementalistRules.effectiveChargeSeconds(base, stats);
        int minPct = (int) Math.round(p.get("minChargeRatio", 0.75) * 100.0);
        int maxPct = (int) Math.round(p.get("chargeDamageMultiplier", 1.25) * 100.0);
        int scalePct = (int) Math.round(p.get("chargeScaleMultiplier", 1.25) * 100.0);
        String range = ElementalistSkills.VOID_SPHERE.equals(p.skill().id) ? "球体随蓄力增大" : "满蓄力范围" + scalePct + "%";
        return "先咏唱" + number(ElementalistRules.effectiveCastSeconds(p.skill(), c.castSpeed()))
            + "秒，按住可继续移动蓄力（当前满蓄力需" + number(eff) + "秒），松手立即释放。"
            + "蓄力伤害" + minPct + "%～" + maxPct + "%，" + range + "（面板预览按满蓄力计算）。";
    }

    private static void appendMorphAndDye(StringBuilder sb, String skillId, SkillParameters.Snapshot p) {
        var stats = SkillController.INSTANCE.snapshot();
        int mode = ElementalistRules.effectiveMorphMode(skillId, p, stats);
        if (mode > 0) {
            var preset = ElementalistMorphs.current().find(skillId, mode);
            if (preset != null) {
                sb.append("\n【形态").append(mode == 1 ? "A" : "B").append(" · ").append(preset.title()).append("】");
                if (ElementalistSkills.VOID_SPHERE.equals(skillId) && hasCard(TowerBattleCard.ELEM_VOID_CRYSTAL)) {
                    sb.append("定点凝结巨型排斥水晶，不可手动提前引爆，持续结束自动触发范围坍缩核爆。");
                } else {
                    sb.append(preset.summary());
                }
            }
        }
        if (ElementalistRules.isPrismActive(stats)) {
            sb.append("\n【圣灵万象】当前技能已进化为四元素属性共存（圣灵色）。");
            return;
        }
        int innate = ElementalistSkills.innateElement(skillId);
        int eff = ElementalistRules.effectiveElement(p, stats, innate);
        if (eff >= 0 && eff != innate) {
            sb.append("\n【属性染色】当前伤害已转为").append(ElementalistRules.elementName(eff)).append("属性（点灯仍按固有属性）。");
        }
    }
}
