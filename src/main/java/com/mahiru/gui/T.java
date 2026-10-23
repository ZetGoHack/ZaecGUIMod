package com.mahiru.gui;

import net.minecraft.client.resources.language.I18n;

/** Translation helper: the English text is the key, so a missing translation simply shows English. */
public final class T {
    public static String t(String s) { return I18n.get(s); }

    public static String f(String s, Object... args) { return I18n.get(s, args); }

    private T() {}
}
