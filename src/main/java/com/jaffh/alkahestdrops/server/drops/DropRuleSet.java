package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.api.DropTrigger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.tags.TagKey;

/**
 * Holds every drop and removal file of one reload, indexed by target: exact entries (ids and expanded
 * {@code mod:*}) in an identity map, tags checked when a target drops, and {@code *} files in one shared group.
 * The {@link DropTable} of each target is built on its first lookup, once tags are bound, and cached until
 * {@link #clearCache()} or the next reload replaces this set.
 *
 * @param <T> Registry entry type, such as {@code EntityType<?>} or {@code Block}.
 */
public final class DropRuleSet<T> {
    private final TargetMatcher<T> matcher;
    private final Map<T, RuleGroup> exactRules;
    private final Map<String, RuleGroup> namespaceRules;
    private final Map<TagKey<T>, RuleGroup> taggedRules;
    private final RuleGroup anyRules;
    private final Map<T, TriggerTables> tables = new ConcurrentHashMap<>();

    /**
     * Creates a rule set from frozen groups.
     *
     * @param matcher     Matcher of the registry. Never null.
     * @param exactRules     Groups of exact targets. Immutable, never null.
     * @param namespaceRules Groups of {@code mod:*} targets, by namespace. Immutable, never null.
     * @param taggedRules    Groups of tag targets. Immutable, never null.
     * @param anyRules       Group of {@code *} files. Never null.
     */
    private DropRuleSet(
            TargetMatcher<T> matcher,
            Map<T, RuleGroup> exactRules,
            Map<String, RuleGroup> namespaceRules,
            Map<TagKey<T>, RuleGroup> taggedRules,
            RuleGroup anyRules) {
        this.matcher = matcher;
        this.exactRules = exactRules;
        this.namespaceRules = namespaceRules;
        this.taggedRules = taggedRules;
        this.anyRules = anyRules;
    }

    /**
     * Creates a rule set with no files.
     *
     * @param matcher Matcher of the registry. Never null.
     * @param <T>     Registry entry type.
     * @return The empty rule set.
     */
    public static <T> DropRuleSet<T> empty(TargetMatcher<T> matcher) {
        return new Builder<>(matcher).build();
    }

    /**
     * Returns the table of a target, building and caching it on the first call.
     *
     * @param value Target that drops. Never null.
     * @return The table, {@link DropTable#EMPTY} when no file matches.
     */
    public DropTable tableOf(T value) {
        return triggerTablesOf(value).of(DropTrigger.DEATH);
    }

    /**
     * Returns the tables of every trigger of a target, built on the first lookup.
     *
     * @param value Target. Never null.
     * @return The tables, {@link TriggerTables#EMPTY} when no file matches.
     */
    public TriggerTables triggerTablesOf(T value) {
        return tables.computeIfAbsent(value, this::buildTables);
    }

    /**
     * Forgets every cached table, so the next lookups read the current tags.
     */
    public void clearCache() {
        tables.clear();
    }

    /**
     * Builds the table of a target from its exact, {@code mod:*}, tag and {@code *} files.
     *
     * @param value Target. Never null.
     * @return The tables, {@link TriggerTables#EMPTY} when no file matches.
     */
    private TriggerTables buildTables(T value) {
        List<DropFile> dropFiles = new ArrayList<>();
        List<RemovalRule> removals = new ArrayList<>();

        RuleGroup exact = exactRules.get(value);
        if (exact != null) exact.addTo(dropFiles, removals);
        if (matcher.canMatchPattern(value)) {
            RuleGroup namespace = namespaceRules.get(matcher.namespaceOf(value));
            if (namespace != null) namespace.addTo(dropFiles, removals);
            for (Map.Entry<TagKey<T>, RuleGroup> tagged : taggedRules.entrySet()) {
                if (matcher.isInTag(value, tagged.getKey())) tagged.getValue().addTo(dropFiles, removals);
            }
            anyRules.addTo(dropFiles, removals);
        }

        if (dropFiles.isEmpty() && removals.isEmpty()) return TriggerTables.EMPTY;
        return TriggerTables.build(dropFiles, removals);
    }

    /**
     * Represents the files that share one target key.
     *
     * @param dropFiles Drop files. Never null.
     * @param removals  Removal files. Never null.
     */
    private record RuleGroup(List<DropFile> dropFiles, List<RemovalRule> removals) {

