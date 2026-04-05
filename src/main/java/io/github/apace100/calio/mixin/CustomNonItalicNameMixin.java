package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import io.github.apace100.calio.NbtConstants;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/***
 * These mixins allow setting a custom name for item stacks via NBT.
 */
public abstract class CustomNonItalicNameMixin {

    @Mixin(ItemStack.class)
    public abstract static class ModifyItalicDisplayItem {
        @Redirect(method = "getTooltipLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasCustomHoverName()Z"))
        private boolean hasCustomNameWhichIsItalic(ItemStack stack) {
            return stack.has(DataComponents.CUSTOM_NAME) && !Calio.hasNonItalicName(stack);
        }
    }

    @Mixin(Gui.class)
    public abstract static class ModifyItalicDisplayHud {
        @Redirect(method = "renderSelectedItemName", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasCustomHoverName()Z"))
        private boolean hasCustomNameWhichIsItalic(ItemStack stack) {
            return stack.has(DataComponents.CUSTOM_NAME) && !Calio.hasNonItalicName(stack);
        }
    }

    @Mixin(AnvilMenu.class)
    public abstract static class RemoveNonItalicOnRename {
        @Inject(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setHoverName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/world/item/ItemStack;"), locals = LocalCapture.CAPTURE_FAILHARD)
        private void removeNonItalicFlag(CallbackInfo ci, ItemStack itemStack, int i, int j, int k, ItemStack itemStack2) {
            if(itemStack2.has(DataComponents.CUSTOM_DATA)) {
                CompoundTag tag = itemStack2.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.getCompound("display").ifPresent(display -> {
                    if(display.contains(NbtConstants.NON_ITALIC_NAME)) {
                        display.remove(NbtConstants.NON_ITALIC_NAME);
                        tag.put("display", display);
                        itemStack2.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    }
                });
            }
        }
    }
}
