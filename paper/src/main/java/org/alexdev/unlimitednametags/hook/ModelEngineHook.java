package org.alexdev.unlimitednametags.hook;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.BlueprintBone;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
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
 *
 * <p>The height comes from how high the model's bones reach rather than from its hitbox, because a hitbox is
 * only as good as the model author's choice to declare one: a blueprint that does not gets ModelEngine's
 * player-sized default of 1.8 blocks, which is roughly right for a small companion and half the truth for a
 * dragon. Bones are always there and always where the geometry is.</p>
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
     * The height of the tallest model on {@code entity}, or 0 when it wears none. Several models can be
     * stacked on one entity, and the name belongs above all of them.
     *
     * @param entity the entity a nametag is being placed on
     * @return the model height in blocks, scale applied, or 0 for an unmodelled entity
     */
    public double modelHeight(@NotNull Entity entity) {
        final ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
        if (modeled == null) {
            return 0;
        }

        double tallest = 0;
        for (final ActiveModel model : modeled.getModels().values()) {
            final ModelBlueprint blueprint = model.getBlueprint();
            if (blueprint == null) {
                continue;
            }
            tallest = Math.max(tallest, highestBone(blueprint) * model.getScale().y());
        }
        return tallest;
    }

    /**
     * How high the blueprint's bones reach, in blocks. Bone positions are already in blocks: the Blockbench
     * parser scales them by 1/16 on the way in.
     *
     * <p>A bone sits at the origin of the part it renders, not at the top of it, so this lands a little under
     * the crown of the model. That is the half to err on, and the configured offset is added on top of it.</p>
     */
    private double highestBone(@NotNull ModelBlueprint blueprint) {
        double highest = 0;
        for (final BlueprintBone bone : blueprint.getBones().values()) {
            highest = Math.max(highest, highestBone(bone));
        }
        return highest;
    }

    private double highestBone(@NotNull BlueprintBone bone) {
        double highest = bone.getGlobalPosition().y();
        for (final BlueprintBone child : bone.getChildren().values()) {
            highest = Math.max(highest, highestBone(child));
        }
        return highest;
    }
}
