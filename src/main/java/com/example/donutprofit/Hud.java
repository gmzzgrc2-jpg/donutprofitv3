package com.example.donutprofit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** HUD: panel con fade, números animados, gráfico, destello al cambiar y toasts de variación. */
public final class Hud {
    public record Snapshot(double balance, double today, double gambling, int wins, int losses,
                           boolean sessionActive, double sessionProfit, long sessionMs,
                           double[] graph, long deltaSeq, double lastDelta) {

        public static Snapshot live() {
            Stats s = Tracker.s;
            Config c = Config.get();
            return new Snapshot(Tracker.balance, Tracker.todayChange(), s.gambling, s.wins, s.losses,
                    s.sessionActive, s.sessionProfit,
                    s.sessionActive ? System.currentTimeMillis() - s.sessionStartMs : 0,
                    Tracker.graph(c.showGambling), Tracker.deltaSeq, Tracker.lastDelta);
        }

        /** Datos de ejemplo para la pantalla de ajustes cuando no hay mundo cargado. */
        public static Snapshot demo() {
            long ms = System.currentTimeMillis();
            double t = ms / 1000.0;
            double gamb = 350_000 + 120_000 * Math.sin(t * 0.7);
            double[] g = new double[40];
            for (int i = 0; i < g.length; i++) g[i] = 260_000 * Math.sin(i * 0.33 + 0.4) + i * 9_000;
            g[g.length - 1] = gamb;
            long seq = ms / 3500;
            double delta = (seq % 3 == 0) ? -180_000 : 240_000 + (seq % 5) * 40_000;
            return new Snapshot(584_000_000 + gamb, 1_200_000 + gamb, gamb, 7, 4, true, gamb, ms % 3_600_000,
                    g, seq, delta);
        }
    }

    static final class Anim {
        double cur, tgt;
        float flash;
        boolean init;

        void update(double target, float dt, boolean animate) {
            if (!init || !animate) { cur = target; tgt = target; init = true; return; }
            if (Math.abs(target - tgt) > 0.5) flash = 1f;
            tgt = target;
            double k = 1 - Math.exp(-dt * 9);
            cur += (tgt - cur) * k;
            if (Math.abs(tgt - cur) < 0.5) cur = tgt;
            flash = Math.max(0f, flash - dt * 2.2f);
        }
    }

    private static final class Toast {
        final String text; final int color; float age;
        Toast(String text, int color) { this.text = text; this.color = color; }
    }

    public static final class State {
        float fade, widthAnim;
        long last = System.nanoTime();
        long seenSeq = -1;
        final Anim bal = new Anim(), today = new Anim(), gamb = new Anim(), sess = new Anim();
        final List<Toast> toasts = new ArrayList<>();

        void reset() {
            bal.init = false; today.init = false; gamb.init = false; sess.init = false;
            widthAnim = 0; toasts.clear(); seenSeq = -1;
        }
    }

    public static final State LIVE = new State();
    public static final State CONFIG = new State();

    private record Row(String label, String value, int color, float flash) { }

    private static final int LABEL = 0xFF8B93A1;
    private static final int NEUTRAL = 0xFFE5E7EB;
    private static final int POS = 0xFF4ADE80;
    private static final int NEG = 0xFFF87171;
    private static final int IDLE = 0xFF6B7280;

    private Hud() { }

