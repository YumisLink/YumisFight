package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** A stepped slider with native focus and drag handling. */
public final class ThemedSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final IntFunction<String> label;
    private final IntConsumer change;
    private int committed;

    public ThemedSlider(int x, int y, int width, int min, int max, int current,
                        IntFunction<String> label, IntConsumer change) {
        super(x, y, width, 32, Component.empty(), 0);
        this.min = min;
        this.max = max;
        this.label = label;
        this.change = change;
        committed = Math.clamp(current, min, max);
        active = min < max && width > HANDLE_WIDTH;
        value = position(committed);
        updateMessage();
    }

    private double position(int step) {
        return (double) (step-min)/Math.max(1, max-min);
    }

    private int step() {
        return min+(int) Math.round(value*(max-min));
    }

    @Override protected void updateMessage() {
        setMessage(Component.literal(label.apply(step())));
    }

    @Override protected void applyValue() {
        value = position(step());
    }

    private void commit() {
        int selected = step();
        if (selected != committed) {
            committed = selected;
            change.accept(selected);
        }
    }

    @Override public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        commit();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!isActive()) return false;
        if (canChangeValue && (event.isLeft() || event.isRight())) {
            setValue(position(step()+(event.isLeft() ? -1 : 1)));
            commit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth();
        boolean highlighted = isHoveredOrFocused();
        UiTheme.button(graphics, x, y, width, getHeight(), active, highlighted, false);

        var font = UiFonts.font();
        int inset = UiTheme.textInset();
        var text = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width-inset-6));
        graphics.text(font, text, x+inset, y+5, active ? UiTheme.TEXT : UiTheme.MUTED, false);

        int halfHandle = HANDLE_WIDTH/2;
        int trackStart = x+halfHandle;
        int trackEnd = x+width-halfHandle;
        int thumb = trackStart+(int) Math.round(value*(trackEnd-trackStart));
        graphics.fill(trackStart, y+22, trackEnd, y+25, UiTheme.SCROLL_TRACK);
        if (active) graphics.fill(trackStart, y+22, thumb, y+25, UiTheme.ACCENT);
        graphics.fill(thumb-halfHandle, y+19, thumb+halfHandle, y+28,
            !active ? UiTheme.MUTED : highlighted ? UiTheme.ACCENT : UiTheme.SCROLL_THUMB);
        handleCursor(graphics);
    }
}
