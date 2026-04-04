package io.github.apace100.calio.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// In 1.21+, Ingredient no longer has inner Value/ItemValue/TagValue classes.
// Ingredient now uses codec-based serialization internally.
// This accessor now exposes the items array for custom serialization.
@Mixin(Ingredient.class)
public interface IngredientAccessor {

    // TODO: Verify against MC 26.1 source - Ingredient internals may differ
    @Accessor
    ItemStack[] getItems();

}
