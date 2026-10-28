package cn.yumis.fight.modules.affixes.api;
import cn.yumis.fight.modules.attributes.api.Stat;
/** revision只描述效果版本；名称与投放状态可独立更新。数值区间使用实际计算单位。 */
public record AffixDefinition(String id,String name,String operation,String group,Stat stat,int element,
        long revision,boolean retired,Double minimum,Double maximum,Double upperMinimum,Double upperMaximum,Double balanceScale,
        Integer fixedRank,Double parameterMultiplier,String description,Double fixedValue) {
    public AffixDefinition(String id,String name,String operation,String group,Stat stat,int element){this(id,name,operation,group,stat,element,0,false,null,null,null,null,null,null,null,null,null);}
    public AffixDefinition(String id,String name,String operation,String group,Stat stat,int element,long revision,boolean retired,Double minimum,Double maximum,Double upperMinimum,Double upperMaximum,Double balanceScale){this(id,name,operation,group,stat,element,revision,retired,minimum,maximum,upperMinimum,upperMaximum,balanceScale,null,null,null,null);}
    public AffixDefinition(String id,String name,String operation,String group,Stat stat,int element,long revision,boolean retired,Double minimum,Double maximum,Double upperMinimum,Double upperMaximum,Double balanceScale,Integer fixedRank,Double parameterMultiplier,String description){this(id,name,operation,group,stat,element,revision,retired,minimum,maximum,upperMinimum,upperMaximum,balanceScale,fixedRank,parameterMultiplier,description,null);}
    public AffixDefinition {
        if(id==null||id.isBlank()||name==null||operation==null||group==null||revision<0)throw new IllegalArgumentException("Invalid affix definition");
        if(fixedValue!=null&&!Double.isFinite(fixedValue))throw new IllegalArgumentException("Invalid fixed affix value: "+id);
        pair(minimum,maximum);pair(upperMinimum,upperMaximum);
        if(java.util.Set.of("FLAT","PERCENT","CHARACTER_PERCENT").contains(operation)&&stat==null)throw new IllegalArgumentException("Missing affix stat: "+id);
        if(operation.equals("EXTRA")&&(element<0||element>=4))throw new IllegalArgumentException("Invalid affix element: "+id);
        if(upperMinimum!=null&&(!operation.equals("EXTRA")||minimum==null||upperMinimum<minimum||upperMaximum<maximum))throw new IllegalArgumentException("Invalid extra damage interval: "+id);
        if(balanceScale!=null&&(!Double.isFinite(balanceScale)||balanceScale<=0))throw new IllegalArgumentException("Invalid balance scale: "+id);
        if(fixedRank!=null&&(!operation.equals("SKILL_FAMILY")||fixedRank<0))throw new IllegalArgumentException("Invalid fixed skill rank: "+id);
        if(parameterMultiplier!=null&&(!operation.equals("SKILL_PARAMETER")||!Double.isFinite(parameterMultiplier)||parameterMultiplier<0))throw new IllegalArgumentException("Invalid skill parameter multiplier: "+id);
    }
    private static void pair(Double low,Double high){if((low==null)!=(high==null)||low!=null&&(!Double.isFinite(low)||!Double.isFinite(high)||low>high))throw new IllegalArgumentException("Invalid affix interval");}
    public double scale(){return balanceScale==null?1:balanceScale;}
    public boolean active(){return !retired;}
    public AffixDefinition retire(){return new AffixDefinition(id,name,operation,group,stat,element,revision,true,minimum,maximum,upperMinimum,upperMaximum,balanceScale,fixedRank,parameterMultiplier,description,fixedValue);}
    public String effectKey(){return operation+"/"+stat+"/"+element+(operation.equals("EQUIPMENT_RULE")?"/"+id.substring(id.indexOf('/')+1):"");}
    public boolean sameEffect(AffixDefinition other){return java.util.Objects.equals(operation,other.operation)&&stat==other.stat&&element==other.element&&java.util.Objects.equals(minimum,other.minimum)&&java.util.Objects.equals(maximum,other.maximum)&&java.util.Objects.equals(upperMinimum,other.upperMinimum)&&java.util.Objects.equals(upperMaximum,other.upperMaximum)&&Double.compare(scale(),other.scale())==0&&java.util.Objects.equals(fixedRank,other.fixedRank)&&java.util.Objects.equals(parameterMultiplier,other.parameterMultiplier)&&java.util.Objects.equals(fixedValue,other.fixedValue);}
}

