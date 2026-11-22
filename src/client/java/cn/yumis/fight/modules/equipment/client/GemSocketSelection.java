package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.Gear;
import java.util.UUID;

/** 满孔装备滚轮选孔的瞬时客户端状态：移开鼠标、更换装备或完成镶嵌即重置为第一孔。 */
public final class GemSocketSelection {
    private static UUID trackedInstance;
    private static int trackedMenu,trackedSlot,selected;

    private GemSocketSelection(){}

    /** 每帧悬浮校验：光标携带宝石悬浮在满孔装备槽上时保持会话（首次或换目标回到第一孔），否则清空。 */
    public static void track(int hoverMenu,int hoverSlot,Gear gear){
        if(gear==null||gear.sockets<=0||gear.gems.size()<gear.sockets){clear();return;}
        if(trackedInstance==null||!gear.instance.equals(trackedInstance)||trackedMenu!=hoverMenu||trackedSlot!=hoverSlot){
            trackedInstance=gear.instance;trackedMenu=hoverMenu;trackedSlot=hoverSlot;selected=0;
        }
    }
    /** 向上滚动为前一孔，向下为后一孔，环绕循环；仅满孔多孔装备有效。 */
    public static void scroll(int hoverMenu,int hoverSlot,Gear gear,double deltaY){
        if(gear==null||gear.sockets<=1||gear.gems.size()<gear.sockets||deltaY==0)return;
        track(hoverMenu,hoverSlot,gear);
        if(trackedInstance==null)return;
        selected=Math.floorMod(selected+(deltaY>0?-1:1),gear.sockets);
    }
    /** 满孔右键的覆盖目标；无会话默认第一孔。 */
    public static int socket(int hoverMenu,int hoverSlot,UUID gearInstance){
        return gearInstance.equals(trackedInstance)&&trackedMenu==hoverMenu&&trackedSlot==hoverSlot?selected:0;
    }
    /** 悬浮提示标记的孔号；无会话返回 -1。 */
    public static int mark(UUID gearInstance){
        return gearInstance.equals(trackedInstance)?selected:-1;
    }
    /** 未满插入第一个空位，满孔替换选中孔。 */
    public static int insertTarget(int hoverMenu,int hoverSlot,Gear gear){
        return gear.gems.size()>=gear.sockets?socket(hoverMenu,hoverSlot,gear.instance):gear.gems.size();
    }
    public static void clear(){trackedInstance=null;}
}
