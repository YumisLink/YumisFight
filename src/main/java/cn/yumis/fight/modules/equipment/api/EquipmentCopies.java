package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.affixes.api.Affix;

/** Detached working copies without serializing saved growth curves and affixes on every read. */
final class EquipmentCopies {
    private EquipmentCopies(){}
    static Gear gear(Gear source){
        Gear copy=new Gear(source.instance);copy.corrupted=source.corrupted;copy.creativeTemplate=source.creativeTemplate;
        copy.version=source.version;copy.level=source.level;copy.revision=source.revision;copy.sockets=source.sockets;
        copy.slot=source.slot;copy.weapon=source.weapon;copy.armorType=source.armorType;copy.jewelryType=source.jewelryType;copy.rarity=source.rarity;
        copy.set=source.set;copy.source=source.source;copy.template=source.template;copy.appearance=source.appearance;copy.savedName=source.savedName;
        source.baseGrowth.forEach((stat,curve)->copy.baseGrowth.put(stat,curve.clone()));
        for(Affix affix:source.affixes)copy.affixes.add(affix(affix));
        for(Gem original:source.gems){Gem gem=new Gem();gem.name=original.name;gem.level=original.level;gem.group=original.group;for(Affix affix:original.affixes)gem.affixes.add(affix(affix));copy.gems.add(gem);}
        return copy;
    }
    static Affix affix(Affix source){
        if(source.getClass()!=Affix.class)return EquipmentJson.copy(source,Affix.class);
        Affix copy=new Affix();copy.preserved=source.preserved==null?null:source.preserved.deepCopy();
        copy.schemaVersion=source.schemaVersion;copy.instanceId=source.instanceId;copy.syncStatus=source.syncStatus;
        copy.id=source.id;copy.stat=source.stat;copy.operation=source.operation;copy.family=source.family;copy.skill=source.skill;copy.parameter=source.parameter;
        copy.value=source.value;copy.maximum=source.maximum;copy.baseline=source.baseline;copy.baselineMaximum=source.baselineMaximum;
        copy.roll=source.roll;copy.corruptionMultiplier=source.corruptionMultiplier;copy.base=source.base;copy.fixed=source.fixed;copy.locked=source.locked;copy.element=source.element;copy.rank=source.rank;
        if(source.origin!=null){var a=source.origin;var origin=new Affix.Anchor();origin.operation=a.operation;origin.stat=a.stat;origin.element=a.element;origin.rank=a.rank;origin.revision=a.revision;origin.value=a.value;origin.maximum=a.maximum;origin.baseline=a.baseline;origin.baselineMaximum=a.baselineMaximum;origin.roll=a.roll;origin.definitionScale=a.definitionScale;origin.quality=a.quality;copy.origin=origin;}
        return copy;
    }
}
