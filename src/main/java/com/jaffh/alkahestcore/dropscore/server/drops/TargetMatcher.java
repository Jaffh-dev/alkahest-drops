package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Parses the target of a file ({@code entity} or {@code block}) for one registry: an id, {@code "mod:*"}
 * (every entry of a namespace), {@code "#tag"} or {@code "*"} (everything). Patterns never match the entries
 * the module protects (players for mobs); those are only reachable through their exact id.
 *
 * @param <T> Registry entry type, such as {@code EntityType<?>} or {@code Block}.
 */
public final class TargetMatcher<T extends IForgeRegistryEntry<T>> {
    private static final String ANY = "*";
    private static final String TAG_PREFIX = "#";
    private static final String NAMESPACE_SUFFIX = ":*";

    private final IForgeRegistry<T> registry;
    private final BiPredicate<T, TagKey<T>> tagTest;
    private final Predicate<T> canMatchPattern;
    private final String entryName;

    /**
     * Creates a matcher for one registry.
     *
     * @param registry        Registry of the targets. Never null.
     * @param tagTest         Tells whether an entry is in a bound tag. Never null.
     * @param canMatchPattern Tells whether an entry may be matched by {@code mod:*}, {@code #tag} and {@code *}.
     *                        Never null.
     */
    public TargetMatcher(IForgeRegistry<T> registry, BiPredicate<T, TagKey<T>> tagTest, Predicate<T> canMatchPattern) {
        this.registry = registry;
        this.tagTest = tagTest;
        this.canMatchPattern = canMatchPattern;
        this.entryName = registry.getRegistryName().getPath().replace('_', ' ');
    }

    /**
     * Parses a raw target. {@code "mod:*"} is expanded here from the registry; tags are kept as keys because
     * they are not bound yet while datapack files are read.
     *
     * @param raw Raw target. Never null.
     * @param ctx Context of the target key. Never null.
     * @return The parsed target.
     * @throws DropDataException If the id or tag is malformed (error), or names nothing registered (warning).
     */
    public Target<T> parse(String raw, ParseContext ctx) throws DropDataException {
        if (raw.equals(ANY)) return new Target.Any<>();
        if (raw.startsWith(TAG_PREFIX)) {
            return new Target.Tagged<>(TagKey.create(registry.getRegistryKey(), DropValues.parseId(raw.substring(1), ctx)));
        }
        if (raw.endsWith(NAMESPACE_SUFFIX)) return parseNamespace(raw.substring(0, raw.length() - NAMESPACE_SUFFIX.length()), ctx);

        ResourceLocation id = DropValues.parseId(raw, ctx);
        if (!registry.containsKey(id)) throw ctx.unknownId("'" + id + "' is not a registered " + entryName);
        return new Target.Values<>(List.of(registry.getValue(id)));
    }

    /**
     * Expands {@code "mod:*"} into every entry of the namespace that patterns may match.
     *
     * @param namespace Namespace before {@code :*}. Never null.
     * @param ctx       Context of the target key. Never null.
     * @return The entries of the namespace.
     * @throws DropDataException If the namespace is malformed (error) or has no entry (warning, mod not loaded).
     */
    private Target<T> parseNamespace(String namespace, ParseContext ctx) throws DropDataException {
        ResourceLocation probe = ResourceLocation.tryParse(namespace + ":probe");
        if (probe == null || !probe.getNamespace().equals(namespace)) {
            throw ctx.error("'" + namespace + "' is not a valid namespace");
        }

        List<T> values = registry.getEntries().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(namespace))
                .map(Map.Entry::getValue)
                .filter(canMatchPattern)
                .toList();
        if (values.isEmpty()) throw ctx.unknownId("no " + entryName + " is registered in namespace '" + namespace + "'");
        return new Target.Values<>(values);
    }

    /**
     * Tells whether an entry is in a tag. Only valid once tags are bound, after the reload ends.
     *
     * @param value Entry to check. Never null.
     * @param tag   Tag to check. Never null.
     * @return True when the bound tag contains the entry.
     */
    public boolean isInTag(T value, TagKey<T> tag) {
        return tagTest.test(value, tag);
    }

    /**
     * Tells whether {@code #tag} and {@code *} targets may match an entry.
     *
     * @param value Entry to check. Never null.
     * @return False for entries the module protects, such as players.
     */
    public boolean canMatchPattern(T value) {
        return canMatchPattern.test(value);
    }

    /**
     * Represents a parsed file target.
     *
     * @param <T> Registry entry type.
     */
    public sealed interface Target<T> {

        /**
         * Targets a fixed set of entries: one id, or every entry of a namespace.
         *
         * @param values Targeted entries. Immutable, never null.
         * @param <T>    Registry entry type.
         */
        record Values<T>(List<T> values) implements Target<T> {
        }

        /**
         * Targets every entry of a tag, checked when the drop happens.
         *
         * @param tag Targeted tag. Never null.
         * @param <T> Registry entry type.
         */
        record Tagged<T>(TagKey<T> tag) implements Target<T> {
        }

        /**
         * Targets every entry patterns may match.
         *
         * @param <T> Registry entry type.
         */
        record Any<T>() implements Target<T> {
        }
    }
}
