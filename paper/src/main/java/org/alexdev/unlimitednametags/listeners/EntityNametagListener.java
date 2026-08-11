package org.alexdev.unlimitednametags.listeners;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Drives {@link org.alexdev.unlimitednametags.nametags.EntityNameTagManager} from Paper's per-viewer entity tracker.
 * Only fires for non-player entities; players keep going through {@link PaperTrackerListener}.
 */
@RequiredArgsConstructor
public class EntityNametagListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler
    public void onTrack(@NotNull PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player || !plugin.getEntityNametagManager().isEnabled()) {
            return;
        }
        plugin.getEntityNametagManager().handleTrack(event.getPlayer(), event.getEntity());
    }

    @EventHandler
    public void onUntrack(@NotNull PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        plugin.getEntityNametagManager().handleUntrack(event.getPlayer(), event.getEntity());
    }

    @EventHandler
    public void onRemoveFromWorld(@NotNull EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        plugin.getEntityNametagManager().handleEntityRemoved(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onDeath(@NotNull EntityDeathEvent event) {
        plugin.getEntityNametagManager().handleEntityRemoved(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.getEntityNametagManager().handleQuit(event.getPlayer());
    }

    /**
     * A name tag is applied on the tick after the interaction, so the entity is re-evaluated one tick later.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        if (!plugin.getEntityNametagManager().isEnabled()) {
            return;
        }
        scheduleStateChange(event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(@NotNull EntityTameEvent event) {
        if (!plugin.getEntityNametagManager().isEnabled()) {
            return;
        }
        scheduleStateChange(event.getEntity());
    }

    /**
     * A passenger hides the nametag: the display would have to share the vehicle's passenger slots with the rider,
     * which shifts it out of place. Removed immediately so it does not linger for a tick on top of the rider.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(@NotNull EntityMountEvent event) {
        if (!plugin.getEntityNametagManager().isEnabled()) {
            return;
        }
        plugin.getEntityNametagManager().handleEntityRemoved(event.getMount().getUniqueId());
    }

    /**
     * The rider is still attached while this event runs, so the vehicle is re-evaluated a tick later, once its
     * passenger list is actually empty.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(@NotNull EntityDismountEvent event) {
        if (!plugin.getEntityNametagManager().isEnabled()) {
            return;
        }
        scheduleStateChange(event.getDismounted());
    }

    private void scheduleStateChange(@NotNull Entity entity) {
        plugin.getTaskScheduler().runTaskLater(() -> {
            if (entity.isValid()) {
                plugin.getEntityNametagManager().handleStateChanged(entity);
            }
        }, 1);
    }
}
