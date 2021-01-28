package itemmerger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.*;



import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

/*
    To Do:
        - Add command to create custom stack in inventory out of held item (Will require ensuring we add all summoned customstacks to list)
        - Add config for how often task scheduler should run and how large the radius is for grouping items
        - Add a max customitem limit that can be defined in config (defaults at int max. currently it would just roll over)
        - Add command to toggle scheduler
        - Add command to toggle creating new stacks
        - Fix weird bug where creative players creating stacks will have some items not counted (low priority, doesn't affect survival players. probably something to do with how creative players are dealt with differently)
*/

public class ItemMerger extends JavaPlugin {
    public static Plugin plugin;
    public static List<Item> customstacks = new ArrayList<Item>();

    @Override
    public void onEnable() { // startup
        plugin = this;
        ScheduledTasks.stackUpdate.runTaskTimer(this, 20, 20); // start the scheduler that just merges nearby stacks that were moved together, and inserts items into player inventories that can't trigger ItemPickup

        getLogger().info("ItemMerger Started!");
        getServer().getPluginManager().registerEvents(new ItemEvents(), this);
    }


    // Simple function that just checks if the items are the same aside from the CustomStack tag
    protected static boolean sameItem(ItemStack item1, ItemStack item2) {
        return (NBTHelper.removeNBT(item1, "itemmerger.CustomStack").isSimilar(NBTHelper.removeNBT(item2, "itemmerger.CustomStack")));
    }

    // This is where the magic happens. Merges all nearby stackable items
    protected static void mergeNearby(Item item) {
        if (item.getItemStack().getMaxStackSize() != 1 && item.getItemStack().getMaxStackSize() != 0 && (NBTHelper.hasNBT(item.getItemStack(), "itemmerger.NoStack") ? !NBTHelper.getNBTBool(item.getItemStack(), "itemmerger.NoStack") : true)) { // make sure item is not unique/unstackable (Maybe make this configurable (let certain items stack, deny others stacking. wouldn't be too hard, just check if it is on denied material list))
            int items = item.getItemStack().getAmount();
            if (NBTHelper.hasNBT(item.getItemStack(), "itemmerger.CustomStack")) { // see if item is already an item stack. if it is, just multiply item by how much each stack is worth
                items = items * NBTHelper.getNBTInt(item.getItemStack(), "itemmerger.CustomStack");
                item.getItemStack().setAmount(1);
            } else {
                customstacks.add(item); // add to the checker to make sure the plugin merges it
            }
            for (Entity entity : item.getNearbyEntities(5, 5, 5)) { // possibly make this configurable later. get entities in nearby area
                if (entity instanceof Item) { // entity is an item
                    Item near = (Item)entity;
                    if (near.getItemStack().getType() == item.getItemStack().getType()) { // is same type of item
                        if (sameItem(near.getItemStack(), item.getItemStack()) && (NBTHelper.hasNBT(near.getItemStack(), "itemmerger.NoStack") ? !NBTHelper.getNBTBool(near.getItemStack(), "itemmerger.NoStack") : true)) { // merge items if they are of the same type and do not have nostack tag
                            if (NBTHelper.hasNBT(near.getItemStack(), "itemmerger.CustomStack")) { // item found is custom stack
                                items += NBTHelper.getNBTInt(near.getItemStack(), "itemmerger.CustomStack") * near.getItemStack().getAmount(); // multiply in case of fringe case that somehow a customstack stacked
                                near.getItemStack().setAmount(0); // delete stack
                            } else { // item is same but is not stack
                                items += near.getItemStack().getAmount();
                                near.getItemStack().setAmount(0); // delete stack
                            }
                        }
                    }
                }
            }
            item.getItemStack().setItemMeta(NBTHelper.setNBTInt(item.getItemStack(), "itemmerger.CustomStack", items).getItemMeta()); // set the NBT tag
            item.getItemStack().setAmount(1); // make sure there is only 1 item in the customstack stack
        }
    }

    // used to insert the customstack items into a players inventory. it is assumed that items passed ARE custom stacks.
    protected static void pickedUp(Player player, Item item) {
        Pair<ItemStack[], Integer> values = updateInventory(player.getInventory(), item);
        if (!player.getInventory().getContents().equals(values.getKey())) { // only do stuff if the inventory is actually being changed
            player.getInventory().setContents(values.getKey()); // update inventory
            if (values.getValue() > 0) {
                item.getItemStack().setItemMeta(NBTHelper.setNBTInt(item.getItemStack(), "itemmerger.CustomStack", values.getValue()).getItemMeta()); // update the customstack
            } else {
                item.getItemStack().setAmount(0); // delete stack
                customstacks.remove(item); // do not check on this item again - this has to happen here rather than in updateInventory or it causes a ConcurrentModificationException in the scheduler (could use a bool param, but I decided not to.)
            }
        }
    }

    protected static Pair<ItemStack[], Integer> updateInventory(Inventory inputinventory, Item item) {
        Integer items = NBTHelper.getNBTInt(item.getItemStack(), "itemmerger.CustomStack"); // get how many items are in the stack
        ItemStack[] inventory = new ItemStack[inputinventory.getContents().length]; // array that will be used to update inventory
        for (int pos = 0; pos < inventory.length; pos++) {
            if ((inputinventory.getItem(pos) == null ? true : (NBTHelper.removeNBT(item.getItemStack(), "itemmerger.CustomStack").isSimilar(inputinventory.getItem(pos)) && inputinventory.getItem(pos).getAmount() < inputinventory.getItem(pos).getMaxStackSize())) && pos < 36 && items > 0) { //
                Integer curAmount = inputinventory.getItem(pos) == null ? 0 : inputinventory.getItem(pos).getAmount(); // get the amount in this slot, or set it to 0
                if (items - (item.getItemStack().getMaxStackSize() - curAmount) > 0) { // there are more items than would take to fill the slot
                    items -= inputinventory.getItem(pos) != null ? item.getItemStack().getMaxStackSize() - inputinventory.getItem(pos).getAmount() : item.getItemStack().getMaxStackSize();
                    inventory[pos] = NBTHelper.removeNBT(item.getItemStack(), "itemmerger.CustomStack");
                    inventory[pos].setAmount(item.getItemStack().getMaxStackSize());
                } else {
                    inventory[pos] = NBTHelper.removeNBT(item.getItemStack(), "itemmerger.CustomStack");
                    inventory[pos].setAmount(inputinventory.getItem(pos) == null ? items : items + inputinventory.getItem(pos).getAmount());
                    items = 0;
                }
            } else {
                if (inputinventory.getItem(pos) != null) {
                    inventory[pos] = inputinventory.getItem(pos);
                }
            }
        }
        return new ImmutablePair<ItemStack[], Integer>(inventory, items);
    }
}