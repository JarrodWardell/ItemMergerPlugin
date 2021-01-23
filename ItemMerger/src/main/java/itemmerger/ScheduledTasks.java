package itemmerger;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class ScheduledTasks {
    protected static BukkitRunnable stackUpdate = new BukkitRunnable() { // the task used for merging nearby stacks that fell near eachother and inserting items into a player inventory
        @Override
        public void run() {
            ItemMerger.customstacks.removeAll(Collections.singleton(null)); // get rid of items that were already deleted
            List<Item> invalidstacks = new ArrayList<Item>(); // this and next 8 lines solve issue of some items still existing within list upon removal, causing bugs (and, more concerningly, console warns, which are ugly)
            for (Item item : ItemMerger.customstacks) {
                if (!item.isValid() || item == null) {
                    invalidstacks.add(item);
                }
            }
            for (Item item : invalidstacks) {
                ItemMerger.customstacks.remove(item);
            }
            for (Item item : ItemMerger.customstacks) { // iterate through list
                ItemMerger.mergeNearby(item);
                if (item.getPickupDelay() == 0) {
                    for (Entity entity : item.getNearbyEntities(1.425, 1.425, 1.425)) {
                        if (entity instanceof Player) {
                            ItemMerger.pickedUp((Player)entity, item);
                            break;
                        }
                    }
                }
            }
        }
    };
}
