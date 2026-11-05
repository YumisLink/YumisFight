package cn.yumis.fight.modules.character.api;

import com.google.gson.*;
import java.util.*;

/** Character-owned index. Only inactive entries contain growth; the active Progress is authoritative. */
public final class CareerProfiles {
    private static final Gson JSON=new Gson();
    private static final List<String> GROWTH=List.of("level","experience","sp","strength","intelligence","spirit","job","skills","slots","sync","dungeonSync");
    public int schema=1,capacity=8;
    public String activeId;
    public long revision;
    public LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    public static final class Entry {
        public String name;
        public JsonObject growth;
        public Entry(String name,JsonObject growth){this.name=name;this.growth=growth;}
    }
    public static CareerProfiles first(){var book=new CareerProfiles();book.activeId=UUID.randomUUID().toString();book.entries.put(book.activeId,new Entry("职业配置1",null));return book;}
    public CareerProfiles copy(){return JSON.fromJson(JSON.toJsonTree(this),CareerProfiles.class);}
    public static Progress copy(Progress value){return JSON.fromJson(JSON.toJsonTree(value),Progress.class);}
    private static JsonObject growth(Progress value){var source=JSON.toJsonTree(value).getAsJsonObject();var result=new JsonObject();for(String key:GROWTH)result.add(key,source.get(key).deepCopy());return result;}
    public Progress select(Progress current,String target){
        if(target.equals(activeId))return current;
        Entry destination=entries.get(target);if(destination==null||destination.growth==null)throw new IllegalArgumentException("职业配置不存在");
        var source=JSON.toJsonTree(current).getAsJsonObject();for(String key:GROWTH)source.add(key,destination.growth.get(key).deepCopy());
        Progress next=JSON.fromJson(source,Progress.class);entries.get(activeId).growth=growth(current);destination.growth=null;activeId=target;revision=Math.addExact(revision,1);return next;
    }
    public boolean unlocked(Progress current){
        if(current.selected()&&!JobCatalog.basic(current.jobId()))return true;
        for(var entry:entries.values())if(entry.growth!=null){String job=entry.growth.get("job").getAsString();if(!job.equals(JobCatalog.NONE)&&!JobCatalog.basic(job))return true;}
        return false;
    }
    public void fillSlots(){
        boolean added=false;
        while(entries.size()<capacity){var next=new Progress();next.gainLevels(19);next.starterGranted=true;entries.put(UUID.randomUUID().toString(),new Entry("职业配置"+(entries.size()+1),growth(next)));added=true;}
        if(added)revision=Math.addExact(revision,1);
    }
    public void expand(Progress current){
        if(capacity==Integer.MAX_VALUE)throw new IllegalArgumentException("职业配置容量已达上限，未消耗券");
        capacity++;revision=Math.addExact(revision,1);if(unlocked(current))fillSlots();
    }
    public boolean occupied(String job){return entries.values().stream().anyMatch(e->e.growth!=null&&e.growth.get("job").getAsString().equals(job));}
    public void requireCurrent(JsonObject request){if(!request.has("profileId")||!request.has("profileRevision")||!activeId.equals(request.get("profileId").getAsString())||revision!=request.get("profileRevision").getAsLong())throw new IllegalArgumentException("职业配置已切换，请刷新后操作");}
    public void rename(String id,String name){
        String clean=name.strip();if(clean.isEmpty()||clean.codePointCount(0,clean.length())>16||clean.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("配置名称须为1～16个普通字符");
        Entry entry=entries.get(id);if(entry==null)throw new IllegalArgumentException("职业配置不存在");entry.name=clean;
    }
    public void validate(Progress active){
        if(schema!=1||capacity<8||revision<0||entries==null||entries.isEmpty()||entries.size()>capacity||!entries.containsKey(activeId))throw new IllegalStateException("职业配置存档损坏或版本不符");
        var jobs=new HashSet<String>();validateProgress(active);if(active.selected()&&!JobCatalog.basic(active.jobId()))jobs.add(active.jobId());
        for(var pair:entries.entrySet()){
            UUID.fromString(pair.getKey());Entry e=pair.getValue();if(e==null||e.name==null||e.name.isBlank()||pair.getKey().equals(activeId)!=(e.growth==null))throw new IllegalStateException("职业配置索引不完整");
            if(e.growth!=null){if(!e.growth.keySet().equals(new HashSet<>(GROWTH)))throw new IllegalStateException("职业配置成长格式不符");var source=JSON.toJsonTree(new Progress()).getAsJsonObject();e.growth.entrySet().forEach(v->source.add(v.getKey(),v.getValue()));var p=JSON.fromJson(source,Progress.class);validateProgress(p);if(p.selected()&&!JobCatalog.basic(p.jobId())&&!jobs.add(p.jobId()))throw new IllegalStateException("职业配置转职重复");}
        }
    }
    public static void validateProgress(Progress p){
        if(p==null||p.level<1||p.level>CharacterRules.MAX_LEVEL||p.experience<0||p.sp<0||p.strength<0||p.intelligence<0||p.spirit<0||p.free()<0||p.skills==null||p.slots==null||p.slots.length!=Progress.SLOT_COUNT||p.completedJobQuests==null)throw new IllegalStateException("角色成长存档不完整");
        JobCatalog.require(p.jobId());for(String slot:p.slots)if(slot==null)throw new IllegalStateException("技能配置存档不完整");
    }
    public JsonObject view(Progress active){
        var result=new JsonObject();result.addProperty("schema",1);result.addProperty("activeId",activeId);result.addProperty("revision",revision);result.addProperty("capacity",capacity);result.addProperty("unlocked",unlocked(active));var rows=new JsonArray();
        for(var pair:entries.entrySet()){var e=pair.getValue();boolean selected=pair.getKey().equals(activeId);var row=new JsonObject();row.addProperty("id",pair.getKey());row.addProperty("name",e.name);row.addProperty("level",selected?active.level:e.growth.get("level").getAsInt());row.addProperty("job",selected?active.jobId():e.growth.get("job").getAsString());rows.add(row);}result.add("entries",rows);return result;
    }
}
