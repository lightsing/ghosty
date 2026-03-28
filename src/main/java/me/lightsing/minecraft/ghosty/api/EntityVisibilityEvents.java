package me.lightsing.minecraft.ghosty.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.InteractionResult;

public final class EntityVisibilityEvents {
    public static final Event<EntityVisibilityPreCheckCallback> PRE = EventFactory.createArrayBacked(
            EntityVisibilityPreCheckCallback.class,
            (listeners) -> (player, target) -> {
                for (EntityVisibilityPreCheckCallback listener : listeners) {
                    InteractionResult result = listener.shouldCheck(player, target);

                    if (result != InteractionResult.PASS) {
                        return result;
                    }
                }
                return InteractionResult.SUCCESS;
            }
    );

    public static final Event<EntityVisibilityCheckCallback> POST = EventFactory.createArrayBacked(
            EntityVisibilityCheckCallback.class,
            (listeners) -> (event) -> {
                for (EntityVisibilityCheckCallback listener : listeners) {
                    InteractionResult result = listener.onCheckVisibility(event);

                    if (result != InteractionResult.PASS) {
                        return result;
                    }
                }
                return InteractionResult.PASS;
            }
    );
}
