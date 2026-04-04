package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public abstract class DataPackContentsMixin {

    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void calio$cacheDynamicRegistries(ResourceManager manager, RegistryAccess.Frozen dynamicRegistryManager, FeatureFlagSet enabledFeatures, Commands.CommandSelection environment, int functionPermissionLevel, Executor prepareExecutor, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        Calio.DYNAMIC_REGISTRIES.set(dynamicRegistryManager);
    }

}
