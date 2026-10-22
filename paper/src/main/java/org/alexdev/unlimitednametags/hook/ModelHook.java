package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * How tall a modelled entity actually looks.
 *
 * <p>A nametag sits above the entity's hitbox, which is the right answer for a wolf and the wrong one for
 * anything wearing a model: the plugin driving the model leaves the original entity's hitbox alone, so a pet
 * whose body is two blocks tall is still a 0.85-block wolf as far as Bukkit is concerned, and its name is
 * drawn somewhere inside its chest. Each model plugin measures its own models instead, and measures nothing at
 * all for an entity without one, which is what keeps ordinary animals where they have always been.</p>
 */
public abstract class ModelHook extends Hook {

    protected ModelHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    /**
     * How far above {@code entity} the top of its model reaches, or 0 when it wears none. Several models can
     * be stacked on one entity, and the name belongs above all of them.
     *
     * <p>Only bones currently being rendered are measured. A blueprint carries more than the creature: the
     * Cubee dragons hang their spell geometry off bones a block and a half above the head, kept hidden until
     * the skill plays, and measuring those would park the name in empty sky above a pet that never looks that
     * tall. What is on screen is what the name has to clear.</p>
     *
     * @param entity the entity a nametag is being placed on
     * @return the height in blocks above the entity's feet, or 0 for an unmodelled entity
     */
    public abstract double modelHeight(@NotNull Entity entity);
}
