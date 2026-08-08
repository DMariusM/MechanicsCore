package me.deecaad.core.mechanics.defaultmechanics;

import com.cjcrafter.foliascheduler.TaskImplementation;
import me.deecaad.core.MechanicsCore;
import me.deecaad.core.file.InlineSerializer;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.ChanceSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.conditions.Condition;
import me.deecaad.core.mechanics.targeters.Targeter;
import me.deecaad.core.utils.RandomUtil;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A Mechanic is the most powerful tool available to server-admins through config. A Mechanic is an
 * action, basically a block of code, that the admin writes in YAML format. These actions can be
 * executed conditionally using {@link Condition} and can get specifically targeted using
 * {@link Targeter}.
 */
public abstract class Mechanic implements InlineSerializer<Mechanic> {

    public Targeter targeter;
    public List<Condition> conditions;
    private int repeatAmount;
    private int repeatInterval;
    private int delayBeforePlay;
    private double chance;

    /**
     * Default constructor for serializer.
     */
    public Mechanic() {
    }

    public Targeter getTargeter() {
        return targeter;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public int getRepeatAmount() {
        return repeatAmount;
    }

    public int getRepeatInterval() {
        return repeatInterval;
    }

    public int getDelayBeforePlay() {
        return delayBeforePlay;
    }

    public double getChance() {
        return chance;
    }

    /**
     * This method will trigger this Mechanic.
     *
     * <p>
     * This method <i>probably shouldn't</i> be overridden. It handles the global repeat, delay,
     * targeter, and condition code. The behavior of the specific Mechanic is handled by the protected
     * {@link #use0(CastData)} method.
     *
     * @param cast The non-null cast data.
     */
    public void use(CastData cast) {

        // Chance to execute mechanic
        if (!RandomUtil.chance(chance))
            return;

        // Stateful conditions must capture the targeted entities now, before a delay or repeat can
        // change their state. Stateless mechanics keep the old re-target-on-each-repeat behavior.
        boolean requiresPriming = conditions.stream().anyMatch(Condition::requiresPriming);
        List<CastData> primedTargets = requiresPriming ? prepareTargets(cast.clone()) : null;

        if (requiresPriming && primedTargets.isEmpty())
            return;

        // If there is no need to schedule event, skip the event process.
        if (repeatAmount == 1 && repeatInterval == 1 && delayBeforePlay == 0) {
            if (requiresPriming)
                handlePreparedTargets(primedTargets);
            else
                handleTargetersAndConditions(cast.clone()); // clone since targeters modify the cast
            return;
        }

        // Schedule a repeating event to trigger the mechanic multiple times.
        Location location;
        if (cast.hasTargetLocation())
            location = cast.getTargetLocation();
        else
            location = cast.getSourceLocation();

        TaskImplementation<Void> task = MechanicsCore.getInstance().getFoliaScheduler().region(location).runAtFixedRate(new Consumer<>() {
            int runs = 0;

            @Override
            public void accept(TaskImplementation<Void> scheduledTask) {
                if (runs++ >= repeatAmount) {
                    scheduledTask.cancel();
                    return;
                }

                if (requiresPriming)
                    handlePreparedTargets(primedTargets);
                else
                    handleTargetersAndConditions(cast.clone()); // clone since targeters modify the cast
            }
        }, Math.max(delayBeforePlay, 1), Math.max(repeatInterval, 1));

        // This allows developers to consume task ids from playing a Mechanic.
        // Good for canceling tasks early.
        if (cast.getTaskIdConsumer() != null)
            cast.getTaskIdConsumer().accept(task);
    }

    protected void handleTargetersAndConditions(CastData cast) {

        OUTER : for (Iterator<CastData> it = targeter.getTargets(cast); it.hasNext();) {
            CastData target = it.next();
            for (Condition condition : conditions)
                if (!condition.isAllowed(target))
                    continue OUTER;

            use0(target);
        }
    }

    /**
     * Resolves each target exactly once and primes all conditions against a detached copy of that
     * target. Multi-target targeters reuse one mutable {@link CastData}, so each result must be copied
     * before the iterator advances.
     *
     * @param cast The non-null cast to target.
     * @return The prepared target list.
     */
    protected List<CastData> prepareTargets(CastData cast) {
        List<CastData> prepared = new ArrayList<>();
        Iterator<CastData> targets = targeter.getTargets(cast);

        while (targets.hasNext()) {
            CastData target = copyCast(targets.next());
            for (Condition condition : conditions)
                condition.prime(target);
            prepared.add(target);
        }

        return prepared;
    }

    /**
     * Evaluates and executes targets previously captured by {@link #prepareTargets(CastData)}.
     */
    protected void handlePreparedTargets(List<CastData> preparedTargets) {
        OUTER : for (CastData prepared : preparedTargets) {
            CastData target = copyCast(prepared);
            for (Condition condition : conditions)
                if (!condition.isAllowed(target))
                    continue OUTER;

            use0(target);
        }
    }

    /**
     * Makes a detached copy of cast data. {@link CastData#clone()} intentionally shares temporary
     * placeholders, but prepared multi-target casts need independent maps so later targeter results
     * cannot overwrite earlier target placeholders or condition snapshots.
     */
    private CastData copyCast(CastData cast) {
        CastData copy = new CastData(cast.getSource(), cast.itemTitle(), cast.item(), cast.getTaskIdConsumer());
        copy.placeholders().clear();
        copy.placeholders().putAll(cast.placeholders());

        if (cast.getTarget() != null)
            copy.setTargetEntity(cast.getTarget());
        if (cast.hasTargetLocation())
            copy.setTargetLocation(cast.getTargetLocationSupplier());

        return copy;
    }

    /**
     * This method should be overridden to define the behavior of the Mechanic. For example, a Potion
     * mechanic may use the {@link CastData#getTarget()} method to apply a potion effect.
     *
     * @param cast The non-null data including source/target information.
     */
    protected abstract void use0(CastData cast);

    public Mechanic applyParentArgs(SerializeData data, Mechanic mechanic) throws SerializerException {
        mechanic.repeatAmount = data.of("Repeat_Amount").assertRange(1, null).getInt().orElse(1);
        mechanic.repeatInterval = data.of("Repeat_Interval").assertRange(1, null).getInt().orElse(1);
        mechanic.delayBeforePlay = data.of("Delay_Before_Play").assertRange(0, null).getInt().orElse(0);
        mechanic.chance = data.of("Chance").serialize(ChanceSerializer.class).orElse(1.0);
        return mechanic;
    }
}
