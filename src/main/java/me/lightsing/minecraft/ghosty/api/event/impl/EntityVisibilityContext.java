package me.lightsing.minecraft.ghosty.api.event.impl;

import me.lightsing.minecraft.ghosty.Visibility;
import me.lightsing.minecraft.ghosty.api.event.EntityVisibilityCheckEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class EntityVisibilityContext implements EntityVisibilityCheckEvent {
    private final Player viewer;
    private final Entity target;
    private final Visibility.VisibilityResult visibility;

    public EntityVisibilityContext(Player viewer, Entity target, Visibility.VisibilityResult initialVisible) {
        this.viewer = viewer;
        this.target = target;
        this.visibility = initialVisible;
    }

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public Entity getTarget() {
        return target;
    }

    @Override
    public boolean isVisible() {
        return visibility.isVisibleNow;
    }

    @Override
    public boolean wasPreviouslyKnownVisible() {
        return visibility.wasPreviouslyKnownVisible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visibility.isVisibleNow = visible;
    }
}
