package com.mahiru.gui;

import java.util.Arrays;

/** Finds where an art should sit inside a menu panel so faces avoid slots, labels and widgets. */
public class Placement {
    public int x, y, w, h;
    /** clockwise degrees around the centre of the box, -180..180 */
    public int rot;
    public boolean flip;
    /** mirrored top-bottom */
    public boolean flipY;

    /** Axis-aligned box that contains the rotated art. */
    public Placement bounds() {
        if (rot == 0) return this;
        double r = Math.toRadians(rot), c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
        double bw = w * c + h * s, bh = w * s + h * c;
        double cx = x + w / 2.0, cy = y + h / 2.0;
        int x0 = (int) Math.floor(cx - bw / 2), y0 = (int) Math.floor(cy - bh / 2);
        int x1 = (int) Math.ceil(cx + bw / 2), y1 = (int) Math.ceil(cy + bh / 2);
        return new Placement(x0, y0, x1 - x0, y1 - y0);
    }

    public Placement() { }

    public Placement(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    /** Art never touches the bevelled panel border. */
    public static final int INSET = 4;

    private static final int PAD = 200;

    public static Placement find(Arts.Art art, Masks.Mask m) {
        return find(art, m, art.flipped(), false);
    }

    public static Placement find(Arts.Art art, Masks.Mask m, boolean flip) {
        return find(art, m, flip, false);
    }

    public static Placement find(Arts.Art art, Masks.Mask m, boolean flip, boolean flipY) {
        int pw = m.w, ph = m.h;
        float[][] wt = new float[ph][pw];
        for (float[] row : wt) Arrays.fill(row, 1f);
        for (int i = 0; i < m.rects.length; i++) {
            int[] r = m.rects[i];
            boolean slot = m.kinds[i] == 's';
            for (int yy = Math.max(0, r[1]); yy < Math.min(ph, r[1] + r[3]); yy++)
                for (int xx = Math.max(0, r[0]); xx < Math.min(pw, r[0] + r[2]); xx++)
                    wt[yy][xx] = slot ? Math.min(wt[yy][xx], 0.7f) : -1.2f;
        }
        // padded + 5x5 box blur: thin borders do not count as UI, big blocks do
        float[][] wp = new float[ph + 2 * PAD][pw + 2 * PAD];
        for (float[] row : wp) Arrays.fill(row, -1f);
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                float s = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int yy = y + dy, xx = x + dx;
                        s += (yy < 0 || yy >= ph || xx < 0 || xx >= pw) ? -1f : wt[yy][xx];
                    }
                }
                wp[PAD + y][PAD + x] = s / 25f;
            }
        }
        float[][] al = art.alpha();
        int ah = al.length, aw = al[0].length;
        int innerW = pw - 2 * INSET, innerH = ph - 2 * INSET;
        float best = -1e30f;
        int bx = INSET, by = INSET, bw = innerW, bh = innerH;
        for (float hf = 0.7f; hf <= 1.001f; hf += 0.05f) {
            int hh = Math.min(innerH, Math.round(ph * hf));
            int ww = Math.round(hh * (float) art.sw / art.sh);
            if (ww > innerW) { ww = innerW; hh = Math.round(ww * (float) art.sh / art.sw); }
            int rows = Math.max(1, hh / 2), cols = Math.max(1, ww / 2);
            float[][] samp = new float[rows][cols];
            for (int yy = 0; yy < rows; yy++) {
                float vw = yy * 2 < hh * 0.4f ? 3f : 1f;
                for (int xx = 0; xx < cols; xx++)
                    {
                    int col = Math.min(aw - 1, xx * 2 * aw / ww);
                    int row = Math.min(ah - 1, yy * 2 * ah / hh);
                    samp[yy][xx] = al[flipY ? ah - 1 - row : row][flip ? aw - 1 - col : col] * vw;
                }
            }
            int[] dys = { -Math.round(hh * .12f), -Math.round(hh * .06f), 0 };
            for (int dyv : dys) {
                int y = Math.max(INSET, Math.min(ph - INSET - hh, ph - INSET - hh + dyv));
                for (int x = INSET; x <= pw - INSET - ww; x += 2) {
                    float sc = 0;
                    for (int yy = 0; yy < rows; yy++) {
                        float[] wrow = wp[PAD + y + yy * 2];
                        float[] srow = samp[yy];
                        for (int xx = 0; xx < cols; xx++) sc += srow[xx] * wrow[PAD + x + xx * 2];
                    }
                    if (sc > best) { best = sc; bx = x; by = y; bw = ww; bh = hh; }
                }
            }
        }
        return new Placement(bx, by, bw, bh);
    }
}
