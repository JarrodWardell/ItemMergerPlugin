package itemmerger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.*;

import net.minecraft.server.v1_12_R1.NBTTagCompound;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

/*
    To Do:
        - Add command to create custom stack in inventory out of held item (Will require ensuring we add all summoned customstacks to list)
        - Add config for how often task scheduler should run and how large the radius is for grouping items
        - Add a max customitem limit that can be defined in config (defaults at int max. currently it would just roll over)
        - Add command to toggle scheduler
        - Add command to toggle creating new stacks
*/

public class Main extends JavaPlugin implements Listener {
    public static Plugin plugin;
    private List<Item> customstacks = new ArrayList<Item>();

    @Override
    public void onEnable() { // startup
        getLogger().info("ItemMerger Started!");
        getServer().getPluginManager().registerEvents(this, this);

        plugin = this;
        new BukkitRunnable() { // used for scheduling runs through list of all customstacks
            @Override
            public void run() {
                customstacks.removeAll(Collections.singleton(null)); // get rid of items that were already deleted
                List<Item> invalidstacks = new ArrayList<Item>(); // this and next 8 lines solve issue of some items still existing within list upon removal, causing bugs (and, more concerningly, console warns, which are ugly)
                for (Item item : customstacks) {
                    if (!item.isValid() || item == null) {
                        invalidstacks.add(item);
                    }
                }
                for (Item item : invalidstacks) {
                    customstacks.remove(item);
                }
                for (Item item : customstacks) { // iterate through list
                    mergeNearby(item);
                    if (item.getPickupDelay() == 0) {
                        for (Entity entity : item.getNearbyEntities(1.425, 1.425, 1.425)) {
                            if (entity instanceof Player) {
                                pickedUp((Player)entity, item);
                            }
                        }
                        }
                }
            }
        }.runTaskTimer(this, 20, 20);
    }


    // Simple helper function that just checks if the items are the same aside from the CustomStack tag
    private boolean sameItem(ItemStack item1, ItemStack item2) {
        return (removeNBT(item1, "itemmerger.CustomStack").isSimilar(removeNBT(item2, "itemmerger.CustomStack")));
    }

