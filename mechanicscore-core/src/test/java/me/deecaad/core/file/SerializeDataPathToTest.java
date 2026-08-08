package me.deecaad.core.file;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SerializeDataPathToTest {

    @Test
    void resolvesNestedSerializerFromPathToConfiguration() throws SerializerException {
        YamlConfiguration raw = new YamlConfiguration();
        raw.set("Root.Child", "Templates.Child");

        Child template = new Child(7);
        Configuration serialized = new FastConfiguration();
        serialized.set("Templates.Child", template);

        SerializeData data = new SerializeData(new File("path-to-test.yml"), "Root", new BukkitConfig(raw));
        data.setPathToConfig(serialized);

        Child actual = data.of("Child").serialize(new Child()).orElseThrow();
        assertSame(template, actual);
    }

    @Test
    void preservesPathToConfigurationWhenSteppingIntoTemplate() throws SerializerException {
        YamlConfiguration raw = new YamlConfiguration();
        raw.set("Root.Block_Damage", "Templates.Block_Damage");

        Configuration serialized = new FastConfiguration();
        serialized.set("Templates.Block_Damage.Ticks_Before_Regenerate", 40);

        SerializeData data = new SerializeData(new File("path-to-test.yml"), "Root", new BukkitConfig(raw));
        data.setPathToConfig(serialized);

        SerializeData stepped = data.step(new BlockDamageLike());
        assertEquals(40, stepped.of("Ticks_Before_Regenerate").getInt().orElseThrow());
    }

    private static final class Child implements Serializer<Child> {

        private final int value;

        private Child() {
            this(0);
        }

        private Child(int value) {
            this.value = value;
        }

        @Override
        public String getKeyword() {
            return "Child";
        }

        @Override
        public @NotNull Child serialize(@NotNull SerializeData data) throws SerializerException {
            return new Child(data.of("Value").assertExists().getInt().getAsInt());
        }
    }

    private static final class BlockDamageLike implements Serializer<BlockDamageLike> {

        @Override
        public String getKeyword() {
            return "Block_Damage";
        }

        @Override
        public @NotNull BlockDamageLike serialize(@NotNull SerializeData data) {
            return new BlockDamageLike();
        }
    }
}