        /**
         * Creates an empty group to fill while building.
         *
         * @return A group backed by mutable lists.
         */
        static RuleGroup mutable() {
            return new RuleGroup(new ArrayList<>(), new ArrayList<>());
        }

        /**
         * Returns an immutable copy of this group.
         *
         * @return The frozen group.
         */
        RuleGroup freeze() {
            return new RuleGroup(List.copyOf(dropFiles), List.copyOf(removals));
        }

        /**
         * Adds the files of this group to the lists of a table being built.
         *
         * @param dropFilesOut Receives the drop files. Never null.
         * @param removalsOut  Receives the removal files. Never null.
         */
        void addTo(List<DropFile> dropFilesOut, List<RemovalRule> removalsOut) {
            dropFilesOut.addAll(dropFiles);
            removalsOut.addAll(removals);
        }
    }

    /**
     * Collects the files of one reload by target, then freezes them into a {@link DropRuleSet}.
     *
     * @param <T> Registry entry type.
     */
    public static final class Builder<T> {
        private final TargetMatcher<T> matcher;
        private final Map<T, RuleGroup> exactRules = new IdentityHashMap<>();
        private final Map<String, RuleGroup> namespaceRules = new LinkedHashMap<>();
        private final Map<TagKey<T>, RuleGroup> taggedRules = new LinkedHashMap<>();
        private final RuleGroup anyRules = RuleGroup.mutable();

        /**
         * Creates an empty builder.
         *
         * @param matcher Matcher of the registry. Never null.
         */
        public Builder(TargetMatcher<T> matcher) {
            this.matcher = matcher;
        }

        /**
         * Adds a drop file to every group its target names.
         *
         * @param target Parsed target of the file. Never null.
         * @param file   Drop file. Never null.
         * @return This builder.
         */
        public Builder<T> addDrops(TargetMatcher.Target<T> target, DropFile file) {
            forEachGroup(target, group -> group.dropFiles().add(file));
            return this;
        }

        /**
         * Adds a removal file to every group its target names.
         *
         * @param target Parsed target of the file. Never null.
         * @param rule   Removal file. Never null.
         * @return This builder.
         */
        public Builder<T> addRemovals(TargetMatcher.Target<T> target, RemovalRule rule) {
            forEachGroup(target, group -> group.removals().add(rule));
            return this;
        }

        /**
         * Runs an action on every group a target names, creating missing groups.
         *
         * @param target Parsed target. Never null.
         * @param action Action to run. Never null.
         */
        private void forEachGroup(TargetMatcher.Target<T> target, Consumer<RuleGroup> action) {
            if (target instanceof TargetMatcher.Target.Values<T> values) {
                for (T value : values.values()) {
                    action.accept(exactRules.computeIfAbsent(value, key -> RuleGroup.mutable()));
                }
            } else if (target instanceof TargetMatcher.Target.Namespace<T> namespace) {
                action.accept(namespaceRules.computeIfAbsent(namespace.namespace(), key -> RuleGroup.mutable()));
            } else if (target instanceof TargetMatcher.Target.Tagged<T> tagged) {
                action.accept(taggedRules.computeIfAbsent(tagged.tag(), key -> RuleGroup.mutable()));
            } else {
                action.accept(anyRules);
            }
        }

        /**
         * Freezes the collected files.
         *
         * @return The new rule set, with an empty cache.
         */
        public DropRuleSet<T> build() {
            Map<T, RuleGroup> exact = new IdentityHashMap<>();
            exactRules.forEach((value, group) -> exact.put(value, group.freeze()));
            Map<String, RuleGroup> namespaces = new LinkedHashMap<>();
            namespaceRules.forEach((namespace, group) -> namespaces.put(namespace, group.freeze()));
            Map<TagKey<T>, RuleGroup> tagged = new LinkedHashMap<>();
            taggedRules.forEach((tag, group) -> tagged.put(tag, group.freeze()));
            return new DropRuleSet<>(
                    matcher,
                    Collections.unmodifiableMap(exact),
                    Collections.unmodifiableMap(namespaces),
                    Collections.unmodifiableMap(tagged),
                    anyRules.freeze());
        }
    }
}
