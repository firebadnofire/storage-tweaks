package org.firebadnofire.storagetweaks.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

/** No Storage Tweaks references: isolates pack join failures from the mod under test. */
@Mod(modid="storage_baseline_tests",version="1",acceptableRemoteVersions="*")
public final class BaselineTestMod {
    private int ticks,joined;private boolean launched,done;
    private final boolean optionalServer="optional-server".equals(System.getProperty("storageTweaks.runtimeMode"));
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(net.minecraftforge.fml.common.Loader.isModLoaded("firebadnofire_storage_tweaks")) throw new IllegalStateException("Baseline requires Storage Tweaks absent");
        Minecraft.getMinecraft().gameSettings.pauseOnLostFocus=false;
        Minecraft.getMinecraft().gameSettings.setSoundLevel(net.minecraft.util.SoundCategory.MASTER,0F);
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done) return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        if(!launched && mc.currentScreen instanceof GuiMainMenu && ticks>40) {
            launched=true;
            if(optionalServer) mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(),mc,"127.0.0.1",25589));
            else mc.launchIntegratedServer("baseline-"+System.currentTimeMillis(),"Pack baseline",new WorldSettings(713,GameType.SURVIVAL,false,false,WorldType.FLAT));
        }
        if(launched && mc.player!=null && mc.world!=null && ++joined>200) {
            LogManager.getLogger("baseline").info(optionalServer?"OPTIONAL_SERVER_JOIN_PASSED":"BASELINE_PACK_JOIN_PASSED");done=true;mc.shutdown();
        }
        if(launched && (mc.currentScreen instanceof GuiDisconnected || ticks>1800)) {
            LogManager.getLogger("baseline").error("BASELINE_PACK_JOIN_FAILED: disconnected or timed out; inspect earlier exception");done=true;mc.shutdown();
        }
    }
}
