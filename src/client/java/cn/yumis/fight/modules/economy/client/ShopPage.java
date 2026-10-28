package cn.yumis.fight.modules.economy.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.*;
public final class ShopPage {
    private ShopPage(){}
    public static void render(PlayerPageSurface page,Screen parent,long gold,String session,long contractUntil,Supplier<JsonObject> operation,Consumer<JsonObject> submit,int titleColor,int muted){
        page.line("商城 · 金币 "+gold,titleColor);var search=page.textInput("shopSearch","搜索商品",40);
        page.rowButton("全部商品 / 契约",()->{});
        String text=search.get().trim();if(!text.isEmpty()&&!"霸王之契约 overlord_contract".contains(text)){page.line("没有匹配商品。",muted);return;}
        int y=page.cursor(),width=Math.min(page.contentWidth(),340);page.card(0,y,width,154);page.icon("textures/item/currency/overlord_contract.png",10,y+12,30);
        page.text("霸王之契约 · 7天",48,y+15,titleColor);page.text("100000 金币",12,y+48,titleColor);page.text("可穿戴装备等级上限 +5，额度不叠加",12,y+67,muted);page.text("购买立即生效，离线计时，重复购买延长",12,y+84,muted);
        var button=page.button("购买并立即生效",12,y+109,width-24,()->{
            Minecraft.getInstance().gui.setScreen(new ConfirmScreen(yes->{Minecraft.getInstance().gui.setScreen(parent);if(yes){var request=operation.get();request.addProperty("session",session);request.addProperty("product","overlord_contract");submit.accept(request);}},Component.literal("购买霸王之契约？"),Component.literal("消耗100000金币，立即延长7×24小时。")));
        });button.active=gold>=100000;page.cursor(y+164);
        long now=ClientUi.current().serverNow();page.line(contractUntil>now?"契约到期："+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Hong_Kong")).format(Instant.ofEpochMilli(contractUntil)):"当前没有生效的霸王之契约。",muted);
        page.line("等级之外的职业、装备槽资格仍须满足。",muted);
    }
}
