package com.mahiru.gui;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Never let a bug of ours crash the game: run hooks guarded, log once, switch a failing hook off after 5 failures. */
public final class Guard {
    public static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Integer> FAILS = new ConcurrentHashMap<>();

    public static boolean run(String what, Runnable r) {
        if (FAILS.getOrDefault(what, 0) >= 5) return false;
        try {
            r.run();
            return true;
        } catch (Throwable t) {
            int n = FAILS.merge(what, 1, Integer::sum);
            LOG.error("[ZaecGuiMod] {} failed ({}/5{})", what, n, n >= 5 ? ", disabled until restart" : "", t);
            return false;
        }
    }

    private Guard() {}
}
