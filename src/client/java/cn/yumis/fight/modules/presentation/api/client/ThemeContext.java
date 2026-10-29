package cn.yumis.fight.modules.presentation.api.client;

/** A palette is scoped to one screen extraction; nested/modal screens restore their parent. */
public record ThemeContext(ThemeDomain domain) {
    public UiTheme.PaletteScope enter(){return UiTheme.use(ClientSettings.theme(domain));}
}
