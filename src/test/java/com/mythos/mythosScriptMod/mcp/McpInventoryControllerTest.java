package com.mythos.mythosScriptMod.mcp;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class McpInventoryControllerTest {
    @Test public void allPlayerAndCraftingSlotsMapExactlyOnce() {
        Set<Integer> seen = new HashSet<>();
        for(int slot=0;slot<46;slot++) assertTrue(seen.add(McpInventoryController.containerSlot(slot)));
        for(int slot=0;slot<46;slot++) assertTrue(seen.contains(slot));
        assertEquals(36,McpInventoryController.containerSlot(0));
        assertEquals(44,McpInventoryController.containerSlot(8));
        assertEquals(9,McpInventoryController.containerSlot(9));
        assertEquals(8,McpInventoryController.containerSlot(36)); // boots
        assertEquals(5,McpInventoryController.containerSlot(39)); // helmet
        assertEquals(45,McpInventoryController.containerSlot(40)); // offhand
        assertEquals(0,McpInventoryController.containerSlot(41)); // crafting output
        assertEquals(4,McpInventoryController.containerSlot(45));
        assertEquals(-999,McpInventoryController.containerSlot(-999));
    }
    @Test public void invalidSlotsNeverBecomeOutsideClicks() {
        for(int slot:new int[]{Integer.MIN_VALUE,-1000,-998,-1,46,Integer.MAX_VALUE}) {
            try {McpInventoryController.containerSlot(slot);fail("invalid slot accepted: "+slot);}
            catch(IllegalArgumentException expected) {}
        }
    }

    @Test public void vanillaSplitDistributionAndCollectPreserveItems() throws Exception {
        net.minecraft.init.Bootstrap.register();
        // Allocate an inert player: this is an offline vanilla-container test, with no world or network.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer)
                unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,net.minecraft.entity.player.EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        net.minecraft.inventory.Container container = new net.minecraft.inventory.Container() {
            { for(int i=0;i<36;i++) addSlotToContainer(new net.minecraft.inventory.Slot(player.inventory,i,0,0)); }
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer ignored) { return true; }
        };
        player.inventory.setInventorySlotContents(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.APPLE,11));
        container.slotClick(0,1,net.minecraft.inventory.ClickType.PICKUP,player);
        assertEquals(6,player.inventory.getItemStack().getCount());
        assertEquals(5,player.inventory.getStackInSlot(0).getCount());
        container.slotClick(1,1,net.minecraft.inventory.ClickType.PICKUP,player);
        assertEquals(1,player.inventory.getStackInSlot(1).getCount());
        container.slotClick(-999,0,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(2,1,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(3,1,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(-999,2,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        assertEquals(2,player.inventory.getStackInSlot(2).getCount());
        assertEquals(2,player.inventory.getStackInSlot(3).getCount());
        assertEquals(1,player.inventory.getItemStack().getCount());
        container.slotClick(6,0,net.minecraft.inventory.ClickType.PICKUP_ALL,player);
        assertEquals(11,player.inventory.getItemStack().getCount());
        container.slotClick(-999,4,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(4,5,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(5,5,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        container.slotClick(-999,6,net.minecraft.inventory.ClickType.QUICK_CRAFT,player);
        assertEquals(1,player.inventory.getStackInSlot(4).getCount());
        assertEquals(1,player.inventory.getStackInSlot(5).getCount());
        assertEquals(9,player.inventory.getItemStack().getCount());
    }
}
