package org.alexdev.unlimitednametags.nametags;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Formatter;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.config.TextFormatter;
import org.alexdev.unlimitednametags.packet.EntityTextPacketNameTag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a text display above non-player entities.
 * <p>
 * Vanilla only draws an entity's custom name when the viewer aims at it from a few blocks away, and
 * {@code TamableAnimal.getTeam()} returns the <i>owner's</i> team, so hiding a player's vanilla nametag (which this
 * plugin does by forcing {@code nameTagVisibility = NEVER}) hides their pets' names as well. Drawing the name as a
 * display entity is not subject to either rule.
 */
public class EntityNameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, TrackedEntity> tracked = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> byEntityId = new ConcurrentHashMap<>();
    private Set<EntityType> allowedTypes = EnumSet.noneOf(EntityType.class);
    private Set<EntityType> blockedTypes = EnumSet.noneOf(EntityType.class);
    private MyScheduledTask refreshTask;
    private MyScheduledTask followTask;

    public EntityNameTagManager(@NotNull final UnlimitedNameTags plugin) {
        this.plugin = plugin;
        reload();
    }

    @NotNull
    private Settings.EntityNametags settings() {
        return plugin.getConfigManager().getSettings().getEntityNametags();
    }

    public boolean isEnabled() {
        return settings().isEnabled();
    }

    /**
     * Re-resolves the configured type filters and restarts the refresh task. Existing displays are dropped so the
     * next track event rebuilds them from the new settings.
     */
    public void reload() {
        final Settings.EntityNametags config = settings();
        this.allowedTypes = parseTypes(config.getEntityTypes());
        this.blockedTypes = parseTypes(config.getBlacklistedEntityTypes());
        removeAll();
        stopTask();
        if (config.isEnabled() && plugin.isPaper()) {
            startTask(config.resolveRefreshInterval());
            adoptAlreadyTrackedEntities();
        }
    }

    /**
     * Track events already fired for entities near online players before this manager existed (server start with
     * players connected, or {@code /unt reload}), so those are picked up explicitly.
     */
    private void adoptAlreadyTrackedEntities() {
        for (final World world : Bukkit.getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (shouldRender(entity)) {
                    handleStateChanged(entity);
                }
            }
        }
    }

    @NotNull
    private Set<EntityType> parseTypes(@NotNull final List<String> names) {
        final Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (final String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                types.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown entity type in entityNametags: " + name);
            }
        }
        return types;
    }

    /**
     * Runs synchronously: the tick reads live entity state (custom name, health, passengers).
     */
    private void startTask(final int interval) {
        refreshTask = plugin.getTaskScheduler().runTaskTimer(this::tick, interval, interval);
        final int followInterval = settings().resolveFollowInterval();
        followTask = plugin.getTaskScheduler().runTaskTimer(this::followTick, followInterval, followInterval);
    }

    private void stopTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    // ─── Eligibility ──────────────────────────────────────────────────────────

    public boolean shouldRender(@NotNull final Entity entity) {
        final Settings.EntityNametags config = settings();
        if (!config.isEnabled() || entity instanceof Player || !entity.isValid()) {
            return false;
        }
        if (blockedTypes.contains(entity.getType())) {
            return false;
        }
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(entity.getType())) {
            return false;
        }
        if (allowedTypes.isEmpty() && !(entity instanceof LivingEntity)) {
            return false;
        }
        if (config.isOnlyTamed() && !isTamed(entity)) {
            return false;
        }
        if (!entity.getPassengers().isEmpty()) {
            return false;
        }
        return !config.isRequireCustomName() || entity.customName() != null;
    }

    private boolean isTamed(@NotNull final Entity entity) {
        return entity instanceof Tameable tameable && tameable.isTamed();
    }

    // ─── Tracking ─────────────────────────────────────────────────────────────

    /**
     * Called when a viewer starts tracking an entity. Creates the display on first use.
     */
    public void handleTrack(@NotNull final Player viewer, @NotNull final Entity entity) {
        if (!shouldRender(entity)) {
            return;
        }
        final TrackedEntity entry = tracked.computeIfAbsent(entity.getUniqueId(), id -> create(entity));
        entry.display().showToViewer(viewer.getUniqueId());
        entry.display().applyTextTo(viewer.getUniqueId());
    }

    public void handleUntrack(@NotNull final Player viewer, @NotNull final Entity entity) {
        final TrackedEntity entry = tracked.get(entity.getUniqueId());
        if (entry == null) {
            return;
        }
        entry.display().hideFromViewer(viewer.getUniqueId());
        if (entry.display().getViewers().isEmpty()) {
            remove(entity.getUniqueId());
        }
    }

    public void handleQuit(@NotNull final Player viewer) {
        for (final TrackedEntity entry : tracked.values()) {
            entry.display().handleQuit(viewer.getUniqueId());
        }
    }

    public void handleEntityRemoved(@NotNull final UUID entityId) {
        remove(entityId);
    }

    /**
     * Re-evaluates an entity whose state just changed (renamed, tamed). Viewers already tracking it would otherwise
     * wait for their next track event, which only fires after they walk out of range and back.
     */
    public void handleStateChanged(@NotNull final Entity entity) {
        final boolean eligible = shouldRender(entity);
        final TrackedEntity entry = tracked.get(entity.getUniqueId());
        if (!eligible) {
            if (entry != null) {
                remove(entity.getUniqueId());
            }
            return;
        }
        if (entry != null) {
            entry.display().updateText(buildText(entity));
            return;
        }
        for (final Player viewer : entity.getTrackedBy()) {
            handleTrack(viewer, entity);
        }
    }

    @NotNull
    private TrackedEntity create(@NotNull final Entity entity) {
        final Settings.EntityNametags config = settings();
        final Settings.DisplayGroup group = Settings.DisplayGroup.builder()
                .line(config.getFormat())
                .background(config.getBackground())
                .scale(config.getScale())
                .yOffset(config.getYOffset())
                .billboard(config.getBillboard())
                .build();

        final UUID entityId = entity.getUniqueId();
        final EntityTextPacketNameTag display = new EntityTextPacketNameTag(
                plugin, entityId, () -> resolveEntity(entityId), group, config);
        display.setVisible(true);
        display.setFollowing(config.isFollowEntity(), config.resolveFollowInterval());

        final TrackedEntity entry = new TrackedEntity(entity, display);
        entry.display().updateText(buildText(entity));
        byEntityId.put(entity.getEntityId(), entityId);
        return entry;
    }

    @Nullable
    private Entity resolveEntity(@NotNull final UUID entityId) {
        final TrackedEntity entry = tracked.get(entityId);
        return entry != null ? entry.entity() : null;
    }

    private void remove(@NotNull final UUID entityId) {
        final TrackedEntity entry = tracked.remove(entityId);
        if (entry == null) {
            return;
        }
        byEntityId.remove(entry.entity().getEntityId());
        entry.display().remove();
    }

    public void removeAll() {
        for (final UUID entityId : new HashSet<>(tracked.keySet())) {
            remove(entityId);
        }
        tracked.clear();
        byEntityId.clear();
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    private void tick() {
        for (final Map.Entry<UUID, TrackedEntity> mapping : new HashSet<>(tracked.entrySet())) {
            final TrackedEntity entry = mapping.getValue();
            if (!shouldRender(entry.entity())) {
                remove(mapping.getKey());
                continue;
            }
            entry.display().updateText(buildText(entry.entity()));
        }
    }

    /**
     * Pushes each followed display to its entity's current position. Nothing else moves it: a followed display is
     * deliberately not a passenger, so the client would leave it wherever it was spawned.
     */
    private void followTick() {
        for (final TrackedEntity entry : tracked.values()) {
            if (entry.display().isFollowing()) {
                entry.display().syncPosition();
            }
        }
    }

    /**
     * True when the plugin draws this entity's name, so the vanilla custom name can be stripped from packets.
     */
    public boolean isManaged(final int bukkitEntityId) {
        return byEntityId.containsKey(bukkitEntityId);
    }

    public boolean shouldHideVanillaName() {
        final Settings.EntityNametags config = settings();
        return config.isEnabled() && config.isHideVanillaName();
    }

    // ─── Passengers ───────────────────────────────────────────────────────────

    /**
     * Display ids riding the given entity, which is none of them while following. Vanilla rewrites the whole
     * passenger list whenever someone mounts or dismounts, so a mounted display has to be appended back or it is
     * silently unmounted and freezes at its last absolute position.
     */
    @NotNull
    public List<Integer> displayIdsFor(final int bukkitEntityId) {
        final UUID entityId = byEntityId.get(bukkitEntityId);
        if (entityId == null) {
            return List.of();
        }
        final TrackedEntity entry = tracked.get(entityId);
        if (entry == null || entry.display().isFollowing()) {
            return List.of();
        }
        return List.of(entry.display().displayEntityId());
    }

    /**
     * A rendered entity never has passengers of its own, so the display is the whole list.
     */
    public void sendPassengersPacket(@NotNull final User viewer, @NotNull final UUID ownerId) {
        final TrackedEntity entry = tracked.get(ownerId);
        if (entry == null || entry.display().isFollowing()) {
            return;
        }
        plugin.getPacketManager().sendEntityPassengersPacket(viewer, entry.entity().getEntityId(),
                List.of(entry.display().displayEntityId()));
    }

    // ─── Text ─────────────────────────────────────────────────────────────────

    @NotNull
    private Component buildText(@NotNull final Entity entity) {
        final Settings.EntityNametags config = settings();
        final TextFormatter textFormatter = plugin.getConfigManager().getSettings().getBehavior().getFormat();
        final Formatter formatter = Formatter.from(textFormatter);

        Component component = formatter.format(plugin, Bukkit.getConsoleSender(),
                applyPlaceholders(config.getFormat(), entity, textFormatter));

        final String tamedFormat = config.getTamedFormat();
        if (tamedFormat != null && !tamedFormat.isBlank() && isTamed(entity)) {
            component = component.append(Component.newline()).append(
                    formatter.format(plugin, Bukkit.getConsoleSender(),
                            applyPlaceholders(tamedFormat, entity, textFormatter)));
        }
        return component;
    }

    @NotNull
    private String applyPlaceholders(@NotNull final String raw, @NotNull final Entity entity,
            @NotNull final TextFormatter textFormatter) {
        return raw.replace("%entity_name%", entityName(entity, textFormatter))
                .replace("%entity_type%", prettyType(entity.getType()))
                .replace("%owner_name%", ownerName(entity))
                .replace("%health%", formatHealth(entity))
                .replace("%max_health%", formatMaxHealth(entity));
    }

    /**
     * Serialises the custom name with the same markup the configured formatter parses, so a coloured name tag keeps
     * its colours instead of being flattened or double-escaped.
     */
    @NotNull
    private String entityName(@NotNull final Entity entity, @NotNull final TextFormatter textFormatter) {
        final Component custom = entity.customName();
        if (custom == null) {
            return prettyType(entity.getType());
        }
        return switch (textFormatter) {
            case MINIMESSAGE -> MiniMessage.miniMessage().serialize(custom);
            case LEGACY, UNIVERSAL -> LegacyComponentSerializer.legacyAmpersand().serialize(custom);
        };
    }

    @NotNull
    private String prettyType(@NotNull final EntityType type) {
        final String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    @NotNull
    private String ownerName(@NotNull final Entity entity) {
        if (!(entity instanceof Tameable tameable)) {
            return "";
        }
        final AnimalTamer owner = tameable.getOwner();
        return owner != null && owner.getName() != null ? owner.getName() : "";
    }

    @NotNull
    private String formatHealth(@NotNull final Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return "";
        }
        return String.valueOf(Math.round(living.getHealth()));
    }

    @NotNull
    private String formatMaxHealth(@NotNull final Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return "";
        }
        final AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? "" : String.valueOf(Math.round(attribute.getValue()));
    }

    public void onDisable() {
        stopTask();
        removeAll();
    }

    /**
     * One rendered entity: the Bukkit handle and its display.
     */
    private record TrackedEntity(@NotNull Entity entity, @NotNull EntityTextPacketNameTag display) {
    }
}
