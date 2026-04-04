package io.github.apace100.calio.mixin;

import com.google.common.collect.Multimap;
import io.github.apace100.calio.Calio;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/***
 * This mixin makes sure that adding attribute modifiers to an equipment item does not overwrite the existing ones.
 * Updated for 1.21+ DataComponents API - the old NBT-based injection point no longer exists.
 */
@Mixin(ItemStack.class)
public abstract class DontOverwriteAttrModsMixin {

    // TODO: Verify against MC 26.1 source - ItemStack.getAttributeModifiers() has changed significantly
    // In 1.21+, attribute modifiers are stored as DataComponents, not NBT.
    // The old injection point targeting CompoundTag.getList no longer exists.
    // This mixin needs to be reworked to hook into the new DataComponents-based system.
    @Inject(at = @At("RETURN"), method = "getAttributeModifiers", cancellable = true)
    private void addAttributeModifiersFromItem(CallbackInfoReturnable<Multimap<Holder<Attribute>, AttributeModifier>> info) {
        ItemStack thisStack = (ItemStack)(Object)this;
        if(Calio.areEntityAttributesAdditional(thisStack)) {
            Multimap<Holder<Attribute>, AttributeModifier> result = info.getReturnValue();
            // Add the item's default attribute modifiers back
            // TODO: Verify against MC 26.1 source - how to get default attribute modifiers
        }
    }
}
