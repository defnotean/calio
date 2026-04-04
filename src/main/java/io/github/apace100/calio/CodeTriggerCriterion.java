package io.github.apace100.calio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public class CodeTriggerCriterion extends SimpleCriterionTrigger<CodeTriggerCriterion.Conditions> {

    public static final CodeTriggerCriterion INSTANCE = new CodeTriggerCriterion();

    public static final Identifier ID = Identifier.parse("apacelib:code_trigger");

    @Override
    public Codec<Conditions> codec() {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayer player, String triggeredId) {
        this.trigger(player, (conditions) -> conditions.matches(triggeredId));
    }

    public static record Conditions(Optional<ContextAwarePredicate> player, String triggerId) implements SimpleInstance {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("trigger_id", "empty").forGetter(Conditions::triggerId)
            ).apply(instance, Conditions::new)
        );

        public static Conditions trigger(String triggerId) {
            return new Conditions(Optional.empty(), triggerId);
        }

        public boolean matches(String triggered) {
            return this.triggerId.equals(triggered);
        }
    }
}
