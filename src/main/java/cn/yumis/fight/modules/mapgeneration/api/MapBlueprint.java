package cn.yumis.fight.modules.mapgeneration.api;

import com.google.gson.Gson;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import java.io.IOException;
import java.util.*;

/** Data contract for roads, terrain regions and rigid facilities. Coordinates are relative to the spawn anchor. */
public final class MapBlueprint {
    public int version=1;
    public int[] bounds;
    public Settings settings=new Settings();
    public List<Road> roads=new ArrayList<>();
    public List<Ground> grounds=new ArrayList<>();
    public List<Facility> facilities=new ArrayList<>();
    public static final class Settings {
        public double maxRise=1;
        public int entranceFlatRadius=3, floatingThreshold=80, searchRadius=256, floatingSearchHeight=32;
        public int islandMargin=3, islandDepth=18;
        public int[] roadWeights={35,35,20,10};
        public String[] roadBlocks={"minecraft:stone","minecraft:andesite","minecraft:cobblestone","minecraft:diorite"};
        public String[] roadSlabs={"minecraft:stone_slab","minecraft:andesite_slab","minecraft:cobblestone_slab","minecraft:diorite_slab"};
        public String[] roadStairs={"minecraft:stone_stairs","minecraft:andesite_stairs","minecraft:cobblestone_stairs","minecraft:diorite_stairs"};
        public List<String> mainWoodFamilies=List.of("oak","birch","jungle","acacia","mangrove","cherry","pale_oak","bamboo");
        public List<String> accentWoodFamilies=List.of("spruce","dark_oak");
        public boolean lighting=true;
        public int streetLightTarget=0;
        public boolean noHostileSpawns=false;
    }
    public static final class Road {
        public String id;
        public double width=5;
        public int[][] points;
        public boolean external;
    }
    public static final class Ground {
        public String id;
        public int[] bounds;
        public int[][] polygon;
        public String surface="native";
        public String island="";
        public boolean clearVegetation;
        public List<Plant> plants=new ArrayList<>();
    }
    public static final class Plant {
        public int x,z;
        public String block;
        public boolean tall;
    }
    public static final class Facility {
        public String id, template, anchor="road", parent="", kind="structure", foundation="platform";
        public int[] position, entrance;
        public int referenceY;
        public int lightTarget=8;
        public boolean island=true, platform=true, skipInOcean=false, flatten=true;
        public boolean keepClear=false;
        public int[][] blocked=new int[0][];
        public List<String> tags=new ArrayList<>();
        public List<Loot> loot=new ArrayList<>();
        public List<NpcGroup> npcGroups=new ArrayList<>();
        public boolean fittedFoundation() {return !foundation.equals("platform");}
    }
    public static final class Loot {
        public int[] pos;
        public String table;
    }
    public static final class NpcGroup {
        public String id;
        public List<String> definitions=new ArrayList<>();
        public List<NpcAnchor> anchors=new ArrayList<>();
    }
    public static final class NpcAnchor {
        public String id;
        public double[] pos;
        public float yaw;
    }
    public static MapBlueprint load(ServerLevel level,Identifier id) throws IOException {
        var resource=Identifier.fromNamespaceAndPath(id.getNamespace(),"map_blueprint/"+id.getPath()+".json");
        try(var reader=level.getServer().getResourceManager().getResourceOrThrow(resource).openAsReader()) {
            MapBlueprint blueprint=new Gson().fromJson(reader,MapBlueprint.class);
            blueprint.validate();return blueprint;
        }
    }
    public void validate() {
        if(version!=1)throw new IllegalArgumentException("不支持的地图蓝图版本: "+version);
        if(bounds==null||bounds.length!=4||bounds[2]<bounds[0]||bounds[3]<bounds[1])throw new IllegalArgumentException("蓝图需要有效 bounds [minX,minZ,maxX,maxZ]");
        if(settings.maxRise<=0||settings.maxRise>1||settings.entranceFlatRadius<0||settings.floatingThreshold<0||settings.searchRadius<0||settings.streetLightTarget<0||settings.streetLightTarget>15)throw new IllegalArgumentException("道路／浮岛／灯光规则无效");
        int materials=settings.roadWeights.length;
        if(materials==0||settings.roadBlocks.length!=materials||settings.roadSlabs.length!=materials||settings.roadStairs.length!=materials||Arrays.stream(settings.roadWeights).anyMatch(v->v<0)||Arrays.stream(settings.roadWeights).sum()==0)throw new IllegalArgumentException("道路整块、半砖、楼梯及权重需要逐项对应");
        Set<String> ids=new HashSet<>();
        for(Road r:roads)if(r.id==null||!ids.add(r.id)||r.width<1||r.points==null||r.points.length<2||Arrays.stream(r.points).anyMatch(p->p.length!=2))throw new IllegalArgumentException("道路配置不完整或ID重复");
        for(Ground g:grounds)if(g.id==null||!ids.add(g.id)||g.bounds==null||g.bounds.length!=4)throw new IllegalArgumentException("地面配置不完整或ID重复");
        for(Facility f:facilities) {
            if(f.id==null||!ids.add(f.id)||f.template==null||f.position==null||f.position.length!=2||f.entrance==null||f.entrance.length!=2)throw new IllegalArgumentException("设施配置不完整或ID重复");
            Identifier.parse(f.template);
            if(f.foundation==null||!Set.of("platform","sparse_stone","terrain_retaining").contains(f.foundation)||f.fittedFoundation()&&!f.platform)throw new IllegalArgumentException("设施地基须为 platform、sparse_stone 或 terrain_retaining，后两种须启用 platform: "+f.id);
            for(Loot l:f.loot) {if(l.pos==null||l.pos.length!=3||l.table==null)throw new IllegalArgumentException("战利品槽配置无效");Identifier.parse(l.table);}
            Set<String> groups=new HashSet<>();
            for(var group:f.npcGroups){
                if(group.id==null||!groups.add(group.id)||group.definitions.isEmpty()||new HashSet<>(group.definitions).size()!=group.definitions.size()||group.anchors.size()<group.definitions.size())throw new IllegalArgumentException("NPC投放组无效："+f.id);
                for(String definition:group.definitions)cn.yumis.fight.modules.npc.api.NpcDefinitions.current().require(definition);
                Set<String> anchors=new HashSet<>();for(var anchor:group.anchors)if(anchor.id==null||!anchors.add(anchor.id)||anchor.pos==null||anchor.pos.length!=3||Arrays.stream(anchor.pos).anyMatch(v->!Double.isFinite(v))||!Float.isFinite(anchor.yaw))throw new IllegalArgumentException("NPC锚点无效："+f.id);
            }
        }
    }
}
