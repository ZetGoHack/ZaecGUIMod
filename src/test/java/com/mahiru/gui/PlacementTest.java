package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlacementTest {
    private static Placement rotated(int x, int y, int w, int h, int rot) {
        Placement p = new Placement(x, y, w, h);
        p.rot = rot;
        return p;
    }

    @Test
    void unrotatedBoundsIsTheSameBox() {
        Placement p = new Placement(10, 20, 30, 40);
        assertSame(p, p.bounds());
    }

    @Test
    void rotationBy180KeepsTheBox() {
        Placement b = rotated(10, 20, 30, 40, 180).bounds();
        assertEquals(10, b.x, 1);
        assertEquals(20, b.y, 1);
        assertEquals(30, b.w, 1);
        assertEquals(40, b.h, 1);
    }

    @Test
    void rotationBy90SwapsSidesAroundTheCentre() {
        Placement b = rotated(0, 0, 100, 40, 90).bounds();
        assertEquals(40, b.w, 1);
        assertEquals(100, b.h, 1);
        assertEquals(50, b.x + b.w / 2.0, 1);   // the centre stays at (50, 20)
        assertEquals(20, b.y + b.h / 2.0, 1);
    }

    @Test
    void rotationBy45GrowsTheBox() {
        Placement b = rotated(0, 0, 100, 100, 45).bounds();
        assertTrue(b.w >= 141 && b.w <= 143, "w " + b.w);
        assertEquals(b.w, b.h);
    }

    @Test
    void negativeRotationGivesTheSameBoundsAsPositive() {
        Placement ba = rotated(5, 6, 70, 30, 30).bounds(), bb = rotated(5, 6, 70, 30, -30).bounds();
        assertEquals(ba.x, bb.x);
        assertEquals(ba.y, bb.y);
        assertEquals(ba.w, bb.w);
        assertEquals(ba.h, bb.h);
    }
}
