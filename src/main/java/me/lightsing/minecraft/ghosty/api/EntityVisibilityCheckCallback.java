package me.lightsing.minecraft.ghosty.api;

import me.lightsing.minecraft.ghosty.api.event.EntityVisibilityCheckEvent;
import net.minecraft.world.InteractionResult;

public interface EntityVisibilityCheckCallback {


    /**
     * Called when a visibility check is performed for an entity. Listeners can modify the visibility result by calling
     * setVisible() on the event.
     *
     * @param event the visibility check event containing the viewer, target entity, and current visibility state
     * @return PASS to allow other listeners to modify the visibility result, or other results to short-circuit further
     * checks and use the current visibility state as final. this is not used to directly set the visibility, but can
     * be used to prevent other listeners from modifying it further.
     */
    InteractionResult onCheckVisibility(EntityVisibilityCheckEvent event);
}
