package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
/** Small cosmetic embers, spirit glints, and Summoner VP card persistent visuals (Sandor aura, Mark sigil, Louise magma). */
final class SummonAmbientVisuals {
    private SummonAmbientVisuals(){}
    static void extract(List<Quad> out,List<SpriteQuad> sprites,Vec3 camera){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        long serverNow=SkillsClient.current().serverNow();
        long uiNow=ClientUi.now();
        double globalTime=uiNow/1000d;
        for(var raw:mc.level.entitiesForRendering())if(raw instanceof SummonedCreature e&&e.distanceToSqr(mc.player)<64*64){
            if(e.move().equals("death")||e.move().equals("retreat"))continue;
            double time=e.tickCount/20d;Vec3 p=e.position();
            if(e.paladinAura()){
                double auraR=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SANDOR_PALADIN.value("radius");
                EffectSprites.sigil(sprites,p.add(0,.08,0),auraR,globalTime*.45,0xffe494,115,true);
                for(int i=0;i<16;i++){
                    double a=i*Math.PI/8+globalTime*.5;
                    dot(out,p.add(Math.cos(a)*auraR,.15,Math.sin(a)*auraR),.06,0xffe69c,135);
                }
            }
            switch(e.kind()){
                case FIRE->{
                    for(int i=0;i<3;i++){double u=(time*.7+i*.31)%1;dot(out,p.add(Math.sin(i*2+time)*.13,.55+u*.27,Math.cos(i*2+time)*.13),.025,0xffd27a,(int)(180*(1-u)));}
                    EffectSprites.glow(sprites,p.add(0,.62,0),camera,.46,0xffa84c,120);
                }
                case LIGHT->{
                    for(int i=0;i<10;i++){double a=i*Math.PI/5+time*.7;dot(out,p.add(Math.cos(a)*.31,.34+Math.sin(a*2)*.045,Math.sin(a)*.16),.025,0xa1eaff,150);}
                    dot(out,p.add(0,.35,0),.055,0xe1fbff,210);
                    EffectSprites.glow(sprites,p.add(0,.38,0),camera,.50,0x9beaff,130);
                }
                case ICE->{
                    double a=time*.8;dot(out,p.add(Math.cos(a)*.22,.53,Math.sin(a)*.22),.025,0xb6efff,155);
                    EffectSprites.glow(sprites,p.add(0,.52,0),camera,.42,0x8ae5ff,110);
                }
                case DARK->{
                    for(int i=0;i<2;i++){double a=time+i*Math.PI;dot(out,p.add(Math.sin(a)*.12,.65+i*.05,Math.cos(a)*.12),.025,0x78d5d5,150);}
                    EffectSprites.glow(sprites,p.add(0,.62,0),camera,.44,0x8e72d8,110);
                }
                case KING->{
                    for(int i=0;i<4;i++){double a=time+i*Math.PI/2;dot(out,p.add(Math.sin(a)*.12,2.05+(time*.25+i*.15)%1*.28,Math.cos(a)*.1),.035,0x9beaff,160);}
                    EffectSprites.glow(sprites,p.add(0,2.1,0),camera,.72,0x9beaff,145);
                }
                case HERDER->{
                    if(e.visualScale()>1.2f){
                        EffectSprites.glow(sprites,p.add(0,1.2,0),camera,.85,0xff8b36,130);
                    }
                }
                default->{}
            }
        }
        // Mark sigil (蚀落印记)
        UUID marked=SummonerClient.markedTarget(serverNow);
        if(marked!=null){
            var target=mc.level.getEntity(marked);
            if(target!=null&&target.isAlive()&&target.distanceToSqr(mc.player)<64*64){
                Vec3 mp=target.position();
                double r=Math.max(1.1,target.getBbWidth()*.95);
                EffectSprites.sigil(sprites,mp.add(0,.08,0),r,globalTime*2.4,0xd28cff,215,true);
                EffectSprites.glow(sprites,target.getBoundingBox().getCenter().add(0,target.getBbHeight()*.55,0),camera,.65,0xffe48c,195);
            }
        }
        // Louise Inferno Magma Seas (天火路易丝烈焰火海)
        for(var zone:SummonerClient.activeMagmaZones()){
            if(uiNow>zone.expiresAt)continue;
            double fade=Math.clamp((zone.expiresAt-uiNow)/600d,0,1);
            EffectSprites.sigil(sprites,zone.center.add(0,.07,0),zone.radius*.95,globalTime*.9,0xff6422,(int)(165*fade),true);
            for(int i=0;i<10;i++){
                double a=i*Math.PI/5+globalTime*1.1;
                double dist=zone.radius*(.3+.6*((i*37)%10)/10d);
                double rise=((globalTime*1.8+i*.27)%1)*1.1;
                dot(out,zone.center.add(Math.cos(a)*dist,.1+rise,Math.sin(a)*dist),.07,0xff9a38,(int)(195*fade*(1-rise/1.2)));
            }
        }
    }
    private static void dot(List<Quad> out,Vec3 p,double radius,int color,int alpha){
        out.add(new Quad(p.add(-radius,-radius,0),p.add(radius,-radius,0),p.add(radius,radius,0),p.add(-radius,radius,0),color,alpha));
        out.add(new Quad(p.add(0,-radius,-radius),p.add(0,-radius,radius),p.add(0,radius,radius),p.add(0,radius,-radius),color,alpha));
    }
}
