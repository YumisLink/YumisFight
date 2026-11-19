package cn.yumis.fight.modules.talents.api;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** 天赋盘规则包：六轴十二线一百二十节点与出生点，随应用规则整包发布。数值占位 0，经批准后热更填入。 */
public final class TalentCatalog {
    public static final int AXIS_COUNT=6,LINE_COUNT=12,POSITION_COUNT=10;
    public record Condition(String kind,double threshold,String tag){}
    public record Effect(String channel,String key,double value,Condition condition){}
    public record Node(String id,String line,int position,String tier,String title,List<Effect> effects,boolean retired){}
    public record Line(String id,String axis,String title){}
    public record Axis(String id,String title){}
    public record Birth(String id,String axis,String title,List<Effect> effects){}
    private static final Set<String> CHANNELS=Set.of("mut","add","total","final","crit","taken","conversion","param","count","killHeal");
    private static final Set<String> PARAMS=Set.of("summonInherit/HEALTH","summonInherit/DEFENSE","summonInherit/RESIST","summonInherit/SHIELD","summonDamage","summonDuration","summonAggro","summonElement","summonMana","killWindow");
    private List<Axis> axes=List.of();
    private List<Line> lines=List.of();
    private List<Node> nodes=List.of();
    private List<Birth> births=List.of();
    private transient Map<String,Axis> axisById=Map.of();
    private transient Map<String,Line> lineById=Map.of();
    private transient Map<String,Node> nodeById=Map.of();
    private transient Map<String,Birth> birthByAxis=Map.of();
    private transient Map<String,List<Node>> byLine=Map.of();
    private static Supplier<TalentCatalog> source=TalentCatalog::defaults;
    private static final class Defaults{
        private static final String JSON=read();
        private static String read(){
            try(var input=TalentCatalog.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/talents.json")){
                if(input==null)throw new IllegalStateException("缺少默认天赋盘配置");
                return new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    public static TalentCatalog defaults(){return parse(Defaults.JSON);}
    public static void source(Supplier<TalentCatalog> value){source=Objects.requireNonNull(value);}
    public static TalentCatalog current(){return source.get();}
    public static TalentCatalog parse(String json){
        var result=new Gson().fromJson(json,TalentCatalog.class);
        if(result==null||result.axes==null||result.lines==null||result.nodes==null||result.births==null)throw new IllegalArgumentException("天赋盘定义缺失");
        validate(result);
        return result;
    }
    public List<Axis> axes(){return axes;}
    public List<Line> lines(){return lines;}
    public List<Node> nodes(){return nodes;}
    public Axis axis(String id){return axisById.get(id);}
    public Line line(String id){return lineById.get(id);}
    public Birth birth(String axis){return birthByAxis.get(axis);}
    public Node find(String id){return nodeById.get(id);}
    public Node node(String id){var node=find(id);if(node==null)throw new IllegalArgumentException("天赋节点不存在："+id);return node;}
    public List<Node> lineNodes(String lineId){return byLine.getOrDefault(lineId,List.of());}
    public List<Line> linesOf(String axisId){
        var result=new ArrayList<Line>();for(Line line:lines)if(line.axis().equals(axisId))result.add(line);return result;
    }
    /** 环序＝线表顺序；同位环邻＝环序前后两线同位置节点。 */
    public List<Node> ringNeighbors(String nodeId){
        var node=node(nodeId);
        if(node.position()!=4&&node.position()!=10)return List.of();
        int index=lineIndex(node.line());
        var result=new ArrayList<Node>(2);
        for(int neighbor:new int[]{(index+LINE_COUNT-1)%LINE_COUNT,(index+1)%LINE_COUNT}){
            var line=lines.get(neighbor);
            for(Node candidate:byLine.get(line.id()))if(candidate.position()==node.position())result.add(candidate);
        }
        return result;
    }
    public static String tierOf(int position){return position<=3?"basic":position==4?"capstone":position<10?"deep":"apex";}
    private int lineIndex(String lineId){
        for(int index=0;index<lines.size();index++)if(lines.get(index).id().equals(lineId))return index;
        throw new IllegalArgumentException("天赋线不存在："+lineId);
    }
    private static void validate(TalentCatalog result){
        if(result.axes.size()!=AXIS_COUNT)throw new IllegalArgumentException("天赋轴数量无效");
        var axisIds=new HashSet<String>();
        for(Axis axis:result.axes){
            require(axis!=null&&axis.id()!=null&&!axis.id().isBlank()&&axis.title()!=null&&!axis.title().isBlank(),"天赋轴无效");
            if(!axisIds.add(axis.id()))throw new IllegalArgumentException("天赋轴重复："+axis.id());
        }
        if(result.lines.size()!=LINE_COUNT)throw new IllegalArgumentException("天赋线数量无效");
        var lineIds=new HashSet<String>();
        for(Line line:result.lines){
            require(line!=null&&line.id()!=null&&!line.id().isBlank()&&line.title()!=null&&!line.title().isBlank(),"天赋线无效");
            if(!axisIds.contains(line.axis()))throw new IllegalArgumentException("天赋线所属轴不存在："+line.id());
            if(!lineIds.add(line.id()))throw new IllegalArgumentException("天赋线重复："+line.id());
        }
        if(result.nodes.size()!=LINE_COUNT*POSITION_COUNT)throw new IllegalArgumentException("天赋节点数量无效");
        var nodeById=new LinkedHashMap<String,Node>();
        var byLine=new LinkedHashMap<String,Map<Integer,Node>>();
        int[] tiers=new int[4];
        for(Node node:result.nodes){
            require(node!=null&&node.id()!=null&&!node.id().isBlank()&&node.title()!=null&&!node.title().isBlank(),"天赋节点无效");
            if(!lineIds.contains(node.line()))throw new IllegalArgumentException("天赋节点所属线不存在："+node.id());
            if(node.position()<1||node.position()>POSITION_COUNT)throw new IllegalArgumentException("天赋节点位置越位："+node.id());
            if(!node.id().equals(node.line()+"_"+node.position()))throw new IllegalArgumentException("天赋节点 ID 与位置不符："+node.id());
            if(!tierOf(node.position()).equals(node.tier()))throw new IllegalArgumentException("天赋节点档位与位置不符："+node.id());
            if(nodeById.putIfAbsent(node.id(),node)!=null)throw new IllegalArgumentException("天赋节点重复："+node.id());
            if(node.effects()==null||node.effects().isEmpty()||node.effects().size()>6)throw new IllegalArgumentException("天赋节点效果行数无效："+node.id());
            for(Effect effect:node.effects())validate(node.id(),effect);
            byLine.computeIfAbsent(node.line(),ignored->new TreeMap<Integer,Node>()).put(node.position(),node);
            tiers[node.position()<=3?0:node.position()==4?1:node.position()<10?2:3]++;
        }
        if(tiers[0]!=36||tiers[1]!=12||tiers[2]!=60||tiers[3]!=12)throw new IllegalArgumentException("天赋档位分布无效");
        for(var line:result.lines){
            var positions=byLine.get(line.id());
            if(positions==null||positions.size()!=POSITION_COUNT)throw new IllegalArgumentException("天赋线节点不全："+line.id());
        }
        if(result.births.size()!=AXIS_COUNT)throw new IllegalArgumentException("出生点数量无效");
        var birthByAxis=new LinkedHashMap<String,Birth>();
        for(Birth birth:result.births){
            require(birth!=null&&birth.axis()!=null&&birth.title()!=null&&!birth.title().isBlank(),"出生点无效");
            if(!axisIds.contains(birth.axis()))throw new IllegalArgumentException("出生点所属轴不存在："+birth.axis());
            if(!birth.id().equals("origin_"+birth.axis()))throw new IllegalArgumentException("出生点 ID 无效："+birth.id());
            if(birth.effects()==null||birth.effects().isEmpty())throw new IllegalArgumentException("出生点缺少效果："+birth.id());
            for(Effect effect:birth.effects())validate(birth.id(),effect);
            if(birthByAxis.putIfAbsent(birth.axis(),birth)!=null)throw new IllegalArgumentException("出生点重复："+birth.axis());
        }
        var birthByAxisFixed=Map.copyOf(birthByAxis);
        var byLineFixed=new LinkedHashMap<String,List<Node>>();
        for(var entry:byLine.entrySet())byLineFixed.put(entry.getKey(),List.copyOf(entry.getValue().values()));
        result.axisById=Map.copyOf(axisMap(result.axes));result.lineById=Map.copyOf(lineMap(result.lines));
        result.nodeById=Map.copyOf(nodeById);result.birthByAxis=birthByAxisFixed;result.byLine=byLineFixed;
        result.axes=List.copyOf(result.axes);result.lines=List.copyOf(result.lines);
        result.nodes=List.copyOf(nodeById.values());result.births=List.copyOf(result.births);
    }
    private static Map<String,Axis> axisMap(List<Axis> axes){
        var result=new LinkedHashMap<String,Axis>();for(Axis axis:axes)result.put(axis.id(),axis);return result;
    }
    private static Map<String,Line> lineMap(List<Line> lines){
        var result=new LinkedHashMap<String,Line>();for(Line line:lines)result.put(line.id(),line);return result;
    }
    private static void validate(String id,Effect effect){
        if(effect==null||effect.channel()==null||!CHANNELS.contains(effect.channel()))throw new IllegalArgumentException("天赋效果通道无效："+id);
        if(!Double.isFinite(effect.value()))throw new IllegalArgumentException("天赋效果数值无效："+id);
        switch(effect.channel()){
            case "mut","add","total"->statKey(id,effect.key());
            case "conversion"->conversionKey(id,effect.key());
            case "param"->{
                if(!PARAMS.contains(effect.key()))throw new IllegalArgumentException("天赋参数键未登记："+id+"/"+effect.key());
                if(effect.key().equals("summonElement")&&(effect.value()<-1||effect.value()>3||effect.value()!=Math.floor(effect.value())))throw new IllegalArgumentException("召唤元素指定无效："+id);
            }
            case "count"->{if(!effect.key().equals("line")&&!effect.key().equals("capstone"))throw new IllegalArgumentException("天赋计数键无效："+id);}
            case "killHeal"->{if(!effect.key().isEmpty()&&!effect.key().equals("summon"))throw new IllegalArgumentException("击杀回复键无效："+id);}
            default->{}
        }
        var condition=effect.condition();
        if(condition==null)return;
        if(condition.kind()==null||!TalentConditions.KINDS.contains(condition.kind()))throw new IllegalArgumentException("天赋条件标签无效："+id);
        if(!Double.isFinite(condition.threshold())||condition.threshold()<0)throw new IllegalArgumentException("天赋条件阈值无效："+id);
        boolean needsAilment=condition.kind().equals("targetAilment");
        if(needsAilment&&(condition.tag()==null||!(TalentConditions.AILMENTS.contains(condition.tag())||condition.tag().equals(TalentConditions.ANY_AILMENT))))throw new IllegalArgumentException("天赋条件异常标签无效："+id);
        if(!needsAilment&&condition.tag()!=null&&!TalentConditions.AILMENTS.contains(condition.tag()))throw new IllegalArgumentException("天赋条件异常标签无效："+id);
    }
    private static void statKey(String id,String key){
        try{Stat.valueOf(key);}catch(RuntimeException failure){throw new IllegalArgumentException("天赋属性词条无效："+id+"/"+key);}
    }
    private static void conversionKey(String id,String key){
        int split=key.indexOf(':');
        if(split<=0||split==key.length()-1)throw new IllegalArgumentException("天赋转化键格式无效："+id);
        String sources=key.substring(0,split);
        if(!sources.equals("ALL"))for(String token:sources.split(",",-1)){
            int element;
            try{element=Integer.parseInt(token);}catch(NumberFormatException failure){throw new IllegalArgumentException("天赋转化元素无效："+id);}
            if(element<-1||element>3)throw new IllegalArgumentException("天赋转化元素无效："+id);
        }
        if(ElementAccumulation.kind(key.substring(split+1))==null)throw new IllegalArgumentException("天赋转化异常未登记："+id);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
