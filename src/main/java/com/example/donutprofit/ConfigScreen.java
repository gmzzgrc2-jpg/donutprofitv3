package com.example.donutprofit;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Pantalla de ajustes: tarjeta con animación de entrada y vista previa en vivo del HUD. */
public class ConfigScreen extends Screen {
    private static final int COL_W = 150, GAP = 10, PAD = 14, STEP = 22;
    private static final int CARD_W = COL_W * 2 + GAP + PAD * 2;
    private static final int CARD_H = 292;

    private final Screen parent;
    private final List<ClickableWidget> items = new ArrayList<>();
    private final List<Integer> relY = new ArrayList<>();
    private long openedAt;
    private int cardX, cardY;
    private String version = "";

    public ConfigScreen(Screen parent) {
        super(Text.literal("DonutProfit"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = System.nanoTime();
        items.clear();
        relY.clear();
        version = FabricLoader.getInstance().getModContainer("donutprofit")
                .map(mc -> mc.getMetadata().getVersion().getFriendlyString()).orElse("");

        Config c = Config.get();
        cardX = (this.width - CARD_W) / 2;
        cardY = Math.max(6, (this.height - CARD_H) / 2);
        int left = cardX + PAD;
        int right = cardX + PAD + COL_W + GAP;

        // MOSTRAR
        toggle(left, 58, "Balance", () -> c.showBalance, v -> c.showBalance = v);
        toggle(left, 58 + STEP, "Hoy", () -> c.showToday, v -> c.showToday = v);
        toggle(left, 58 + STEP * 2, "Gambling", () -> c.showGambling, v -> c.showGambling = v);
        toggle(left, 58 + STEP * 3, "W / L", () -> c.showWinLoss, v -> c.showWinLoss = v);
        toggle(left, 58 + STEP * 4, "Sesión", () -> c.showSession, v -> c.showSession = v);
        toggle(left, 58 + STEP * 5, "Gráfico", () -> c.showGraph, v -> c.showGraph = v);

        // APARIENCIA
        track(ButtonWidget.builder(Text.literal("Posición: " + c.anchor.label), b -> {
            c.anchor = c.anchor.next();
            b.setMessage(Text.literal("Posición: " + c.anchor.label));
        }).dimensions(right, 0, COL_W, 20).build(), 58);

        track(ButtonWidget.builder(Text.literal("Color: " + Config.ACCENT_NAMES[c.accent]), b -> {
            c.accent = (c.accent + 1) % Config.ACCENTS.length;
            b.setMessage(Text.literal("Color: " + Config.ACCENT_NAMES[c.accent]));
        }).dimensions(right, 0, COL_W, 20).build(), 58 + STEP);

        track(new Slider(right, 0, COL_W, 20, "Escala", 0.5f, 2.5f, c.scale, "%.2f", v -> c.scale = v), 58 + STEP * 2);
        track(new Slider(right, 0, COL_W, 20, "Opacidad", 0f, 1f, c.opacity, "%.2f", v -> c.opacity = v), 58 + STEP * 3);
        track(new Slider(right, 0, COL_W, 20, "Margen X", 0f, 300f, c.offsetX, "%.0f", v -> c.offsetX = Math.round(v)), 58 + STEP * 4);
        track(new Slider(right, 0, COL_W, 20, "Margen Y", 0f, 300f, c.offsetY, "%.0f", v -> c.offsetY = Math.round(v)), 58 + STEP * 5);

        // GENERAL
        toggle(left, 208, "HUD", () -> c.enabled, v -> c.enabled = v);
        toggle(right, 208, "Animaciones", () -> c.animations, v -> c.animations = v);
        toggle(left, 208 + STEP, "Notificaciones", () -> c.toasts, v -> c.toasts = v);
        toggle(right, 208 + STEP, "Compacto", () -> c.compact, v -> c.compact = v);

        // PIE
        track(ButtonWidget.builder(Text.literal("Reiniciar hoy"), b -> Tracker.resetToday())
                .dimensions(left, 0, COL_W, 20).build(), 258);
        track(ButtonWidget.builder(Text.literal("Hecho"), b -> this.close())
                .dimensions(right, 0, COL_W, 20).build(), 258);
    }

    private void track(ClickableWidget w, int rel) {
        this.addDrawableChild(w);
        items.add(w);
        relY.add(rel);
    }

    private void toggle(int x, int rel, String name, BooleanSupplier get, Consumer<Boolean> set) {
        track(ButtonWidget.builder(label(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(name, get.getAsBoolean()));
        }).dimensions(x, 0, COL_W, 20).build(), rel);
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    /** Sin blur: la vista previa del HUD se dibuja nítida en render(). */
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Config c = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        float t = Math.min(1f, (System.nanoTime() - openedAt) / 320_000_000f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        int slide = Math.round((1f - e) * 14);
        int accent = c.accentColor();

        // atenuado del fondo
        ctx.fill(0, 0, this.width, this.height, Gfx.a(0x99000000, e));

        // vista previa en vivo del HUD (datos reales si hay mundo, de ejemplo si no)
        boolean live = mc.world != null && Tracker.balance >= 0;
        Hud.draw(ctx, Hud.CONFIG, live ? Hud.Snapshot.live() : Hud.Snapshot.demo(), c.enabled ? 1f : 0.35f);

        int x = cardX, y = cardY + slide;

        // tarjeta
        Gfx.rrect(ctx, x - 1, y, CARD_W + 2, CARD_H + 3, 7, 0x22000000);
        Gfx.rrect(ctx, x - 2, y + 1, CARD_W + 4, CARD_H + 5, 8, 0x12000000);
        Gfx.rrectGrad(ctx, x, y, CARD_W, CARD_H, 6, 0xFF171920, 0xFF0F1015);
        Gfx.outline(ctx, x, y, CARD_W, CARD_H, 6, 0x2EFFFFFF);
        Gfx.hgrad(ctx, x + 6, y, CARD_W - 12, 2, accent, accent & 0xFFFFFF);

        // cabecera
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + PAD, y + 11);
        m.scale(1.4f, 1.4f);
        ctx.drawTextWithShadow(this.textRenderer, "DonutProfit", 0, 0, Gfx.t(accent, 1f));
        m.popMatrix();
        if (!version.isEmpty()) {
            String v = "v" + version;
            ctx.drawTextWithShadow(this.textRenderer, v, x + CARD_W - PAD - this.textRenderer.getWidth(v), y + 13, 0xFF6B7280);
        }
        ctx.drawTextWithShadow(this.textRenderer, "Los cambios se ven en vivo en el HUD", x + PAD, y + 28, 0xFF8B93A1);
        ctx.fill(x + PAD, y + 40, x + CARD_W - PAD, y + 41, 0x22FFFFFF);

        // secciones
        section(ctx, "MOSTRAR", x + PAD, y + 46, COL_W);
        section(ctx, "APARIENCIA", x + PAD + COL_W + GAP, y + 46, COL_W);
        section(ctx, "GENERAL", x + PAD, y + 196, CARD_W - PAD * 2);

        // widgets (siguen la animación de entrada)
        for (int i = 0; i < items.size(); i++) {
            ClickableWidget w = items.get(i);
            w.setY(cardY + relY.get(i) + slide);
            w.render(ctx, mouseX, mouseY, delta);
        }

        // velo de entrada: revela el contenido suavemente
        if (e < 1f) Gfx.rrect(ctx, x, y, CARD_W, CARD_H, 6, Gfx.a(0xFF131519, 1f - e));
    }

    private void section(DrawContext ctx, String name, int x, int y, int w) {
        ctx.drawTextWithShadow(this.textRenderer, name, x, y, 0xFF8B93A1);
        int tw = this.textRenderer.getWidth(name);
        ctx.fill(x + tw + 6, y + 4, x + w, y + 5, 0x22FFFFFF);
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    private static final class Slider extends SliderWidget {
        private final String name;
        private final float min, max;
        private final String format;
        private final Consumer<Float> setter;

        Slider(int x, int y, int w, int h, String name, float min, float max, float val, String format, Consumer<Float> setter) {
            super(x, y, w, h, Text.empty(), (val - min) / (max - min));
            this.name = name; this.min = min; this.max = max; this.format = format; this.setter = setter;
            updateMessage();
        }

        private float current() { return min + (float) this.value * (max - min); }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(name + ": " + String.format(Locale.ROOT, format, current())));
        }

        @Override
        protected void applyValue() { setter.accept(current()); }
    }
}
