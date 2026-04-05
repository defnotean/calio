package io.github.apace100.calio.registry;

import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

/**
 * Pre-defined DataObjectFactory instances for common Minecraft types.
 */
public class DataObjectFactories {

    public static final DataObjectFactory<AttributeModifier> ATTRIBUTE_MODIFIER = new SimpleDataObjectFactory<>(
        new SerializableData()
            .add("id", SerializableDataTypes.IDENTIFIER, Identifier.parse("calio:unnamed_attribute_modifier"))
            .add("operation", SerializableDataTypes.MODIFIER_OPERATION)
            .add("value", SerializableDataTypes.DOUBLE),
        data -> new AttributeModifier(
            data.<Identifier>get("id"),
            data.getDouble("value"),
            data.get("operation")
        ),
        (modifier, serializableData) -> serializableData.instance()
            .set("id", modifier.id())
            .set("operation", modifier.operation())
            .set("value", modifier.amount())
    );

    public static final DataObjectFactory<ItemStack> ITEM_STACK = new SimpleDataObjectFactory<>(
        new SerializableData()
            .add("item", SerializableDataTypes.ITEM)
            .add("amount", SerializableDataTypes.INT, 1),
        data -> {
            net.minecraft.world.item.Item item = data.get("item");
            ItemStack stack = new ItemStack(item, data.getInt("amount"));
            return stack;
        },
        (stack, serializableData) -> serializableData.instance()
            .set("item", stack.getItem())
            .set("amount", stack.getCount())
    );
}
