package me.deecaad.core.mechanics.defaultmechanics;

import me.deecaad.core.file.MapConfigLike;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.conditions.Condition;
import me.deecaad.core.mechanics.targeters.Targeter;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechanicPrimingTest {

    @Test
    void statefulConditionTargetsOnceAndPrimesBeforeEvaluation() throws SerializerException {
        LivingEntity source = livingEntity("source");
        LivingEntity first = livingEntity("first");
        LivingEntity second = livingEntity("second");

        RecordingMechanic mechanic = new RecordingMechanic();
        SerializeData empty = new SerializeData(new File("mechanic-test.yml"), null, new MapConfigLike(Map.of()));
        mechanic.applyParentArgs(empty, mechanic);

        CountingTargeter targeter = new CountingTargeter(List.of(first, second));
        mechanic.targeter = targeter;
        mechanic.conditions = List.of(new PrimedCondition());

        mechanic.use(new CastData(source, null, null));

        assertEquals(1, targeter.calls);
        assertEquals(List.of(first.getUniqueId(), second.getUniqueId()), mechanic.usedTargets);
    }

    private static LivingEntity livingEntity(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        return (LivingEntity) Proxy.newProxyInstance(
                MechanicPrimingTest.class.getClassLoader(),
                new Class<?>[] { LivingEntity.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(null, 0.0, 0.0, 0.0);
                    case "isValid", "isAlive" -> true;
                    case "toString" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == float.class)
            return 0.0F;
        if (type == double.class)
            return 0.0D;
        if (type == char.class)
            return '\0';
        return null;
    }

    private static final class RecordingMechanic extends Mechanic {

        private final List<UUID> usedTargets = new ArrayList<>();

        @Override
        protected void use0(CastData cast) {
            usedTargets.add(cast.getTarget().getUniqueId());
        }

        @Override
        public @NotNull NamespacedKey getKey() {
            return NamespacedKey.minecraft("recording_mechanic");
        }

        @Override
        public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
            return applyParentArgs(data, new RecordingMechanic());
        }
    }

    private static final class CountingTargeter extends Targeter {

        private final List<LivingEntity> targets;
        private int calls;

        private CountingTargeter(List<LivingEntity> targets) {
            this.targets = targets;
        }

        @Override
        public boolean isEntity() {
            return true;
        }

        @Override
        protected Iterator<CastData> getTargets0(CastData cast) {
            calls++;
            Iterator<LivingEntity> iterator = targets.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public CastData next() {
                    cast.setTargetEntity(iterator.next());
                    return cast;
                }
            };
        }

        @Override
        public @NotNull NamespacedKey getKey() {
            return NamespacedKey.minecraft("counting_targeter");
        }

        @Override
        public @NotNull Targeter serialize(@NotNull SerializeData data) {
            return this;
        }
    }

    private static final class PrimedCondition extends Condition {

        private static final String KEY = "primed";

        @Override
        public boolean requiresPriming() {
            return true;
        }

        @Override
        public void prime(CastData cast) {
            cast.placeholders().put(KEY, cast.getTarget().getUniqueId().toString());
        }

        @Override
        protected boolean isAllowed0(CastData cast) {
            return cast.getTarget().getUniqueId().toString().equals(cast.placeholders().get(KEY));
        }

        @Override
        public @NotNull NamespacedKey getKey() {
            return NamespacedKey.minecraft("primed_condition");
        }

        @Override
        public @NotNull Condition serialize(@NotNull SerializeData data) {
            return this;
        }
    }
}
