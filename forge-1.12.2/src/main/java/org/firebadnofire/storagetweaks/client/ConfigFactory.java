package org.firebadnofire.storagetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.*;
import org.firebadnofire.storagetweaks.*;
import java.util.*;

public final class ConfigFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft minecraft) { }
    @Override public boolean hasConfigGui() { return true; }
    @Override public GuiScreen createConfigGui(GuiScreen parent) { return screen(parent); }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return Collections.emptySet(); }
    static GuiScreen screen(GuiScreen parent) {
        List<IConfigElement> categories=new ArrayList<>();
        for(String category:Settings.config.getCategoryNames()) categories.add(new ConfigElement(Settings.config.getCategory(category)));
        return new GuiConfig(parent,categories,StorageTweaks.ID,false,false,"Storage Tweaks - Punch: "+ClientProxy.capabilityStatus) {
            @Override public void onGuiClosed() { super.onGuiClosed(); Settings.refresh(); }
        };
    }
}
