package org.alexdev.unlimitednametags.hook;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * How tall a modelled entity actually looks.
 *
 * <p>A nametag sits above the entity's hitbox, which is the right answer for a wolf and the wrong one for
 * anything wearing a model: the plugin driving the model leaves the original entity's hitbox alone, so a pet
 * whose body is two blocks tall is still a 0.85-block wolf as far as Bukkit is concerned, and its name is
 * drawn somewhere inside its chest. This measures the model instead, and measures nothing at all for an
 * entity without one, which is what keeps ordinary animals where they have always been.</p>
 */
public class ModelEngineHook extends Hook {

    public ModelEngineHook(@NotNull UnlimitedNameTags plugin) {
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
    public double modelHeight(@NotNull Entity entity) {
        final ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
        if (modeled == null) {
            return 0;
        }

        final double feet = entity.getLocation().getY();
        double tallest = 0;
        for (final ActiveModel model : modeled.getModels().values()) {
            for (final ModelBone bone : model.getBones().values()) {
                if (bone.isEffectivelyInvisible()) {
                    continue;
                }
                final Location location = bone.getLocation();
                if (location != null) {
                    tallest = Math.max(tallest, location.getY() - feet);
                }
            }
        }
        return tallest;
    }
}
