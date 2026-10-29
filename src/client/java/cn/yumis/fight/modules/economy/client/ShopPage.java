package cn.yumis.fight.modules.economy.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.*;
public final class ShopPage {
    private ShopPage(){}
    public static void render(PlayerPageSurface page,Screen parent,long gold,long points,String session,long contractUntil,Supplier<JsonObject> operation,Consumer<JsonObject> submit,int titleColor,int muted){
        String category=page.remember("shop.category","contracts");
        page.navigation(List.of(new PlayerPageSurface.Navigation("items","道具"),new PlayerPageSurface.Navigation("materials","材料"),new PlayerPageSurface.Navigation("fashion","时装"),new PlayerPageSurface.Navigation("bundles","礼包"),new PlayerPageSurface.Navigation("contracts","契约")),category,id->{page.rememberValue("shop.category",id);page.rebuild();});
        page.line("金币 "+gold+"    点券 "+points,titleColor);var search=page.textInput("shopSearch","搜索商品",40);
        if(category.equals("items")){
            int y=page.cursor(),index=0,w=Math.max(80,Math.min(150,(page.contentWidth()-20)/2));
            for(boolean base:List.of(true,false)){
                String id=base?cn.yumis.fight.modules.character.api.CharacterItems.PROFESSION_RESET:cn.yumis.fight.modules.character.api.CharacterItems.CAREER_RESET,title=base?"职业重置券":"转职重置券";long cost=cn.yumis.fight.modules.economy.api.CurrencyRules.resetTicketPrice(base);
                if(!search.get().isBlank()&&!(title+" "+id).contains(search.get().trim()))continue;
                int x=index++*(w+8);page.card(x,y,w,92);page.icon("textures/item/"+id+".png",x+(w-24)/2,y+7,24);page.text(title,x+7,y+41,titleColor);page.text(cost+"点券",x+7,y+58,titleColor);
                var hit=page.button("购买",x+5,y+72,w-10,()->Minecraft.getInstance().gui.setScreen(new ThemedConfirmScreen(yes->{Minecraft.getInstance().gui.setScreen(parent);if(yes){var request=operation.get();request.addProperty("session",session);request.addProperty("product",id);submit.accept(request);}},Component.literal("购买"+title+"？"),Component.literal(points<cost?"点券不足，需要"+cost+"点券。":"消耗"+cost+"点券，获得可交易实体券。右键确认使用，保留任务标记，清除觉醒。"),ThemeDomain.MENU)));hit.setHeight(16);
            }
            if(index==0)page.line("没有匹配商品。",muted);page.cursor(y+100);return;
        }
        if(!category.equals("contracts")){page.line("暂无商品。",muted);return;}
        if(!search.get().isBlank()&&!"霸王之契约 overlord_contract".contains(search.get().trim())){page.line("没有匹配商品。",muted);return;}
        int available=page.contentWidth()-24,fit=(available+4)/60,columns=fit>=8?Math.min(12,fit):Math.clamp(fit,4,6),cell=available/columns,w=cell-4,y=page.cursor(),color=cn.yumis.fight.modules.equipment.api.Rarity.ARTIFACT.color();
        page.card(0,y,w,85);var hit=page.button("",0,y,w,()->Minecraft.getInstance().gui.setScreen(new ThemedConfirmScreen(yes->{Minecraft.getInstance().gui.setScreen(parent);if(yes){var request=operation.get();request.addProperty("session",session);request.addProperty("product","overlord_contract");submit.accept(request);}},Component.literal("购买霸王之契约？"),Component.literal(gold>=100000?"消耗100000金币，立即延长7×24小时。":"金币不足，需要100000金币。"),ThemeDomain.MENU)));
        hit.setHeight(85);
        page.icon("textures/item/currency/overlord_contract.png",(w-24)/2,y+6,24);
        var font=UiFonts.font();var title=font.split(Component.literal("霸王之契约"),Math.max(12,w-8));int yy=y+36;
        for(var line:title){StringBuilder s=new StringBuilder();line.accept((i,style,code)->{s.appendCodePoint(code);return true;});page.text(s.toString(),4,yy,color);yy+=10;}
        page.text("100000",4,y+69,color);
        page.hover(0,y,w,85,(g,mx,my)->{
            long now=ClientUi.current().serverNow();String expiry=contractUntil>now?"到期："+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Hong_Kong")).format(Instant.ofEpochMilli(contractUntil)):"当前未生效";
            g.setComponentTooltipForNextFrame(font,List.of(Component.literal("霸王之契约 · 7天").withColor(color),Component.literal("100000金币").withColor(color),Component.literal("可穿戴装备等级上限 +5，额度不叠加"),Component.literal("购买立即生效，离线计时，重复购买延长"),Component.literal("职业、装备槽资格仍须满足"),Component.literal(expiry).withColor(muted)),mx,my,net.minecraft.world.item.ItemStack.EMPTY);
        });
        page.cursor(y+93);
    }
}
