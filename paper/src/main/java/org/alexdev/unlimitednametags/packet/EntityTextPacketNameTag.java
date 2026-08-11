package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.BukkitEntityNametagPlatform;
import org.alexdev.unlimitednametags.platform.BukkitEntityNametagRuntime;
import org.alexdev.unlimitednametags.platform.BukkitNametagMaterialBridge;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Text display mounted on a non-player {@link Entity}.
 * <p>
 * The player path seeds each new viewer's display by copying metadata from the owner's own display, but an animal is
 * never a viewer, so there is no such template. Every visual property is therefore baked into the per-viewer entity
 * as it is built; {@code modifyAbstractAll} would only reach viewers that already exist. The text is shared by all
 * viewers because an animal has no relational placeholders.
 */
public final class EntityTextPacketNameTag extends TextPacketNameTag {

    private final UnlimitedNameTags plugin;
    private final Settings.EntityNametags config;
    private volatile Component text;

    public EntityTextPacketNameTag(@NotNull final UnlimitedNameTags plugin, @NotNull final UUID ownerId,
            @NotNull final Supplier<Entity> ownerSupplier, @NotNull final Settings.DisplayGroup displayGroup,
            @NotNull final Settings.EntityNametags config) {
        super(
                new BukkitEntityNametagRuntime(plugin),
                new BukkitEntityNametagPlatform(plugin, ownerId, ownerSupplier),
                new BukkitNametagMaterialBridge(plugin),
                ownerId,
                displayGroup);
        this.plugin = plugin;
        this.config = config;
    }

    @NotNull
    public UnlimitedNameTags getPlugin() {
        return plugin;
    }

    /**
     * Runs when a viewer first sees the display, which is always after the constructor has assigned {@link #config}.
     */
    @Override
    protected @NotNull Function<User, WrapperEntity> buildBaseSupplier() {
        final Function<User, WrapperEntity> base = super.buildBaseSupplier();
        return user -> {
            final WrapperEntity wrapper = base.apply(user);
            applyVisuals((TextDisplayMeta) wrapper.getEntityMeta());
            return wrapper;
        };
    }

    private void applyVisuals(@NotNull final TextDisplayMeta meta) {
        final Component current = text;
        if (current != null) {
            meta.setText(current);
        }
        meta.setBillboardConstraints(config.getBillboard());
        meta.setViewRange(config.getViewDistance());

        final float resolvedScale = getScale();
        meta.setScale(new Vector3f(resolvedScale, resolvedScale, resolvedScale));
        meta.setTranslation(new Vector3f(0, getBaseTranslationY(), 0));

        final Settings.Background background = config.getBackground();
        meta.setBackgroundColor(background.getArgb());
        meta.setShadow(background.shadowed());
        meta.setSeeThrough(background.seeThrough());
    }

    /**
     * Sets the shared text and flushes it to every current viewer.
     */
    public void updateText(@NotNull final Component component) {
        if (component.equals(text)) {
            return;
        }
        this.text = component;
        for (final UUID viewerId : getViewers()) {
            if (text(viewerId, component)) {
                refreshForViewer(viewerId, true);
            }
        }
    }

    /**
     * Pushes the current text to a viewer that has just been shown the display.
     */
    public void applyTextTo(@NotNull final UUID viewerId) {
        final Component current = text;
        if (current == null) {
            return;
        }
        if (text(viewerId, current)) {
            refreshForViewer(viewerId, true);
        }
    }
}
