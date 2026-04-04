package io.github.apace100.calio.mixin;

import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {

    @Invoker
    static String[] callRemovePadding(List<String> pattern) {
        throw new AssertionError();
    }

}
