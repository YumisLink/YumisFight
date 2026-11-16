package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Selection, A* and retries live on the selecting player's client. */
public final class ConveyorPlacementClient {
    private static BlockPos start;
    private static Direction startFacing;
    private static ConveyorBlock selected;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static String session="";
    private static ConveyorPathfinder search;
    private static InteractionHand hand;
    private static JsonObject pending;
    private static ConveyorPathfinder previewSearch;
    private static BlockPos previewEnd;
    private static Direction previewFacing;
    private static ConveyorRoute.Plan preview;
    private static long previewRetry;
    private static long sent;
    private static boolean used;
    private ConveyorPlacementClient(){}
    public static void register(){
        ConveyorPlacementPreview.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ConveyorPlacementClient::interact);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick());
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event)->{
            if(belt(event.getItemStack())!=null){
                event.getToolTip().add(Component.literal("Shift＋右键地面：选起点，再选终点自动铺设").withColor(0xaab7c9));
                event.getToolTip().add(Component.literal("新铺起终点朝向分别跟随选点时的玩家朝向").withColor(0xaab7c9));
                event.getToolTip().add(Component.literal("Shift＋右键空气：取消选点；单次最多256段").withColor(0xaab7c9));
                event.getToolTip().add(Component.literal("起终点相距32格内显示路线虚影；自动覆盖地表杂草、石子和断枝，保留魔力花").withColor(0xaab7c9));
                if(belt(event.getItemStack()).speed>1)event.getToolTip().add(Component.literal("运输速度：普通传送带的3倍").withColor(0x87ceeb));
            }
        });
    }
    private static ConveyorBlock belt(net.minecraft.world.item.ItemStack stack){return stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof ConveyorBlock block?block:null;}
    private static void message(String text){ClientUi.current().message(text);}
    private static void clearPreview(){previewSearch=null;previewEnd=null;previewFacing=null;preview=null;previewRetry=0;}
    private static void clear(){start=null;startFacing=null;selected=null;world=null;session="";search=null;pending=null;hand=null;clearPreview();}
    private static BlockHitResult target(){
        var mc=Minecraft.getInstance();
        if(mc.hitResult instanceof EntityHitResult)return null;
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getViewVector(1).scale(mc.player.blockInteractionRange()));
        return mc.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player){
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter level,BlockPos pos){
                return ConveyorRoute.replaceable(state)?Shapes.empty():super.getBlockShape(state,level,pos);
            }
        });
    }
    private static BlockPos placement(BlockHitResult hit){
        if(hit==null||hit.getType()!=HitResult.Type.BLOCK)return null;
        var level=Minecraft.getInstance().level;BlockPos pos=hit.getBlockPos();
        if(ConveyorConnections.belt(level.getBlockState(pos)))return pos.immutable();
        if(hit.getDirection()!=Direction.UP)return null;
        pos=pos.above();return ConveyorRoute.available(level,pos)?pos.immutable():null;
    }
    private static void interact(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isUseItem()||mc.player==null||mc.level==null||mc.gui.screen()!=null||!mc.player.isShiftKeyDown()||belt(mc.player.getItemInHand(event.getHand()))==null)return;
        event.setCanceled(true);event.setSwingHand(false);
        if(used)return;used=true;
        if(!ClientUi.current().ready()||!mc.player.isAlive()||mc.player.isSpectator())return;
        if(pending!=null){message("建造请求已发送，正在等待服务器结果");return;}
        BlockHitResult hit=target();if(hit==null)return;
        if(hit.getType()==HitResult.Type.MISS){clear();message("已取消传送带选点");return;}
        BlockPos pos=placement(hit);
        if(pos==null){message(hit.getDirection()!=Direction.UP?"请Shift右键地板的上表面":"该位置没有可用空位或承托");return;}
        ConveyorBlock block=belt(mc.player.getItemInHand(event.getHand()));
        if(start==null||selected!=block||world!=mc.level||!session.equals(ClientUi.current().session())){
            start=pos.immutable();startFacing=mc.player.getDirection();selected=block;world=mc.level;session=ClientUi.current().session();hand=event.getHand();search=null;clearPreview();
            message("传送带起点已设置："+start.getX()+", "+start.getY()+", "+start.getZ()+"；Shift右键终点铺设");return;
        }
        if(search!=null){message("正在规划路线；Shift右键空气可以取消");return;}
        hand=event.getHand();search=new ConveyorPathfinder(world,start,pos.immutable(),selected,startFacing,mc.player.getDirection());clearPreview();message("正在规划传送带路线…");
    }
    private static void tick(){
        var mc=Minecraft.getInstance();if(!mc.options.keyUse.isDown())used=false;
        if(start==null)return;
        if(mc.player==null||world!=mc.level||!session.equals(ClientUi.current().session())||!mc.player.isAlive()){clear();return;}
        updatePreview();
        if(search!=null){
            search.advance();
            if(search.done()){
                if(search.error()!=null){message(search.error());search=null;return;}
                pending=ClientUi.current().operation("conveyorBuild");pending.remove("profileId");pending.addProperty("dimension",world.dimension().identifier().toString());
                pending.addProperty("block",BuiltInRegistries.BLOCK.getKey(selected).toString());pending.addProperty("hand",hand.name());
                JsonArray route=new JsonArray();
                for(var step:search.result()){
                    JsonObject cell=new JsonObject();cell.addProperty("x",step.pos().getX());cell.addProperty("y",step.pos().getY());cell.addProperty("z",step.pos().getZ());cell.addProperty("facing",step.facing().getSerializedName());route.add(cell);
                }
                pending.add("route",route);search=null;send();
            }
        }else if(pending!=null&&ClientUi.now()-sent>=3000)send();
    }
    private static boolean showingPreview(){
        var mc=Minecraft.getInstance();
        return start!=null&&world==mc.level&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&session.equals(ClientUi.current().session())&&mc.gui.screen()==null&&mc.player.isShiftKeyDown()&&search==null&&pending==null
            &&(belt(mc.player.getMainHandItem())==selected||belt(mc.player.getOffhandItem())==selected);
    }
    private static void updatePreview(){
        if(!showingPreview()){clearPreview();return;}
        BlockPos end=placement(target());
        if(end==null||start.equals(end)||start.distSqr(end)>32*32){clearPreview();return;}
        Direction facing=Minecraft.getInstance().player.getDirection();
        if(!end.equals(previewEnd)||previewFacing!=facing){clearPreview();previewEnd=end;previewFacing=facing;}
        if(preview!=null){
            try{preview=ConveyorRoute.plan(world,preview.steps(),selected);}
            catch(IllegalArgumentException invalid){preview=null;previewRetry=0;}
        }
        if(preview==null&&previewSearch==null&&world.getGameTime()>=previewRetry)previewSearch=new ConveyorPathfinder(world,start,end,selected,startFacing,facing);
        if(previewSearch==null)return;
        previewSearch.advance();
        if(previewSearch.done()){
            if(previewSearch.error()==null)try{preview=ConveyorRoute.plan(world,previewSearch.result(),selected);}catch(IllegalArgumentException invalid){preview=null;}
            previewSearch=null;previewRetry=world.getGameTime()+10;
        }
    }
    static ConveyorRoute.Plan preview(){return showingPreview()?preview:null;}
    private static void send(){sent=ClientUi.now();ClientUi.current().send("conveyorBuild",pending);}
    public static void result(JsonObject reply){
        if(pending==null||!session.equals(reply.get("session").getAsString())||!pending.get("operation").equals(reply.get("operation")))return;
        if(reply.has("pending")&&reply.get("pending").getAsBoolean())return;
        boolean success=reply.get("success").getAsBoolean();pending=null;
        if(success)clear();message(reply.get("message").getAsString());
    }
}
