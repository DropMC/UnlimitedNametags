package org.alexdev.unlimitednametags.hook;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bone.BoneMovement;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.nms.ModelDisplay;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Measures the models BetterModel puts on an entity.
 *
 * <p>A bone's world position is already relative to the entity's feet, the point BetterModel draws the model
 * from. A bone that draws nothing has no display, and one hidden by a skill or by its blueprint has an invisible
 * display, so neither is measured.</p>
 */
public class BetterModelHook extends ModelHook {

    public BetterModelHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public double modelHeight(@NotNull Entity entity) {
        final EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUniqueId());
        if (registry == null) {
            return 0;
        }

        double tallest = 0;
        final BoneMovement cache = new BoneMovement();
        for (final EntityTracker tracker : registry.trackers()) {
            for (final RenderedBone bone : tracker.bones()) {
                final ModelDisplay display = bone.getDisplay();
                if (display == null || display.invisible()) {
                    continue;
                }
                tallest = Math.max(tallest, bone.worldPosition(cache).y());
            }
        }
        return tallest;
    }
}
