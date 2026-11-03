package cn.yumis.fight.modules.economy.client;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;
public final class ShopPage {
    private record Product(String id,String title,String icon,long price,boolean contract,List<String> details){
        String priceText(){return price+(contract?"金币":"点券");}
    }
    private static final List<Product> ITEMS=List.of(
        ticket("profession_reset_ticket","职业重置券",List.of(
            "当前配置至少20级且已转职时，消耗后新增20级职业配置。",
            "保留原配置的等级、加点和技能，新配置自行选择基础职业与转职。",
            "每人初始8个容量；新配置不再赠送武器。",
            "转职与觉醒资格全角色共享，最终转职不得重复。",
            "需在副本、训练外脱战，无施法且所有技能冷却结束。")),
        ticket("career_unlock_ticket","一次性转职解锁券",List.of(
            "永久解锁所有职业的通用转职资格。",
            "20级可用，无职业前置。",
            "已有资格时不消耗券。")),
        ticket("awakening_ticket","一次性觉醒券",List.of(
            "永久获得觉醒资格，不自动学习技能。",
            "50级可用，无职业前置。",
            "全部职业配置共用觉醒资格，各套仍需50级学习觉醒技能。",
            "已有觉醒资格时不消耗券。"))
    );
    private static final Product CONTRACT=new Product("overlord_contract","霸王之契约","textures/item/currency/overlord_contract.png",100000,true,List.of(
        "可穿戴装备等级上限 +5，额度不叠加。",
        "购买立即生效，持续7×24小时，离线继续计时。",
        "重复购买从当前到期时间与现在的较晚者续7天。",
        "职业、装备槽资格仍须满足。"));
    private static final DateTimeFormatter EXPIRY=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Hong_Kong"));
    private ShopPage(){}
    public static void render(PlayerPageSurface page,Screen parent,long gold,long points,String session,long contractUntil,Supplier<JsonObject> operation,Consumer<JsonObject> submit,int titleColor,int muted){
        String category=page.remember("shop.category","contracts");
        page.navigation(List.of(new PlayerPageSurface.Navigation("items","道具"),new PlayerPageSurface.Navigation("materials","材料"),new PlayerPageSurface.Navigation("fashion","时装"),new PlayerPageSurface.Navigation("bundles","礼包"),new PlayerPageSurface.Navigation("contracts","契约")),category,id->{page.rememberValue("shop.category",id);page.rebuild();});
        page.line("金币 "+gold+"    点券 "+points,titleColor);var search=page.textInput("shopSearch","搜索商品",40);
        List<Product> catalog=switch(category){case "items"->ITEMS;case "contracts"->List.of(CONTRACT);default->List.of();};
        if(catalog.isEmpty()){page.line("暂无商品。",muted);return;}
        String query=search.get().trim().toLowerCase(Locale.ROOT);
        var products=catalog.stream().filter(product->(product.title()+" "+product.id()).toLowerCase(Locale.ROOT).contains(query)).toList();
        if(products.isEmpty()){page.line("没有匹配商品。",muted);return;}
        page.line("悬浮商品查看详细信息，点击购买后确认。",muted);
        int available=Math.max(1,page.contentWidth()-24),columns=Math.min(2,Math.max(1,(available+8)/140));
        int w=Math.min(150,(available-(columns-1)*8)/columns),y=page.cursor();
        var font=UiFonts.font();
        int titleRows=products.stream().mapToInt(product->font.split(Component.literal(product.title()),Math.max(1,w-14)).size()).max().orElse(1);
        int extra=Math.max(0,titleRows-1)*font.lineHeight,height=92+extra,index=0;
        for(var product:products){
            int x=(index%columns)*(w+8),rowY=y+(index/columns)*(height+8);index++;
            int color=product.contract()?Rarity.ARTIFACT.color():titleColor;
            page.card(x,rowY,w,height);page.icon(product.icon(),x+(w-24)/2,rowY+7,24);
            int titleY=rowY+41;
            for(var line:font.split(Component.literal(product.title()),Math.max(1,w-14))){
                var text=new StringBuilder();line.accept((i,style,code)->{text.appendCodePoint(code);return true;});
                page.text(text.toString(),x+7,titleY,color);titleY+=font.lineHeight;
            }
            page.text(product.priceText(),x+7,rowY+58+extra,color);
            var buy=page.button("购买",x+5,rowY+72+extra,w-10,()->purchase(parent,product,product.contract()?gold:points,session,operation,submit));buy.setHeight(16);
            page.hover(x,rowY,w,height,(g,mx,my)->g.setComponentTooltipForNextFrame(font,details(product,contractUntil,color,muted),mx,my,net.minecraft.world.item.ItemStack.EMPTY));
        }
        page.cursor(y+((index+columns-1)/columns)*(height+8));
    }

    private static Product ticket(String id,String title,List<String> details){
        return new Product(id,title,"textures/item/"+id+".png",CurrencyRules.ticketPrice(id),false,details);
    }
    private static List<Component> details(Product product,long contractUntil,int color,int muted){
        var lines=new ArrayList<Component>();
        color=UiTheme.tooltipColor(color);muted=UiTheme.tooltipColor(muted);
        lines.add(Component.literal(product.title()+(product.contract()?" · 7天":"")).withColor(color));
        lines.add(Component.literal(product.priceText()).withColor(color));
        for(String detail:product.details())lines.add(Component.literal(detail).withColor(UiTheme.TIP_TEXT));
        if(product.contract()){
            if(contractUntil>ClientUi.current().serverNow()){
                lines.add(Component.literal("到期时间（香港）：").withColor(muted));
                lines.add(Component.literal(EXPIRY.format(Instant.ofEpochMilli(contractUntil))).withColor(muted));
            }else lines.add(Component.literal("当前未生效").withColor(muted));
        }else{
            lines.add(Component.literal("购买获得1张可交易实体券。").withColor(muted));
            lines.add(Component.literal("持券主手右键使用。").withColor(muted));
        }
        return lines;
    }
    private static void purchase(Screen parent,Product product,long balance,String session,Supplier<JsonObject> operation,Consumer<JsonObject> submit){
        String currency=product.contract()?"金币":"点券";
        String message=balance<product.price()?currency+"不足，需要"+product.priceText()+"。":"消耗"+product.priceText()+"，"+(product.contract()?"立即延长7×24小时。":"获得1张可交易实体券。"+product.details().getFirst());
        Minecraft.getInstance().gui.setScreen(new ThemedConfirmScreen(yes->{
            Minecraft.getInstance().gui.setScreen(parent);
            if(yes){var request=operation.get();request.addProperty("session",session);request.addProperty("product",product.id());submit.accept(request);}
        },Component.literal("购买"+product.title()+"？"),Component.literal(message),ThemeDomain.MENU));
    }
}
