package io.github.apace100.calio.mixin;

import net.minecraft.world.entity.ai.behavior.WeightedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedList.Entry.class)
public interface WeightedListEntryAccessor {

    @Accessor
    int getWeight();
}
