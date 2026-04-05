package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/***
 * This mixin makes sure that adding attribute modifiers to an equipment item does not overwrite the existing ones.
 * Updated for 26.1 DataComponents API - attribute modifiers are now stored as ItemAttributeModifiers component.
 */
@Mixin(ItemStack.class)
public abstract class DontOverwriteAttrModsMixin {

    @Inject(at = @At("RETURN"), method = "getAttributeModifiers", cancellable = true)
    private void addAttributeModifiersFromItem(CallbackInfoReturnable<ItemAttributeModifiers> info) {
        ItemStack thisStack = (ItemStack)(Object)this;
        if(Calio.areEntityAttributesAdditional(thisStack)) {
            ItemAttributeModifiers currentModifiers = info.getReturnValue();

            //  In 26.1, the default modifiers are stored on the item's DataComponents, not on the Item itself.
            //  We retrieve them from the ATTRIBUTE_MODIFIERS data component of the item's default stack.
            ItemAttributeModifiers defaultModifiers = thisStack.getItem().components()
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

            // Merge current stack modifiers with the item's default modifiers
            List<ItemAttributeModifiers.Entry> mergedEntries = new ArrayList<>(currentModifiers.modifiers());
            for (ItemAttributeModifiers.Entry defaultEntry : defaultModifiers.modifiers()) {
                if (!mergedEntries.contains(defaultEntry)) {
                    mergedEntries.add(defaultEntry);
                }
            }

            info.setReturnValue(new ItemAttributeModifiers(mergedEntries));
        }
    }
}
