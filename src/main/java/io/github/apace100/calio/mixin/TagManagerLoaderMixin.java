package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(TagManager.class)
public abstract class TagManagerLoaderMixin {

    @Shadow private List<TagManager.LoadResult<?>> results;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "method_40098", at = @At("RETURN"))
    private void calio$cacheRegistryTags(List<?> list, Void void_, CallbackInfo ci) {

        Map<TagKey<?>, Collection<Holder<?>>> registryTagsCache = new HashMap<>();
        this.results.forEach(entry -> entry.tags().forEach((id, entries) ->
            registryTagsCache.put(TagKey.create(entry.key(), id), (Collection) entries))
        );

        Calio.REGISTRY_TAGS.set(registryTagsCache);

    }

}
