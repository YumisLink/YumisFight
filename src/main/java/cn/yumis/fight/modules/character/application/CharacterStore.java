package cn.yumis.fight.modules.character.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.character.api.*;
import com.google.gson.*;
import java.util.*;

/** New character generation only. No old player keys or old field decoding. */
public final class CharacterStore implements CharacterPersistence {
    private static final Gson JSON=new Gson();
    private final Journal journal;
    public CharacterStore(Journal journal){this.journal=journal;}
    private static String active(UUID id){return "character-v2/active/"+id;}
    private static String book(UUID id){return "character-v2/book/"+id;}
    public Opened open(UUID id){
        boolean a=journal.get(active(id)).isJsonNull(),b=journal.get(book(id)).isJsonNull();
        if(a&&b)return new Opened(new Progress(),CareerProfiles.first(),true);
        if(a||b)throw new IllegalStateException("职业配置存档不完整，拒绝恢复");
        Progress p=read(id);CareerProfiles profiles=profiles(id);profiles.validate(p);return new Opened(p,profiles,false);
    }
    public Progress read(UUID id){
        var value=journal.get(active(id));
        if(!value.isJsonObject()||!value.getAsJsonObject().keySet().containsAll(Set.of("schema","level","experience","sp","strength","intelligence","spirit","sync","dungeonSync","starterGranted","job","completedJobQuests","awakened","skills","slots"))||value.getAsJsonObject().get("schema").getAsInt()!=2)throw new IllegalStateException("角色存档代际不符或字段缺失");
        Progress p=JSON.fromJson(value,Progress.class);CareerProfiles.validateProgress(p);return p;
    }
    public CareerProfiles profiles(UUID id){var value=journal.get(book(id));if(!value.isJsonObject()||!value.getAsJsonObject().keySet().containsAll(Set.of("schema","capacity","activeId","revision","entries")))throw new IllegalStateException("职业配置存档不完整");return JSON.fromJson(value,CareerProfiles.class);}
    private static Map<String,JsonElement> activeWrites(UUID id,Progress p){CareerProfiles.validateProgress(p);var value=JSON.toJsonTree(p).getAsJsonObject();value.addProperty("schema",2);return Map.of(active(id),value);}
    public Map<String,JsonElement> writes(UUID id,Progress p){profiles(id).validate(p);return activeWrites(id,p);}
    public Map<String,JsonElement> writes(UUID id,Progress p,CareerProfiles profiles){profiles.validate(p);var result=new LinkedHashMap<>(activeWrites(id,p));result.put(book(id),JSON.toJsonTree(profiles));return result;}
}
