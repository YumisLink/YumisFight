package cn.yumis.fight.modules.dungeons.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Module-local loader for the approved Y-up art source; no dependency on the skills model loader. */
final class GblModel extends EntityModel<GblEnemyRenderer.State> {
    private static final Set<String> WORLD_MOTION=Set.of("pounce","jump_slam","shockwave");
    record Cube(String name,float[] box,float[] rotation,int[] uv){}
    record Bone(String name,String parent,float[] pivot,float[] rotation,boolean effect,Cube[] cubes){}
    record Clip(double seconds,float[][][] frames){}
    record Data(int texture_size,Bone[] bones,Map<String,Clip> clips){}
    private final Data data;private final ModelPart[] parts;
    private GblModel(Data data,ModelPart root){
        super(root);this.data=data;parts=new ModelPart[data.bones.length];Map<String,ModelPart> byName=new HashMap<>();byName.put("",root.getChild("ground"));
        for(int i=0;i<parts.length;i++){Bone b=data.bones[i];parts[i]=byName.get(b.parent).getChild(b.name);byName.put(b.name,parts[i]);}
    }
    static GblModel load(String name){
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","dungeons/gbl/"+name+".json"))){
            Data data=new Gson().fromJson(reader,Data.class);var mesh=new MeshDefinition();Map<String,PartDefinition> parts=new HashMap<>();parts.put("",mesh.getRoot().addOrReplaceChild("ground",CubeListBuilder.create(),PartPose.offset(0,24,0)));
            for(Bone b:data.bones){
                var bone=parts.get(b.parent).addOrReplaceChild(b.name,CubeListBuilder.create(),PartPose.offsetAndRotation(b.pivot[0],-b.pivot[1],-b.pivot[2],rad(b.rotation[0]),-rad(b.rotation[1]),-rad(b.rotation[2])));parts.put(b.name,bone);
                for(int i=0;i<b.cubes.length;i++){
                    Cube c=b.cubes[i];float[] v=c.box;float cx=v[0]+v[3]/2,cy=v[1]+v[4]/2,cz=v[2]+v[5]/2;
                    bone.addOrReplaceChild("cube_"+i,CubeListBuilder.create().texOffs(c.uv[0],c.uv[1]).addBox(-v[3]/2,-v[4]/2,-v[5]/2,v[3],v[4],v[5]),PartPose.offsetAndRotation(cx,-cy,-cz,rad(c.rotation[0]),-rad(c.rotation[1]),-rad(c.rotation[2])));
                }
            }
            return new GblModel(data,LayerDefinition.create(mesh,data.texture_size,data.texture_size).bakeRoot());
        }catch(java.io.IOException error){throw new IllegalStateException("Missing GBL model: "+name,error);}
    }
    private static float rad(float deg){return deg*(float)Math.PI/180;}
    @Override public void setupAnim(GblEnemyRenderer.State s){
        super.setupAnim(s);for(var part:parts)part.resetPose();String name=s.dying?"death":s.elapsed<s.duration&&!s.move.isEmpty()?s.move:s.hurt>0?"hurt":s.moving&&data.clips.containsKey("walk")?"walk":"idle";
        Clip clip=data.clips.getOrDefault(name,data.clips.get("idle"));double phase;
        if(s.dying)phase=Math.clamp(s.deathPhase,0,1);
        else if(name.equals("idle")||name.equals("walk"))phase=(s.ageInTicks/20d%clip.seconds)/clip.seconds;
        else if(name.equals("hurt"))phase=Math.clamp((10-s.hurt)/10d,0,1);
        else phase=Math.clamp(s.elapsed/(double)Math.max(1,s.duration),0,1);
        double frame=phase*(clip.frames.length-1);int a=(int)frame,b=Math.min(a+1,clip.frames.length-1);float f=(float)(frame-a);
        for(int i=0;i<parts.length;i++){
            Bone bone=data.bones[i];ModelPart part=parts[i];float[] p=clip.frames[a][i],q=clip.frames[b][i];
            part.visible=!bone.effect||bone.name.equals("goddess");if(!part.visible)continue;
            // The server owns actual leap position and collision. This avoids integrating art root motion twice.
            boolean worldMotion=bone.name.equals("root")&&WORLD_MOTION.contains(name);
            if(!worldMotion){part.x+=mix(p[0],q[0],f);part.y-=mix(p[1],q[1],f);part.z-=mix(p[2],q[2],f);}
            part.xRot+=rad(mix(p[3],q[3],f));part.yRot-=rad(mix(p[4],q[4],f));part.zRot-=rad(mix(p[5],q[5],f));part.xScale=mix(p[6],q[6],f);part.yScale=mix(p[7],q[7],f);part.zScale=mix(p[8],q[8],f);
        }
    }
    private static float mix(float a,float b,float f){return a+(b-a)*f;}
}
