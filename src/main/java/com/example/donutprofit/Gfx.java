package com.example.donutprofit;

import net.minecraft.client.gui.DrawContext;

/** Primitivas de dibujo: esquinas redondeadas, degradados y color. */
final class Gfx {
    private Gfx() { }

    /** Multiplica el alfa de un color ARGB. */
    static int a(int argb, float mul) {
        int al = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * mul)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** Igual que a(), pero con alfa mínimo para que el texto nunca se pinte opaco por error. */
    static int t(int argb, float mul) {
        int al = Math.max(5, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * mul)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
        int r = Math.round(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = Math.round((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(r * r - dy * dy) - 1e-4);
    }

    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int d = inset(r, i);
            c.fill(x + d, y + i, x + w - d, y + i + 1, color);
            c.fill(x + d, y + h - 1 - i, x + w - d, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    static void rrectGrad(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int yy = 0; yy < h; yy++) {
            int d = 0;
            if (yy < r) d = inset(r, yy);
            else if (yy >= h - r) d = inset(r, h - 1 - yy);
            c.fill(x + d, y + yy, x + w - d, y + yy + 1, mix(top, bottom, h <= 1 ? 0f : (float) yy / (h - 1)));
        }
    }

    static void outline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int d0 = inset(r, 0);
        c.fill(x + d0, y, x + w - d0, y + 1, color);
        c.fill(x + d0, y + h - 1, x + w - d0, y + h, color);
        for (int i = 1; i < r; i++) {
            int d = inset(r, i), p = inset(r, i - 1);
            int e = Math.max(p, d + 1);
            c.fill(x + d, y + i, x + e, y + i + 1, color);
            c.fill(x + w - e, y + i, x + w - d, y + i + 1, color);
            c.fill(x + d, y + h - 1 - i, x + e, y + h - i, color);
            c.fill(x + w - e, y + h - 1 - i, x + w - d, y + h - i, color);
        }
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** Degradado horizontal en tiras de 2 px. */
    static void hgrad(DrawContext c, int x, int y, int w, int h, int left, int right) {
        for (int xx = 0; xx < w; xx += 2) {
            c.fill(x + xx, y, x + Math.min(xx + 2, w), y + h, mix(left, right, w <= 1 ? 0f : (float) xx / (w - 1)));
        }
    }
}
