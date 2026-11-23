package org.firebadnofire.storagetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import org.firebadnofire.storagetweaks.Settings;
import org.firebadnofire.storagetweaks.inventory.*;

final class InventoryJob {
    private Plan plan;
    private GuiContainer screen;
    private net.minecraft.inventory.Container container;
    private int index, sentTick, nextTick, window, dimension;
    private Short action;
    private boolean waiting;
    boolean active() { return plan != null; }

    void start(GuiContainer screen, Plan plan, int tick) {
        if (active()) throw new IllegalArgumentException("Another inventory operation is running");
        container=screen==null?Minecraft.getMinecraft().player.inventoryContainer:InventoryAdapter.container(screen);
        InventoryAdapter.validate(container, plan);
        this.screen=screen; this.plan=plan; index=0; nextTick=tick;
        dimension=Minecraft.getMinecraft().player.dimension; window=container.windowId;
        waiting=false; action=null;
    }
    void outgoing(CPacketClickWindow packet) {
        if (active() && waiting && packet.getWindowId()==window) {
            if (action != null) { stop("Unexpected inventory click; stopped"); return; }
            action=packet.getActionNumber();
        }
    }
    void confirmed(SPacketConfirmTransaction packet, int tick) {
        if (!active() || !waiting || action==null || packet.getWindowId()!=window || packet.getActionNumber()!=action) return;
        if (!packet.wasAccepted()) { stop("Server rejected an inventory click; stopped for resynchronization"); return; }
        if (!InventoryAdapter.snapshot(container).equals(plan.clicks.get(index).after)) {
            stop("Inventory changed while waiting for the server; stopped"); return;
        }
        waiting=false; action=null; index++; nextTick=tick+Settings.pace;
    }
    void tick(int tick) {
        if (!active()) return;
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player==null || (screen!=null && !InventoryAdapter.supported(screen)) || mc.currentScreen!=screen || mc.player.openContainer!=(screen==null?container:screen.inventorySlots) ||
                mc.player.isDead || mc.player.dimension!=dimension || !Settings.enabled) { stop("Inventory operation interrupted"); return; }
        if (waiting) {
            if (tick-sentTick>=Settings.timeout) stop("Inventory acknowledgment timed out; no further clicks sent");
            return;
        }
        if (tick<nextTick) return;
        if (index==plan.clicks.size()) {
            if (Settings.notify) ClientProxy.say("Inventory operation complete");
            plan=null; screen=null; container=null; return;
        }
        Plan.Click click=plan.clicks.get(index);
        if (!InventoryAdapter.snapshot(container).equals(click.before)) { stop("Inventory changed; stopped before next click"); return; }
        waiting=true; action=null; sentTick=tick;
        net.minecraft.inventory.Container visible=mc.player.openContainer;
        try {
            // Creative wraps player slots; transact against the real window-0 slot IDs.
            mc.player.openContainer=container;
            mc.playerController.windowClick(window,click.slot,click.hotbarButton < 0 ? 0 : click.hotbarButton,click.hotbarButton < 0 ? ClickType.PICKUP : ClickType.SWAP,mc.player);
        } finally { mc.player.openContainer=visible; }
        if (!InventoryAdapter.snapshot(container).equals(click.after)) stop("Unexpected local click semantics; stopped");
    }
    void authoritativeUpdate() {
        if(!active()) return;
        Snapshot expected=waiting?plan.clicks.get(index).after:index<plan.clicks.size()?plan.clicks.get(index).before:plan.result;
        if(!InventoryAdapter.snapshot(container).equals(expected)) stop("Unexpected authoritative inventory update; stopped");
    }
    void stop(String reason) {
        if (active()) ClientProxy.say(reason + ". Cursor items are preserved; put them away normally.");
        plan=null; screen=null; container=null; waiting=false; action=null;
    }
}
