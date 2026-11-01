package cn.yumis.fight.modules.skills.client;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.io.IOException;
/** Native textured ModelParts and local poses shared with the editable art source. */
final class SummonCuboidModel extends EntityModel<SummonRenderer.State> {
    record Cube(float[] box,int[] uv){}
    record Bone(String name,String parent,float[] pivot,float[] rotation,Cube[] cubes){}
    record Clip(double seconds,float[][][] frames){}
    record Data(int texture,Bone[] bones,Map<String,Clip> clips){}
    private final Data data;private final ModelPart[] parts;
    private SummonCuboidModel(Data data,ModelPart root){
        super(root);this.data=data;parts=new ModelPart[data.bones.length];var byName=new HashMap<String,ModelPart>();byName.put("",root);
        for(int i=0;i<parts.length;i++){var bone=data.bones[i];parts[i]=byName.get(bone.parent).getChild(bone.name);byName.put(bone.name,parts[i]);}
    }
    static SummonCuboidModel load(String id){
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","summons/block/"+id+".json"))){
            Data data=new Gson().fromJson(reader,Data.class);var mesh=new MeshDefinition();var definitions=new HashMap<String,PartDefinition>();definitions.put("",mesh.getRoot());
            for(var b:data.bones){var builder=CubeListBuilder.create();for(var cube:b.cubes){float[] v=cube.box;builder.texOffs(cube.uv[0],cube.uv[1]).addBox(v[0],v[1],v[2],v[3],v[4],v[5]);}definitions.put(b.name,definitions.get(b.parent).addOrReplaceChild(b.name,builder,PartPose.offsetAndRotation(b.pivot[0],b.pivot[1],b.pivot[2],b.rotation[0],b.rotation[1],b.rotation[2])));}
            return new SummonCuboidModel(data,LayerDefinition.create(mesh,data.texture,data.texture).bakeRoot());
        }catch(IOException e){throw new IllegalStateException("Missing cuboid summon "+id,e);}
    }
    @Override public void setupAnim(SummonRenderer.State state){
        super.setupAnim(state);for(var part:parts)part.resetPose();String clip=state.move.isEmpty()?(state.moving?"walk":"idle"):state.move;
        Clip action=data.clips.getOrDefault(clip,data.clips.get("idle"));boolean loop=clip.equals("idle")||clip.equals("walk")||clip.equals("guard");double time=loop?state.ageInTicks/20d:state.elapsed*action.seconds/Math.max(1,state.duration);
        double frame=(loop?time%action.seconds:Math.min(action.seconds,time))/action.seconds*(action.frames.length-1);int a=(int)frame,b=Math.min(a+1,action.frames.length-1);float f=(float)(frame-a);
        for(int i=0;i<parts.length;i++){var p=parts[i];float[] x=action.frames[a][i],y=action.frames[b][i];p.x+=mix(x[0],y[0],f);p.y+=mix(x[1],y[1],f);p.z+=mix(x[2],y[2],f);p.xRot+=mix(x[3],y[3],f);p.yRot+=mix(x[4],y[4],f);p.zRot+=mix(x[5],y[5],f);p.xScale=mix(x[6],y[6],f);p.yScale=mix(x[7],y[7],f);p.zScale=mix(x[8],y[8],f);}
    }
    private static float mix(float a,float b,float f){return a+(b-a)*f;}
}
