package org.firebadnofire.storagetweaks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

/** Client features use vanilla packets; installing this mod on either side is optional. */
@Mod(modid = StorageTweaks.ID, name = "Fire's Storage Tweaks", version = "0.0.1",
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*",
        guiFactory = "org.firebadnofire.storagetweaks.client.ConfigFactory")
public final class StorageTweaks {
    public static final String ID = "firebadnofire_storage_tweaks";
    public static Logger LOG;
    @SidedProxy(clientSide = "org.firebadnofire.storagetweaks.client.ClientProxy",
            serverSide = "org.firebadnofire.storagetweaks.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        LOG = event.getModLog();
        Settings.load(event.getSuggestedConfigurationFile());
        org.firebadnofire.storagetweaks.server.PunchNetwork.init();
        proxy.preInit();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        org.firebadnofire.storagetweaks.server.PunchDepositCommand.registerPermission();proxy.init();
    }
    @Mod.EventHandler public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new org.firebadnofire.storagetweaks.server.PunchDepositCommand());
    }
}
