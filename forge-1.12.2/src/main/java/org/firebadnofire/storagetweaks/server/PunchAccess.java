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
    public static void registerReviewedContentMods() {
        ModContainer mod=Loader.instance().getIndexedModList().get("travelersbackpack");
        if(mod==null || POLICIES.containsKey("travelersbackpack")) return;
        // This exact content-only build has no vanilla-chest access/protection hooks.
        // Do not infer permission for a different version or a modified distribution.
        try(java.io.InputStream input=new java.io.FileInputStream(mod.getSource())) {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[8192];int read;
            while((read=input.read(buffer))!=-1) digest.update(buffer,0,read);
            StringBuilder hex=new StringBuilder();
            for(byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
            if(hex.toString().equals("39a7914b5a55b7d5ad5d1192c38a66b926895317e0426a359a529de2f8028581")) {
                register("travelersbackpack",(player,pos)->true);
                org.firebadnofire.storagetweaks.StorageTweaks.LOG.info("Reviewed Traveler's Backpack 1.0.35 build: vanilla-storage punch remains subject to all access checks");
            } else org.firebadnofire.storagetweaks.StorageTweaks.LOG.warn("Unreviewed Traveler's Backpack build: punch remains unavailable");
        } catch(java.io.IOException | java.security.GeneralSecurityException failure) {
            org.firebadnofire.storagetweaks.StorageTweaks.LOG.warn("Cannot verify Traveler's Backpack build; punch remains unavailable",failure);
        }
    }
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
