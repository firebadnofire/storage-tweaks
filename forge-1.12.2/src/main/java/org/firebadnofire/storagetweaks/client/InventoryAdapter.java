package org.firebadnofire.storagetweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.items.ItemHandlerHelper;
import org.firebadnofire.storagetweaks.inventory.*;
import java.util.*;

final class InventoryAdapter {
    static boolean supported(GuiContainer gui) {
        return gui.getClass() == GuiChest.class && gui.inventorySlots.getClass() == ContainerChest.class ||
                gui.getClass() == GuiShulkerBox.class && gui.inventorySlots.getClass() == ContainerShulkerBox.class ||
                gui.getClass() == GuiInventory.class && gui.inventorySlots.getClass() == ContainerPlayer.class;
    }
    static int[] group(Container container, boolean player, boolean hotbar) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.inventorySlots.get(i);
            boolean isPlayer = slot.inventory instanceof InventoryPlayer;
            if (player && isPlayer && slot.getSlotIndex() >= (hotbar ? 0 : 9) && slot.getSlotIndex() < 36 ||
                    !player && !isPlayer) ids.add(i);
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
    static org.firebadnofire.storagetweaks.inventory.Stack value(ItemStack stack) {
        if (stack.isEmpty()) return org.firebadnofire.storagetweaks.inventory.Stack.EMPTY;
        NBTTagCompound tag = stack.serializeNBT(); tag.removeTag("Count");
        return new org.firebadnofire.storagetweaks.inventory.Stack(canonical(tag), stack.getDisplayName(),
                Objects.requireNonNull(stack.getItem().getRegistryName()).toString(), stack.getCount(), stack.getMaxStackSize());
    }
    static Snapshot snapshot(Container container) {
        List<org.firebadnofire.storagetweaks.inventory.Stack> values = new ArrayList<>();
        int[] limits = new int[container.inventorySlots.size()];
        for (int i = 0; i < limits.length; i++) {
            Slot slot = container.inventorySlots.get(i);
            values.add(value(slot.getStack())); limits[i] = slot.getSlotStackLimit();
        }
        return new Snapshot(values, value(Minecraft.getMinecraft().player.inventory.getItemStack()), limits);
    }
    static void validate(Container container, Plan plan) {
        Map<String, ItemStack> prototypes = new HashMap<>();
        for (Slot slot : container.inventorySlots) if (!slot.getStack().isEmpty()) {
            ItemStack stack = slot.getStack(); String key = value(stack).key;
            ItemStack existing = prototypes.put(key, stack.copy());
            if (existing != null && stack.getMaxStackSize() > 1 && !ItemHandlerHelper.canItemStacksStack(existing, stack))
                throw new IllegalArgumentException("Item capabilities prevent safe stack comparison");
        }
        for (Plan.Click click : plan.clicks) {
            Slot slot = container.getSlot(click.slot);
            if (!slot.canTakeStack(Minecraft.getMinecraft().player)) throw new IllegalArgumentException("Restricted slot");
            org.firebadnofire.storagetweaks.inventory.Stack placed = click.after.slots.get(click.slot);
            if (!placed.empty()) {
                ItemStack prototype = prototypes.get(placed.key).copy(); prototype.setCount(placed.count);
                if (!slot.isItemValid(prototype) || placed.count > slot.getItemStackLimit(prototype))
                    throw new IllegalArgumentException("Item is not permitted in destination slot");
            }
        }
    }
    static String canonical(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            StringBuilder result = new StringBuilder("{");
            for (String key : new TreeSet<>(compound.getKeySet())) {
                String value = canonical(compound.getTag(key));
                result.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value);
            }
            return result.append('}').toString();
        }
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag; StringBuilder result = new StringBuilder("["+list.getTagType()+":");
            for (int i=0;i<list.tagCount();i++) { String value=canonical(list.get(i)); result.append(value.length()).append(':').append(value); }
            return result.append(']').toString();
        }
        return tag.getId() + ":" + tag.toString();
    }
}