    /** Llamado cada frame desde HudRenderCallback. */
    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof ConfigScreen) return; // la pantalla de ajustes dibuja su propia vista previa
        Config c = Config.get();
        boolean want = c.enabled && mc.player != null && !mc.options.hudHidden;
        draw(ctx, LIVE, Snapshot.live(), want ? 1f : 0f);
    }

    public static void draw(DrawContext ctx, State st, Snapshot sn, float goal) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config c = Config.get();
        TextRenderer tr = mc.textRenderer;

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - st.last) / 1_000_000_000f);
        st.last = now;

        if (c.animations) {
            float step = dt * 5f;
            st.fade = st.fade < goal ? Math.min(goal, st.fade + step) : Math.max(goal, st.fade - step);
        } else {
            st.fade = goal;
        }
        if (st.fade <= 0.01f) { st.reset(); return; }
        float al = st.fade;
        float rise = goal <= 0f ? 1f : 1f - (float) Math.pow(1f - Math.min(1f, st.fade / goal), 3);

        // ---- valores animados ----
        boolean an = c.animations;
        st.bal.update(Math.max(sn.balance(), 0), dt, an);
        st.today.update(sn.today(), dt, an);
        st.gamb.update(sn.gambling(), dt, an);
        st.sess.update(sn.sessionProfit(), dt, an);

        // ---- toasts ----
        if (st.seenSeq < 0) {
            st.seenSeq = sn.deltaSeq();
        } else if (sn.deltaSeq() != st.seenSeq) {
            st.seenSeq = sn.deltaSeq();
            if (c.toasts && Math.abs(sn.lastDelta()) > 0) {
                st.toasts.add(new Toast(Tracker.fmt(sn.lastDelta(), true), sn.lastDelta() > 0 ? POS : NEG));
                while (st.toasts.size() > 4) st.toasts.remove(0);
            }
        }
        for (Iterator<Toast> it = st.toasts.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            t.age += dt;
            if (t.age > 1.8f) it.remove();
        }

        // ---- contenido ----
        boolean hero = !c.compact && (c.showGambling || c.showToday);
        boolean heroGamb = c.showGambling;
        Anim ha = heroGamb ? st.gamb : st.today;

        List<Row> rows = new ArrayList<>();
        if (c.showBalance)
            rows.add(new Row("Balance", sn.balance() < 0 ? "—" : "$" + Tracker.fmt(st.bal.cur, false), NEUTRAL, st.bal.flash));
        if (c.showToday && !(hero && !heroGamb))
            rows.add(new Row("Hoy", Tracker.fmt(st.today.cur, true), tone(st.today.cur), st.today.flash));
        if (c.showGambling && !hero)
            rows.add(new Row("Gambling", Tracker.fmt(st.gamb.cur, true), tone(st.gamb.cur), st.gamb.flash));
        if (c.showWinLoss) {
            int total = sn.wins() + sn.losses();
            String wl = sn.wins() + " / " + sn.losses() + (total > 0 ? "  " + Math.round(100.0 * sn.wins() / total) + "%" : "");
            rows.add(new Row("W / L", wl, NEUTRAL, 0f));
        }
        if (c.showSession && sn.sessionActive())
            rows.add(new Row("Sesión", Tracker.fmt(st.sess.cur, true), tone(st.sess.cur), st.sess.flash));
        boolean graph = c.showGraph;

        // ---- medidas ----
        int pad = 8, rowH = 11, gap = 14;
        String title = "DonutProfit";
        String status = sn.sessionActive() ? Tracker.time(sn.sessionMs()) : "IDLE";
        int statusW = tr.getWidth(status);
        int headerW = tr.getWidth(title) + 16 + statusW + 8;

        float heroScale = 1.6f;
        String heroNum = Tracker.fmt(ha.cur, true);
        int heroW = hero ? 12 + Math.round(tr.getWidth(heroNum) * heroScale) : 0;

        int labelW = 0, valueW = 0;
        for (Row r : rows) {
            labelW = Math.max(labelW, tr.getWidth(r.label()));
            valueW = Math.max(valueW, tr.getWidth(r.value()));
        }
        int rowsW = rows.isEmpty() ? 0 : labelW + gap + valueW;
        int inner = Math.max(Math.max(headerW, heroW), Math.max(rowsW, 104));
        int targetW = inner + pad * 2;
        if (st.widthAnim <= 0f || !c.animations) st.widthAnim = targetW;
        else st.widthAnim += (targetW - st.widthAnim) * (float) (1 - Math.exp(-dt * 12));
        int w = Math.round(st.widthAnim);

        boolean anyBlock = hero || graph || !rows.isEmpty();
        int h = pad + 10;
        if (anyBlock) h += 4 + 1 + 5;
        if (hero) h += 23 + 6;
        if (graph) h += 16 + 6;
        if (!rows.isEmpty()) h += rows.size() * rowH - 3;
        else if (anyBlock) h -= 6;
        h += pad;

        // ---- posición ----
        float sc = c.scale;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float pw = w * sc, ph = h * sc;
        float px = c.anchor.right() ? sw - pw - c.offsetX : c.offsetX;
        float py = c.anchor.bottom() ? sh - ph - c.offsetY : c.offsetY;
        float slide = (1f - rise) * 10f;
        py += c.anchor.bottom() ? slide : -slide;

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px, py);
        m.scale(sc, sc);

        // ---- fondo ----
        float op = c.opacity;
        Gfx.rrect(ctx, -1, 0, w + 2, h + 2, 5, Gfx.a(0x18000000, al));
        Gfx.rrect(ctx, -2, 1, w + 4, h + 4, 6, Gfx.a(0x0C000000, al));
        Gfx.rrectGrad(ctx, 0, 0, w, h, 4,
                Gfx.a(0xFF181A21, op * al), Gfx.a(0xFF0C0D11, op * al));
        Gfx.outline(ctx, 0, 0, w, h, 4, Gfx.a(0x2AFFFFFF, al));
        int accent = c.accentColor();
        Gfx.hgrad(ctx, 4, 0, w - 8, 1, Gfx.a(accent, al), Gfx.a(accent & 0xFFFFFF, 0f));

        // ---- cabecera ----
        int y = pad;
        ctx.drawTextWithShadow(tr, title, pad, y, Gfx.t(accent, al));
        boolean active = sn.sessionActive();
        float pulse = active ? 0.55f + 0.45f * (float) Math.sin(now / 2.5e8) : 1f;
        int dotCol = Gfx.a(active ? POS : IDLE, al * pulse);
        int dx = w - pad - statusW - 9, dy = y + 2;
        ctx.fill(dx + 1, dy, dx + 4, dy + 5, dotCol);
        ctx.fill(dx, dy + 1, dx + 5, dy + 4, dotCol);
        ctx.drawTextWithShadow(tr, status, w - pad - statusW, y, Gfx.t(active ? NEUTRAL : IDLE, al));
        y += 10;

        int toastY = y;
        if (anyBlock) {
            y += 4;
            ctx.fill(pad, y, w - pad, y + 1, Gfx.a(0x22FFFFFF, al));
            y += 1 + 5;
        }

        // ---- hero ----
        if (hero) {
            int col = Gfx.mix(tone(ha.cur), 0xFFFFFFFF, ha.flash * 0.7f);
            arrow(ctx, pad, y + 4, ha.cur, Gfx.a(col, al));
            m.pushMatrix();
            m.translate(pad + 12, y);
            m.scale(heroScale, heroScale);
            ctx.drawTextWithShadow(tr, heroNum, 0, 0, Gfx.t(col, al));
            m.popMatrix();
            m.pushMatrix();
            m.translate(pad, y + 15);
            m.scale(0.75f, 0.75f);
            ctx.drawTextWithShadow(tr, heroGamb ? "GAMBLING · HOY" : "PROFIT · HOY", 0, 0, Gfx.t(LABEL, al));
            m.popMatrix();
            y += 23 + 6;
        }

        // ---- gráfico ----
        if (graph) {
            drawGraph(ctx, sn.graph(), pad, y, w - pad * 2, 16, al);
            y += 16 + 6;
        }

        // ---- filas ----
        for (Row r : rows) {
            ctx.drawTextWithShadow(tr, r.label(), pad, y, Gfx.t(LABEL, al));
            int vc = Gfx.mix(r.color(), 0xFFFFFFFF, r.flash() * 0.7f);
            ctx.drawTextWithShadow(tr, r.value(), w - pad - tr.getWidth(r.value()), y, Gfx.t(vc, al));
            y += rowH;
        }

        // ---- toasts ----
        boolean right = c.anchor.right();
        for (Toast t : st.toasts) {
            float p = t.age / 1.8f;
            float a2 = (1f - p) * Math.min(1f, t.age * 8f) * al;
            if (a2 <= 0.02f) continue;
            float up = (1f - (float) Math.pow(1f - p, 3)) * 20f;
            float ts = 1.15f;
            int tw = Math.round(tr.getWidth(t.text) * ts);
            int bx = right ? -(tw + 16) : (w + 6);
            int by = Math.round(toastY - 2 - up);
            Gfx.rrect(ctx, bx, by, tw + 10, 15, 3, Gfx.a(0xE00E0F13, a2));
            Gfx.outline(ctx, bx, by, tw + 10, 15, 3, Gfx.a(t.color, a2 * 0.6f));
            m.pushMatrix();
            m.translate(bx + 5, by + 3);
            m.scale(ts, ts);
            ctx.drawTextWithShadow(tr, t.text, 0, 0, Gfx.t(t.color, a2));
            m.popMatrix();
        }

        m.popMatrix();
    }

    private static void arrow(DrawContext ctx, int x, int y, double v, int color) {
        if (v > 0.5) {
            for (int i = 0; i < 4; i++) ctx.fill(x + 3 - i, y + i, x + 4 + i, y + i + 1, color);
        } else if (v < -0.5) {
            for (int i = 0; i < 4; i++) ctx.fill(x + i, y + i, x + 7 - i, y + i + 1, color);
        } else {
            ctx.fill(x + 1, y + 1, x + 6, y + 3, color);
        }
    }

    private static void drawGraph(DrawContext ctx, double[] g, int x, int y, int w, int h, float al) {
        int mid = y + h / 2;
        for (int xx = x; xx < x + w; xx += 4) {
            ctx.fill(xx, mid, Math.min(xx + 2, x + w), mid + 1, Gfx.a(0x33FFFFFF, al));
        }
        if (g == null || g.length < 2 || w < 2) return;
        double max = 1;
        for (double v : g) max = Math.max(max, Math.abs(v));
        int n = g.length;
        for (int xx = 0; xx < w; xx++) {
            double pos = (double) xx / (w - 1) * (n - 1);
            int i0 = (int) Math.floor(pos);
            int i1 = Math.min(n - 1, i0 + 1);
            double v = g[i0] + (g[i1] - g[i0]) * (pos - i0);
            int bar = (int) Math.round(Math.abs(v) / max * (h / 2.0 - 1));
            if (bar <= 0) continue;
            if (v >= 0) {
                ctx.fill(x + xx, mid - bar, x + xx + 1, mid, Gfx.a(POS, 0.45f * al));
                ctx.fill(x + xx, mid - bar, x + xx + 1, mid - bar + 1, Gfx.a(POS, al));
            } else {
                ctx.fill(x + xx, mid + 1, x + xx + 1, mid + 1 + bar, Gfx.a(NEG, 0.45f * al));
                ctx.fill(x + xx, mid + bar, x + xx + 1, mid + bar + 1, Gfx.a(NEG, al));
            }
        }
    }

    private static int tone(double v) {
        return v > 0.5 ? POS : (v < -0.5 ? NEG : NEUTRAL);
    }
}
