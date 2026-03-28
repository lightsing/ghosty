package me.lightsing.minecraft.ghosty.mixin;

import me.lightsing.minecraft.ghosty.VisibilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(
            method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD")
    )
    public void ghosty$onChangeDimension(DimensionTransition ignoredDimensionTransition, CallbackInfoReturnable<Entity> ignoredCir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        VisibilityManager.cleanup(player);
    }
}
