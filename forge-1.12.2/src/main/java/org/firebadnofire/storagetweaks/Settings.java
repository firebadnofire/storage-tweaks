package org.firebadnofire.storagetweaks;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class Settings {
    public static Configuration config;
    public static boolean enabled, chestButtons, inventorySort, finder, punch, hover, caseSensitive, notify;
    public static int pace, timeout, searchRadius, searchBlocks, searchMillis, results, interactionPace;
    public static String[] storeExclusions, punchExclusions;
    public static boolean serverPunch;
    public static void load(File file) { config = new Configuration(file); config.load(); refresh(); }
    public static void refresh() {
        enabled = config.getBoolean("enabled", "client", true, "Enable client features");
        chestButtons = config.getBoolean("chestButtons", "client", true, "Take, Store, Sort");
        inventorySort = config.getBoolean("inventorySort", "client", true, "Sort main 27 slots only");
        finder = config.getBoolean("finder", "client", true, "Enable finder commands unless clientcommands is present");
        punch = config.getBoolean("punch", "client", true, "Requires compatible server");
        hover = config.getBoolean("hover", "client", true, "Punch availability label");
        caseSensitive = config.getBoolean("caseSensitive", "inventory", false, "Name sort comparison");
        notify = config.getBoolean("notify", "inventory", false, "Report successful inventory jobs");
        pace = config.getInt("clickPaceTicks", "inventory", 1, 1, 20, "Minimum ticks between acknowledged clicks");
        timeout = config.getInt("responseTimeoutTicks", "inventory", 100, 20, 600, "Stop instead of waiting indefinitely");
        storeExclusions = config.getStringList("storeExclusions", "inventory", new String[0], "Exact registry IDs excluded from Store");
        punchExclusions = config.getStringList("punchExclusions", "punch", new String[0], "Additional protected registry IDs");
        serverPunch = config.getBoolean("enabled", "server", true, "Offer server-authoritative punch deposits");
        searchRadius = config.getInt("radius", "finder", 64, 1, 256, "Also limited to loaded/rendered chunks");
        searchBlocks = config.getInt("blocksPerTick", "finder", 4096, 64, 65536, "Hard work limit");
        searchMillis = config.getInt("millisecondsPerTick", "finder", 2, 1, 10, "Time work limit");
        results = config.getInt("maxResults", "finder", 20, 1, 100, "Displayed results");
        interactionPace = config.getInt("interactionPaceTicks", "finder", 4, 4, 100, "Minimum spacing between container interactions");
        if (config.hasChanged()) config.save();
    }
}
