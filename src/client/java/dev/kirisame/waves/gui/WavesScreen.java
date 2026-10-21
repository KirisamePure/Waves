package dev.kirisame.waves.gui;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.config.WavesConfig;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ReferencePack;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class WavesScreen extends Screen {
    private final Screen parent;
    private int page;
    private boolean saveFailed;
    private boolean activationFailed;

    public WavesScreen(Screen parent) {
        super(Text.translatable("screen.waves.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        int top = Math.max(66, height / 2 - 62);
        WavesConfig c = WavesClient.config;
        if (page == 0) {
            button(left, top, 148, toggle("enabled", c.enabled), () -> { c.enabled = !c.enabled; rebuild(); });
            boolean connected = IrisBridge.active();
            button(left + 152, top, 148, Text.translatable(connected ? "screen.waves.shaders" : "screen.waves.enable_reference"), () -> {
                if (connected) client.setScreen(IrisBridge.shaderScreen(this));
                else { activationFailed = !ReferencePack.enable(); rebuild(); }
            });
            slider(left, top + 26, "amplitude", 0, 2, () -> c.amplitude, v -> c.amplitude = v, "%.2f");
            slider(left + 152, top + 26, "wavelength", 8, 96, () -> c.wavelength, v -> c.wavelength = v, "%.1f");
            slider(left, top + 52, "speed", 0, 4, () -> c.speed, v -> c.speed = v, "%.2f");
            slider(left + 152, top + 52, "direction", 0, 360, () -> c.direction, v -> c.direction = v, "%.0f°");
            slider(left, top + 78, "sharpness", 0, 1, () -> c.sharpness, v -> c.sharpness = v, "%.2f");
            button(left + 152, top + 78, 148, Text.translatable("screen.waves.preset"), () -> {
                c.amplitude = .45; c.wavelength = 30; c.speed = 1.15;
                c.sharpness = .85; c.rainMultiplier = 2; c.thunderMultiplier = 6; rebuild();
            });
        } else {
            slider(left, top, "rain", 1, 5, () -> c.rainMultiplier, v -> {
                c.rainMultiplier = v; c.thunderMultiplier = Math.max(v, c.thunderMultiplier);
            }, "%.1f×");
            slider(left + 152, top, "thunder", 1, 10, () -> c.thunderMultiplier,
                    v -> c.thunderMultiplier = Math.max(c.rainMultiplier, v), "%.1f×");
            slider(left, top + 26, "transition", .25, 30, () -> c.transitionSeconds, v -> c.transitionSeconds = v, "%.1f s");
            slider(left + 152, top + 26, "sea_level", -64, 320, () -> c.seaLevel, v -> c.seaLevel = Math.rint(v), "%.0f");
            button(left, top + 52, 148, toggle("sea_only", c.seaLevelOnly), () -> { c.seaLevelOnly = !c.seaLevelOnly; rebuild(); });
            button(left + 152, top + 52, 148, toggle("overworld", c.overworldOnly), () -> { c.overworldOnly = !c.overworldOnly; rebuild(); });
            button(left, top + 78, 300, Text.translatable("screen.waves.reset"), () -> { WavesClient.config = new WavesConfig(); rebuild(); });
        }
        button(left, top + 108, 148, Text.translatable(page == 0 ? "screen.waves.next" : "screen.waves.previous"), () -> { page = 1 - page; rebuild(); });
        button(left + 152, top + 108, 148, Text.translatable("gui.done"), this::close);
        if (saveFailed) button(left, top + 134, 300, Text.translatable("screen.waves.discard_save"), () -> client.setScreen(parent));
    }

    private void rebuild() { clearChildren(); init(); }

    private static Text toggle(String key, boolean enabled) {
        return Text.translatable("option.waves." + key).append(": ").append(Text.translatable(enabled ? "options.on" : "options.off"));
    }

    private void button(int x, int y, int w, Text text, Runnable action) {
        addDrawableChild(ButtonWidget.builder(text, ignored -> action.run()).dimensions(x, y, w, 20).build());
    }

    private void slider(int x, int y, String key, double min, double max,
                        DoubleSupplier get, DoubleConsumer set, String format) {
        SliderWidget widget = new SliderWidget(x, y, 148, 20, Text.empty(), (get.getAsDouble() - min) / (max - min)) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.translatable("option.waves." + key).append(": ")
                        .append(String.format(Locale.ROOT, format, get.getAsDouble())));
            }
            @Override protected void applyValue() { set.accept(min + value * (max - min)); updateMessage(); }
        };
        widget.setTooltip(Tooltip.of(Text.translatable("tooltip.waves." + key)));
        addDrawableChild(widget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.10's Screen.renderWithTooltip already draws/blurs the background.
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        String status = saveFailed ? "screen.waves.save_failed" :
                (activationFailed ? "screen.waves.activation_failed" : WavesClient.statusKey());
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(status), width / 2, 31,
                status.equals("screen.waves.active") ? 0xFF7FE0C3 : 0xFFFFCC77);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.waves.diagnostics",
                String.format(Locale.ROOT, "%.2f", WavesClient.amplitude()),
                String.format(Locale.ROOT, "%.0f", WavesClient.config.seaLevel),
                String.format(Locale.ROOT, "%.2f", WavesClient.config.speed)), width / 2, 45, 0xFFB6C4D2);
        if (height >= 240) context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.waves.note"), width / 2, height - 20, 0xFFB6C4D2);
    }

    @Override
    public void close() {
        if (!WavesClient.STORE.save(WavesClient.config)) { saveFailed = true; rebuild(); return; }
        client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }
}
