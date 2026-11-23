package org.firebadnofire.storagetweaks.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import java.util.*;

/** Protection integrations register one authoritative access check for each server mod they cover. */
public final class PunchAccess {
    public interface Policy { boolean mayDeposit(EntityPlayerMP player, BlockPos position); }
    private static final Map<String, Policy> POLICIES = new HashMap<>();
    private static final Set<String> BASE = new HashSet<>(Arrays.asList("minecraft","mcp","FML","forge","firebadnofire_storage_tweaks"));
    public static void register(String modId, Policy policy) {
        if (policy == null || POLICIES.putIfAbsent(modId, policy) != null)
            throw new IllegalArgumentException("Missing or duplicate punch access policy for " + modId);
    }
    public static String blocker() {
        for (ModContainer mod : Loader.instance().getModList())
            if (!BASE.contains(mod.getModId()) && !POLICIES.containsKey(mod.getModId()))
                return "No reviewed punch access integration for " + mod.getModId();
        return "";
    }
    static boolean allowed(EntityPlayerMP player, BlockPos pos) {
        if (!blocker().isEmpty()) return false;
        for (Policy policy : POLICIES.values()) if (!policy.mayDeposit(player,pos)) return false;
        return true;
    }
    private PunchAccess() { }
}
