// Jarrod Wardell - Created January 21st, 2021. Updated January 22nd, 2021.
package itemmerger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import org.apache.commons.lang3.tuple.Pair;

/*
    These were all private methods, but if a plugin is doing some weird stuff (like crop hoppers), having these public
    would allow for them to be supported by simply imitating an event call.
    However, there is nothing here to be extended, so it shouldn't be. Therefore, it is final.
*/

final class ItemEvents implements Listener { // not designed to be extended. what the heck are you trying to extend here, anyways?
    @EventHandler
    public static void onItemSpawn(ItemSpawnEvent spawned) { // emitted when an item is spawned or dropped by any method
        //getLogger().info("New Entity: " + spawned.getEntity().getItemStack().getType());
        ItemMerger.mergeNearby(spawned.getEntity());
    }

    @EventHandler
    public static void onItemPickup(EntityPickupItemEvent event) { // emitted when an entity picks up an item
        if (NBTHelper.hasNBT(event.getItem().getItemStack(), "itemmerger.CustomStack") && (NBTHelper.hasNBT(event.getItem().getItemStack(), "itemmerger.NoStack") ? !NBTHelper.getNBTBool(event.getItem().getItemStack(), "itemmerger.NoStack") : true)) { // only deal with customstacks that are not NoStacks
            event.setCancelled(true); // make sure the stack is not picked up
            ItemMerger.pickedUp((Player)event.getEntity(), event.getItem());
        }
    }

    @EventHandler
    public static void onItemPickupInventory(InventoryPickupItemEvent event) { // inventory has picked up an item
        if (NBTHelper.hasNBT(event.getItem().getItemStack(), "itemmerger.CustomStack") && (NBTHelper.hasNBT(event.getItem().getItemStack(), "itemmerger.NoStack") ? !NBTHelper.getNBTBool(event.getItem().getItemStack(), "itemmerger.NoStack") : true)) { // only deal with customstacks that aren't NoStack
            event.setCancelled(true); // make sure the stack is not removed
            Pair<ItemStack[], Integer> values = ItemMerger.updateInventory(event.getInventory(), event.getItem());
            event.getInventory().setContents(values.getKey()); // update inventory
            if (values.getValue() > 0) {
                event.getItem().getItemStack().setItemMeta(NBTHelper.setNBTInt(event.getItem().getItemStack(), "itemmerger.CustomStack", values.getValue()).getItemMeta()); // update the customstack
            } else {
                event.getItem().getItemStack().setAmount(0); // delete item
                ItemMerger.customstacks.remove(event.getItem()); // do not check on this item again - this has to happen here rather than in updateInventory or it causes a ConcurrentModificationException in the scheduler (could use a bool param, but I decided not to.)
            }
        }
    }
}