    // Helper function to see if the stack has a specific custom tag
    private boolean hasNBT(ItemStack item, String tag) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        return nmsItem.hasTag() && nmsItem.getTag().hasKey(tag);
    }

    // Helper function to read an Integer NBT tag
    private Integer getNBTInt(ItemStack item, String tag) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        return nmsItem.hasTag() ? (nmsItem.getTag().hasKey(tag) ? nmsItem.getTag().getInt(tag) : 0) : 0;
    }

    // Helper function to read an Integer NBT tag
    private Boolean getNBTBool(ItemStack item, String tag) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        return nmsItem.hasTag() ? (nmsItem.getTag().hasKey(tag) ? nmsItem.getTag().getBoolean(tag) : false) : false;
    }

    // Helper function to remove a specific NBT tag outright
    private ItemStack removeNBT(ItemStack item, String tag) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        NBTTagCompound comp = nmsItem.hasTag() ? nmsItem.getTag() : new NBTTagCompound();
        comp.remove(tag);
        nmsItem.setTag(comp);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    // Helper function to make or set an NBT tag to supplied value
    private ItemStack setNBTInt(ItemStack item, String tag, int value) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        NBTTagCompound comp = nmsItem.hasTag() ? nmsItem.getTag() : new NBTTagCompound();
        comp.setInt(tag, value);
        nmsItem.setTag(comp);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    // Helper function to make or set an NBT tag to supplied value
    private ItemStack setNBTBool(ItemStack item, String tag, Boolean value) {
        net.minecraft.server.v1_12_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        NBTTagCompound comp = nmsItem.hasTag() ? nmsItem.getTag() : new NBTTagCompound();
        comp.setBoolean(tag, value);
        nmsItem.setTag(comp);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    // This is where the magic happens. Merges all nearby stackable items
    public void mergeNearby(Item item) {
        if (item.getItemStack().getMaxStackSize() != 1 && item.getItemStack().getMaxStackSize() != 0 && hasNBT(item.getItemStack(), "itemmerger.NoStack") ? !getNBTBool(item.getItemStack(), "itemmerger.NoStack") : true) { // make sure item is not unique/unstackable (Maybe make this configurable (let certain items stack, deny others stacking. wouldn't be too hard, just check if it is on denied material list))
            int items = item.getItemStack().getAmount();
            if (hasNBT(item.getItemStack(), "itemmerger.CustomStack")) { // see if item is already an item stack. if it is, just multiply item by how much each stack is worth
                items = items * getNBTInt(item.getItemStack(), "itemmerger.CustomStack");
                item.getItemStack().setAmount(1);
            } else {
                customstacks.add(item); // add to the checker to make sure the plugin merges it
            }
            for (Entity entity : item.getNearbyEntities(5, 5, 5)) { // possibly make this configurable later. get entities in nearby area
                if (entity instanceof Item) { // entity is an item
                    Item near = (Item)entity;
                    if (near.getItemStack().getType() == item.getItemStack().getType()) { // is same type of item
                        if (sameItem(near.getItemStack(), item.getItemStack()) && hasNBT(near.getItemStack(), "itemmerger.NoStack") ? !getNBTBool(near.getItemStack(), "itemmerger.NoStack") : true) { // merge items if they are of the same type and do not have nostack tag
                            if (hasNBT(near.getItemStack(), "itemmerger.CustomStack")) { // item found is custom stack
                                items += getNBTInt(near.getItemStack(), "itemmerger.CustomStack") * near.getItemStack().getAmount(); // multiply in case of fringe case that somehow a customstack stacked
                                near.getItemStack().setAmount(0); // delete stack
                            } else { // item is same but is not stack
                                items += near.getItemStack().getAmount();
                                near.getItemStack().setAmount(0); // delete stack
                            }
                        }
                    }
                }
            }
            item.getItemStack().setItemMeta(setNBTInt(item.getItemStack(), "itemmerger.CustomStack", items).getItemMeta()); // set the NBT tag
            item.getItemStack().setAmount(1); // make sure there is only 1 item in the customstack stack
        }
    }

    // used to insert the customstack items into a players inventory. it is assumed that items passed ARE custom stacks.
    private void pickedUp(Player player, Item item) {
        Pair<ItemStack[], Integer> values = updateInventory(player.getInventory(), item);
        if (getNBTInt(item.getItemStack(), "itemmerger.CustomStack") != values.getValue()) { // fixes hand waving bug by just not updating the inventory if it isn't updated.
            player.getInventory().setContents(values.getKey()); // update inventory
        }
        if (values.getValue() >= 0) {
            item.getItemStack().setItemMeta(setNBTInt(item.getItemStack(), "itemmerger.CustomStack", values.getValue()).getItemMeta()); // update the customstack
        } else {
            customstacks.remove(item); // do not check on this item again - this has to happen here rather than in updateInventory or it causes a ConcurrentModificationException in the scheduler (could use a bool param, but I decided not to.)
        }
    }

    private Pair<ItemStack[], Integer> updateInventory(Inventory inputinventory, Item item) {
        Integer items = getNBTInt(item.getItemStack(), "itemmerger.CustomStack"); // get how many items are in the stack
        ItemStack[] inventory = new ItemStack[inputinventory.getContents().length]; // array that will be used to update inventory
        for (int pos = 0; pos < inventory.length; pos++) {
            if ((inputinventory.getItem(pos) == null ? true : (sameItem(item.getItemStack(), inputinventory.getItem(pos)) && inputinventory.getItem(pos).getAmount() < inputinventory.getItem(pos).getMaxStackSize())) && pos < 36 && items > 0) { //
                Integer curAmount = inputinventory.getItem(pos) == null ? 0 : inputinventory.getItem(pos).getAmount(); // get the amount in this slot, or set it to 0
                if (items - (item.getItemStack().getMaxStackSize() - curAmount) > 0) { // there are more items than would take to fill the slot
                    items -= inputinventory.getItem(pos) != null ? item.getItemStack().getMaxStackSize() - inputinventory.getItem(pos).getAmount() : item.getItemStack().getMaxStackSize();
                    inventory[pos] = removeNBT(item.getItemStack(), "itemmerger.CustomStack");
                    inventory[pos].setAmount(item.getItemStack().getMaxStackSize());
                } else {
                    inventory[pos] = removeNBT(item.getItemStack(), "itemmerger.CustomStack");
                    inventory[pos].setAmount(inputinventory.getItem(pos) == null ? items : items + inputinventory.getItem(pos).getAmount());
                    items = 0;
                    item.getItemStack().setAmount(0);
                }
            } else {
                if (inputinventory.getItem(pos) != null) {
                    inventory[pos] = inputinventory.getItem(pos);
                }
            }
        }
        return new ImmutablePair<ItemStack[], Integer>(inventory, items);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent spawned) { // emitted when an item is spawned or dropped by any method
        //getLogger().info("New Entity: " + spawned.getEntity().getItemStack().getType());
        mergeNearby(spawned.getEntity());
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) { // emitted when an entity picks up an item
        if (hasNBT(event.getItem().getItemStack(), "itemmerger.CustomStack")) { // only deal with customstacks
            event.setCancelled(true); // make sure the stack is not removed
            pickedUp((Player)event.getEntity(), event.getItem());
        }
    }

    @EventHandler
    public void onItemPickupInventory(InventoryPickupItemEvent event) { // inventory has picked up an item
        if (hasNBT(event.getItem().getItemStack(), "itemmerger.CustomStack")) { // only deal with customstacks
            event.setCancelled(true); // make sure the stack is not removed
            Pair<ItemStack[], Integer> values = updateInventory(event.getInventory(), event.getItem());
            event.getInventory().setContents(values.getKey()); // update inventory
            if (values.getValue() >= 0) {
                event.getItem().getItemStack().setItemMeta(setNBTInt(event.getItem().getItemStack(), "itemmerger.CustomStack", values.getValue()).getItemMeta()); // update the customstack
            } else {
                customstacks.remove(event.getItem()); // do not check on this item again - this has to happen here rather than in updateInventory or it causes a ConcurrentModificationException in the scheduler (could use a bool param, but I decided not to.)
            }
        }
    }

}