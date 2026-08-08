package me.deecaad.core.mechanics.conditions;

import me.deecaad.core.file.InlineSerializer;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import org.jetbrains.annotations.Nullable;

/**
 * A condition is a simple true/false statement that decides whether a {@link Mechanic} is allowed
 * to be used (on a specific entity, in a specific world, etc.).
 */
public abstract class Condition implements InlineSerializer<Condition> {

    private boolean isInverted;

    /**
     * Returns whether this condition needs to capture state when a mechanic is first cast. Stateful
     * conditions use this to compare the original state with the state observed after a delay or on a
     * later repeat. The default implementation is stateless.
     *
     * @return true when {@link #prime(CastData)} must run before delayed execution.
     */
    public boolean requiresPriming() {
        return false;
    }

    /**
     * Captures any state needed by this condition before a mechanic is delayed or repeated. Data may
     * be stored in {@link CastData#placeholders()}, which is retained by the mechanic lifecycle.
     *
     * @param cast The non-null targeted cast data.
     */
    public void prime(CastData cast) {
    }

    @Nullable @Override
    public String getWikiLink() {
        return "https://cjcrafter.gitbook.io/mechanics/#conditions";
    }

    /**
     * Returns <code>true</code> if {@link Mechanic} that holds this condition is allowed to be used.
     *
     * @param cast The non-null data involving the who/what/where.
     * @return true if the mechanic can be used.
     */
    public final boolean isAllowed(CastData cast) {
        return isInverted != isAllowed0(cast);
    }

    protected abstract boolean isAllowed0(CastData cast);

    protected Condition applyParentArgs(SerializeData data, Condition condition) throws SerializerException {
        condition.isInverted = data.of("Inverted").getBool().orElse(false);
        return condition;
    }
}
