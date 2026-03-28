package me.lightsing.minecraft.ghosty.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public interface EntityVisibilityCheckEvent {
    Player getViewer();

    Entity getTarget();

    boolean isVisible();
    boolean wasPreviouslyKnownVisible();

    void setVisible(boolean visible);
}
