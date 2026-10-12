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
 * Measures the models ModelEngine puts on an entity.
 */
public class ModelEngineHook extends ModelHook {

    public ModelEngineHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
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
