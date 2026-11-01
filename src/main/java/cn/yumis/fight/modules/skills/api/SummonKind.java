package cn.yumis.fight.modules.skills.api;
/** Content-owned summon roster. Each kind is unique per owner. */
public enum SummonKind {
 HERDER("herder","赫尔德",1.2,.8,2,2,1.5,1,1.2),
 FIRE("adorean","亚德炎",.75,.55,1.2,1,1,1,1.5),
 ICE("naiad","冰奈斯",.85,.65,1.2,1,1,1,2),
 LIGHT("wisp","雷沃斯",.65,.6,1.2,1,1,1,1),
 DARK("stalker","瑟冥特克",.8,.6,1.2,1,1,1,2.5),
 LOUISE("louise","路易丝",1.7,.65,1.5,1.5,1,1,2),
 SANDOR("sandor","桑德尔",1.9,.95,3.5,3,1.5,1.5,1.8),
 KING("echeverria","伊伽贝拉",2.1,.9,2.5,2,1.5,1,2),
 KASIJAS("kasijas","卡西利亚斯",2.6,1.2,4,2.5,1.5,1,1);
 public final String model,title;public final double height,width,health,defense,resist,shield,interval;
 SummonKind(String model,String title,double height,double width,double health,double defense,double resist,double shield,double interval){this.model=model;this.title=title;this.height=height;this.width=width;this.health=health;this.defense=defense;this.resist=resist;this.shield=shield;this.interval=interval;}
 public String skill(){return switch(this){case HERDER->MageSkills.HERDER;case FIRE,ICE,LIGHT,DARK->SummonerSkills.SPIRITS;case LOUISE->SummonerSkills.LOUISE;case SANDOR->SummonerSkills.SANDOR;case KING->SummonerSkills.KING;case KASIJAS->SummonerSkills.KASIJAS;};}
}
