package cn.yumis.fight.modules.affixes.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
/** 解析有效效果，不写物品或存档；随机分位与固定参数分开处理。 */
public final class AffixResolver {
    private AffixResolver(){}
    public static String definitionId(Affix a){return a.operation.equals("MAGIC")?"magic/"+a.family.toLowerCase(java.util.Locale.ROOT):a.id;}
    public static void anchor(Affix a,String path,AffixDefinition definition,long revision,double effectiveScale){
        if(a.base)return;
        if(a.instanceId==null||a.instanceId.isEmpty())a.instanceId=UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8)).toString();
        Affix.Anchor origin=new Affix.Anchor();origin.operation=a.operation;origin.stat=a.stat;origin.element=a.element;
        origin.value=a.value/a.corruptionMultiplier;origin.maximum=a.maximum/a.corruptionMultiplier;origin.baseline=a.baseline;origin.baselineMaximum=a.baselineMaximum;origin.roll=a.roll;
        origin.revision=revision;origin.rank=a.rank;origin.definitionScale=effectiveScale;
        if(definition!=null&&definition.minimum()!=null){origin.quality=quality(a.value/a.corruptionMultiplier/effectiveScale,definition.minimum(),definition.maximum());}
        else if(a.operation.equals("MAGIC_AMPLIFICATION"))origin.quality=quality(a.value/a.corruptionMultiplier,.25,.5);
        else if(!a.fixed&&a.stat.equals("ATTACK_SPEED"))origin.quality=quality(origin.value,a.baseline,a.baselineMaximum);
        else if(!a.fixed&&(a.operation.equals("FLAT")||a.operation.equals("PERCENT")||a.operation.equals("ALL_RESIST_FLAT")||a.operation.equals("EXTRA"))){
            origin.quality=quality(origin.value,a.baseline*.8,a.baseline*1.2);
            if(a.operation.equals("EXTRA")){Double upper=quality(origin.maximum,a.baselineMaximum*.8,a.baselineMaximum*1.2);if(upper==null||origin.quality==null||Math.abs(upper-origin.quality)>1e-8)origin.quality=null;}
        }
        if(definition!=null&&revision==definition.revision()&&definition.minimum()!=null&&origin.quality!=null)
            origin.quality=quality(a.value/a.corruptionMultiplier/effectiveScale,definition.minimum(),definition.maximum());
        a.origin=origin;a.schemaVersion=2;
        a.syncStatus=origin.quality==null&&!a.fixed&&!a.operation.equals("MAGIC")?"unresolved_snapshot":"";
    }
    private static Double quality(double value,double min,double max){if(!Double.isFinite(value)||!Double.isFinite(min)||!Double.isFinite(max)||max<=min)return null;double q=(value-min)/(max-min);return q>=-1e-9&&q<=1+1e-9?Math.clamp(q,0,1):null;}
    public static void resolve(Affix a,AffixDefinition definition,String path,double effectiveScale){
        if(a.base)return;
        if(a.origin==null||a.schemaVersion!=2)throw new IllegalArgumentException("不支持的词条存档格式: "+a.id);
        if(definition==null)return;
        var o=a.origin;
        if(definition.revision()<o.revision){a.syncStatus="newer_instance";return;}
        if(definition.revision()==o.revision)return;
        boolean bylleFinalAdd=definition.id().equals("unique/bylle_physical_flat")&&"FLAT".equals(o.operation)&&"PHYSICAL_ATTACK".equals(o.stat)&&definition.operation().equals("ITEM_FINAL_ADD")&&definition.stat()==cn.yumis.fight.modules.attributes.api.Stat.PHYSICAL_ATTACK;
        if(!definition.operation().equals(o.operation)&&!bylleFinalAdd){a.syncStatus="effect_conflict";return;}
        // Unknown random origin cannot be reconstructed from display values or guessed from the default roll.
        if(o.quality==null&&!a.fixed&&!a.operation.equals("MAGIC")&&!a.operation.equals("SKILL_PARAMETER")&&!a.operation.equals("SKILL_FAMILY")){a.syncStatus="unresolved_snapshot";return;}
        double ratio=effectiveScale/o.definitionScale;
        double value=o.value*ratio,maximum=o.maximum*ratio,baseline=o.baseline*ratio,baselineMaximum=o.baselineMaximum*ratio;
        if(o.quality!=null&&definition.minimum()!=null){
            value=(definition.minimum()+(definition.maximum()-definition.minimum())*o.quality)*effectiveScale;
            if(a.stat.equals("ATTACK_SPEED")){baseline=definition.minimum()*effectiveScale;baselineMaximum=definition.maximum()*effectiveScale;}
            else if(!a.fixed){baseline=value/(.8+.4*o.quality);}
            if(definition.upperMinimum()!=null){maximum=(definition.upperMinimum()+(definition.upperMaximum()-definition.upperMinimum())*o.quality)*effectiveScale;baselineMaximum=maximum/(.8+.4*o.quality);}
        }
        if(definition.fixedValue()!=null)value=definition.fixedValue()*effectiveScale;
        if(!Double.isFinite(value)||!Double.isFinite(maximum)||!Double.isFinite(baseline)||!Double.isFinite(baselineMaximum))throw new IllegalArgumentException("Non-finite resolved affix: "+a.id);
        a.value=value*a.corruptionMultiplier;a.maximum=maximum*a.corruptionMultiplier;a.baseline=baseline;a.baselineMaximum=baselineMaximum;
        if(bylleFinalAdd)a.operation=definition.operation();
        if(a.operation.equals("SKILL_FAMILY"))a.rank=definition.fixedRank()==null?o.rank:definition.fixedRank();
        if(a.operation.equals("SKILL_PARAMETER"))a.roll=definition.parameterMultiplier()==null?o.roll:definition.parameterMultiplier();
        a.stat=definition.stat()==null?o.stat:definition.stat().name();a.element=definition.element();
        a.syncStatus=definition.retired()?"retired":"";
    }
}

