package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.protocol.player.User;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.CustomDisplayAnimationHandler;
import org.alexdev.unlimitednametags.packet.CustomGlowHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * {@link NametagRuntime} for entity nametags. Everything platform-wide is delegated to the player runtime; the
 * owner-scoped methods are overridden because an animal has no attributes, no PlaceholderAPI context, and mounts its
 * display through the entity passenger list instead of the player one.
 */
public final class BukkitEntityNametagRuntime implements NametagRuntime {

    private final UnlimitedNameTags plugin;
    private final NametagRuntime delegate;

    public BukkitEntityNametagRuntime(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.delegate = plugin.getNametagRuntime();
    }

    @Override
    public int nextEntityId() {
        return delegate.nextEntityId();
    }

    @Override
    @NotNull
    public Settings settings() {
        return delegate.settings();
    }

    @Override
    public boolean isNametagDebug() {
        return delegate.isNametagDebug();
    }

    @Override
    public void logInfo(@NotNull String message) {
        delegate.logInfo(message);
    }

    @Override
    public void logWarning(@NotNull String message) {
        delegate.logWarning(message);
    }

    @Override
    public void logWarning(@NotNull String message, @NotNull Throwable error) {
        delegate.logWarning(message, error);
    }

    @Override
    public @Nullable CustomDisplayAnimationHandler resolveCustomAnimationHandler(@NotNull String id) {
        return delegate.resolveCustomAnimationHandler(id);
    }

    @Override
    public @Nullable GlowOverride resolveGlowAnimation(@NotNull String id) {
        return delegate.resolveGlowAnimation(id);
    }

    @Override
    @NotNull
    public Set<String> registeredGlowAnimationIds() {
        return delegate.registeredGlowAnimationIds();
    }

    @Override
    public @Nullable CustomGlowHandler resolveCustomGlowHandler(@NotNull String id) {
        return delegate.resolveCustomGlowHandler(id);
    }

    @Override
    @NotNull
    public Set<String> registeredCustomGlowHandlerIds() {
        return delegate.registeredCustomGlowHandlerIds();
    }

    @Override
    public void runTaskLaterAsync(@NotNull Runnable task, long delayTicks) {
        delegate.runTaskLaterAsync(task, delayTicks);
    }

    /**
     * Animals have no scale attribute, so the configured scale is already the final one.
     */
    @Override
    public float scaledDisplayScale(@NotNull UUID ownerId, float displayGroupScale) {
        return displayGroupScale;
    }

    @Override
    public void removePassenger(@NotNull UUID viewerId, int displayEntityId) {
        plugin.getPacketManager().removeEntityPassenger(displayEntityId);
    }

    @Override
    public void removePassengerFromAll(int displayEntityId) {
        plugin.getPacketManager().removeEntityPassenger(displayEntityId);
    }

    @Override
    public void sendPassengersPacket(@NotNull User viewerUser, @NotNull UUID ownerId) {
        plugin.getEntityNametagManager().sendPassengersPacket(viewerUser, ownerId);
    }

    /**
     * Entity nametags use a single configured line with no JEXL conditions.
     */
    @Override
    public boolean isDisplayGroupActive(@NotNull UUID ownerId, @NotNull Settings.DisplayGroup group) {
        return true;
    }

    /**
     * PlaceholderAPI needs a player context that an animal cannot provide; entity placeholders are resolved by
     * {@code EntityNameTagManager} before the text reaches the display.
     */
    @Override
    @NotNull
    public String expandPlaceholdersForOwner(@NotNull UUID ownerId, @NotNull String raw) {
        return raw;
    }
}
