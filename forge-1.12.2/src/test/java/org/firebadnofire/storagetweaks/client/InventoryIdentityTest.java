package org.firebadnofire.storagetweaks.client;

import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryIdentityTest {
    @Test public void compoundOrderingDoesNotChangeIdentity() {
        NBTTagCompound first=new NBTTagCompound(),second=new NBTTagCompound();
        first.setString("display","Custom Name");first.setInteger("damage",12);
        second.setInteger("damage",12);second.setString("display","Custom Name");
        assertEquals(InventoryAdapter.canonical(first),InventoryAdapter.canonical(second));
    }
    @Test public void numericTypesAndListOrderRemainDistinct() {
        assertNotEquals(InventoryAdapter.canonical(new NBTTagByte((byte)1)),InventoryAdapter.canonical(new NBTTagInt(1)));
        NBTTagList first=new NBTTagList(),second=new NBTTagList();
        first.appendTag(new NBTTagString("a"));first.appendTag(new NBTTagString("b"));
        second.appendTag(new NBTTagString("b"));second.appendTag(new NBTTagString("a"));
        assertNotEquals(InventoryAdapter.canonical(first),InventoryAdapter.canonical(second));
    }
    @Test public void nestedCustomPropertiesRemainDistinct() {
        NBTTagCompound first=new NBTTagCompound(),second=new NBTTagCompound();
        NBTTagCompound capability=new NBTTagCompound();capability.setInteger("charge",42);
        first.setTag("ForgeCaps",capability);second.setTag("ForgeCaps",capability.copy());
        second.getCompoundTag("ForgeCaps").setInteger("charge",43);
        assertNotEquals(InventoryAdapter.canonical(first),InventoryAdapter.canonical(second));
    }
}
