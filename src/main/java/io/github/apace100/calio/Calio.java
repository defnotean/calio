package io.github.apace100.calio;

import io.github.apace100.calio.network.CalioNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;

public class Calio implements ModInitializer {

    public static final Logger LOGGER = LogManager.getLogger(Calio.class);

	public static final ThreadLocal<RegistryAccess> DYNAMIC_REGISTRIES = new ThreadLocal<>();
	public static final ThreadLocal<Map<TagKey<?>, Collection<Holder<?>>>> REGISTRY_TAGS = new ThreadLocal<>();

	@Override
	public void onInitialize() {
        CriteriaTriggers.register(CodeTriggerCriterion.ID.toString(), CodeTriggerCriterion.INSTANCE);
        CalioNetworking.register();
	}

	public static boolean hasNonItalicName(ItemStack stack) {
		if(!stack.has(DataComponents.CUSTOM_DATA)) return false;
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag display = tag.contains("display") ? tag.getCompound("display") : null;
		return display != null && display.getBoolean(NbtConstants.NON_ITALIC_NAME);
	}

	public static void setNameNonItalic(ItemStack stack) {
		if(stack != null) {
			CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
			display.putBoolean(NbtConstants.NON_ITALIC_NAME, true);
			tag.put("display", display);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	public static boolean areEntityAttributesAdditional(ItemStack stack) {
		if(!stack.has(DataComponents.CUSTOM_DATA)) return false;
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(NbtConstants.ADDITIONAL_ATTRIBUTES) && tag.getBoolean(NbtConstants.ADDITIONAL_ATTRIBUTES);
	}

	/**
	 * Sets whether the item stack counts the entity attribute modifiers specified in its tag as additional,
	 * meaning they won't overwrite the equipment's inherent modifiers.
	 * @param stack
	 * @param additional
	 */
	public static void setEntityAttributesAdditional(ItemStack stack, boolean additional) {
		if(stack != null) {
			CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			if(additional) {
				tag.putBoolean(NbtConstants.ADDITIONAL_ATTRIBUTES, true);
			} else {
				tag.remove(NbtConstants.ADDITIONAL_ATTRIBUTES);
			}
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	public static <T> boolean areTagsEqual(ResourceKey<? extends Registry<T>> registryKey, TagKey<T> tag1, TagKey<T> tag2) {
		return areTagsEqual(tag1, tag2);
	}

	public static <T> boolean areTagsEqual(TagKey<T> tag1, TagKey<T> tag2) {
		if(tag1 == tag2) {
			return true;
		}
		if(tag1 == null || tag2 == null) {
			return false;
		}
		if(!tag1.registry().equals(tag2.registry())) {
			return false;
		}
		if(!tag1.location().equals(tag2.location())) {
			return false;
		}
		return true;
	}
}
