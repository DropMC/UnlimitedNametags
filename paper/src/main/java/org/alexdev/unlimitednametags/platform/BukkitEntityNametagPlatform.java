package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link NametagPlatformBridge} whose owner is an arbitrary {@link Entity} rather than a player. Viewers are still
 * players, so every viewer-side question is answered exactly as {@link BukkitNametagPlatform} answers it; only the
 * owner side resolves an entity.
 */
public final class BukkitEntityNametagPlatform implements NametagPlatformBridge {

    private final UnlimitedNameTags plugin;
    private final UUID ownerId;
    private final Supplier<Entity> ownerSupplier;

    public BukkitEntityNametagPlatform(@NotNull UnlimitedNameTags plugin, @NotNull UUID ownerId,
            @NotNull Supplier<Entity> ownerSupplier) {
        this.plugin = plugin;
        this.ownerId = ownerId;
        this.ownerSupplier = ownerSupplier;
    }

    @Override
    @NotNull
    public UUID ownerId() {
        return ownerId;
    }

    @Nullable
    private Entity owner() {
        final Entity entity = ownerSupplier.get();
        return entity != null && entity.isValid() ? entity : null;
    }

    @Override
    public boolean isOwnerOnline() {
        return owner() != null;
    }

    @Override
    public @Nullable Location anchorLocation(@NotNull UUID owner) {
        final Entity entity = owner();
        if (entity == null) {
            return null;
        }
        final org.bukkit.Location bukkit = entity.getLocation();
        bukkit.setPitch(0);
        bukkit.setYaw(-180);
        return SpigotConversionUtil.fromBukkitLocation(bukkit);
    }

    @Override
    public @Nullable User resolveUser(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return null;
        }
        if (PacketEvents.getAPI().getPlayerManager().getChannel(player) == null) {
            return null;
        }
        return PacketEvents.getAPI().getPlayerManager().getUser(player);
    }

    /**
     * Either side may be the owning entity or a viewing player, so each id is resolved as a player first and as the
     * owning entity second.
     */
    @Override
    public double distanceSquaredSameWorld(@NotNull UUID a, @NotNull UUID b) {
        final Entity ea = resolveAny(a);
        final Entity eb = resolveAny(b);
        if (ea == null || eb == null || ea.getWorld() != eb.getWorld()) {
            return -1;
        }
        return ea.getLocation().distanceSquared(eb.getLocation());
    }

    @Nullable
    private Entity resolveAny(@NotNull UUID id) {
        if (id.equals(ownerId)) {
            return owner();
        }
        return plugin.getPlayerListener().getPlayer(id);
    }

    @Override
    public boolean isSneaking(@NotNull UUID owner) {
        return false;
    }

    @Override
    public float resolveDisplayScale(@NotNull UUID owner, float configScale) {
        return configScale;
    }

    @Override
    public boolean viewerSupportsTextDisplay(@NotNull UUID viewerId) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        if (viewer == null) {
            return false;
        }
        return plugin.getHook(ViaVersionHook.class).map(h -> !h.hasNotTextDisplays(viewer)).orElse(true);
    }

    @Override
    public boolean isEligibleToShow(@NotNull UUID owner, @NotNull UUID viewerId, boolean visible, boolean viewerAlreadySeeing) {
        return nametagShowBlockReason(owner, viewerId, visible, viewerAlreadySeeing) == null;
    }

    @Override
    public @Nullable String nametagShowBlockReason(@NotNull UUID owner, @NotNull UUID viewerId, boolean visible,
            boolean viewerAlreadySeeing) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Entity ownerEntity = owner();
        if (!visible) {
            return "nametag is not marked visible";
        }
        if (viewer == null) {
            return "viewer is not loaded or online";
        }
        if (ownerEntity == null) {
            return "owner entity is not loaded";
        }
        if (!viewerSupportsTextDisplay(viewerId)) {
            return "viewer client does not support text displays";
        }
        if (viewer.getWorld() != ownerEntity.getWorld()) {
            return "viewer is in world " + viewer.getWorld().getName()
                    + " but owner is in world " + ownerEntity.getWorld().getName();
        }
        if (resolveUser(viewerId) == null) {
            return "PacketEvents user is not loaded for viewer";
        }
        if (!viewer.hasPermission("unt.showentitynametags")) {
            return "viewer lacks permission unt.showentitynametags";
        }
        if (plugin.getNametagManager().isHiddenOtherNametags(viewer)) {
            return "viewer has hidden other nametags";
        }
        if (viewerAlreadySeeing) {
            return "viewer is already seeing this nametag";
        }
        return null;
    }

    @Override
    public @Nullable String playerName(@NotNull UUID playerId) {
        if (playerId.equals(ownerId)) {
            final Entity entity = owner();
            return entity != null ? entity.getType().name() + "#" + entity.getEntityId() : null;
        }
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        return player != null ? player.getName() : null;
    }

    @Override
    public boolean isEffectiveShowOwnNametag(@NotNull UUID owner) {
        return false;
    }

    /**
     * The display rides the entity, so this position only seeds the spawn packet; the client keeps it in place from
     * the passenger mount afterwards.
     */
    @Override
    public @Nullable Location offsetDisplayLocation(float displayScale) {
        final Entity entity = owner();
        if (entity == null) {
            return null;
        }
        final org.bukkit.Location bukkit = entity.getLocation();
        bukkit.setPitch(0);
        bukkit.setYaw(-180);
        bukkit.setY(bukkit.getY() + entity.getHeight() * displayScale);
        return SpigotConversionUtil.fromBukkitLocation(bukkit);
    }

    @Override
    public boolean viewerLacksTextDisplaySupport(@NotNull UUID viewerId) {
        return !viewerSupportsTextDisplay(viewerId);
    }

    @Override
    public boolean hasLineOfSight(@NotNull UUID viewerId, @NotNull UUID owner) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Entity ownerEntity = owner();
        if (viewer == null || ownerEntity == null) {
            return false;
        }
        return viewer.hasLineOfSight(ownerEntity);
    }
}
