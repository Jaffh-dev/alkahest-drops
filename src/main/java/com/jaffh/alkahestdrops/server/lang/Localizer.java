package com.jaffh.alkahestdrops.server.lang;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.jaffh.alkahestdrops.AlkahestDrops;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the mod's own text into one language on the server, from the lang files inside the mod jar.
 * Command output is translated here instead of on the client because the commands measure the text to lay out
 * their pages. Keys a language lacks fall back to English; keys of vanilla and other mods are left for the client.
 */
public final class Localizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String ENGLISH_CODE = "en_us";
    private static final String LANG_FOLDER = "assets/" + AlkahestDrops.ID + "/lang/";
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z0-9_]{1,16}");
    /** Same placeholders vanilla accepts: {@code %s}, {@code %1$s} and {@code %%}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final Localizer ENGLISH = new Localizer(readTable(ENGLISH_CODE));
    /** Localizer of every language asked for, by language code; languages without a lang file map to English. */
    private static final Map<String, Localizer> BY_LANGUAGE = new ConcurrentHashMap<>();

    private final Map<String, String> table;

    /**
     * Creates a localizer.
     *
     * @param table Translations by key, English included as fallback. Never null.
     */
    private Localizer(Map<String, String> table) {
        this.table = table;
    }

    /**
     * Returns the English localizer, used for logs and the server console.
     *
     * @return The English localizer.
     */
    public static Localizer english() {
        return ENGLISH;
    }

    /**
     * Returns the localizer of a language.
     *
     * @param languageCode Language code reported by a client, such as {@code pt_br}. Never null.
     * @return Its localizer, or the English one when the mod has no lang file for it.
     */
    public static Localizer of(String languageCode) {
        String code = languageCode.toLowerCase(Locale.ROOT);
        if (code.equals(ENGLISH_CODE) || !LANGUAGE_CODE.matcher(code).matches()) return ENGLISH;
        return BY_LANGUAGE.computeIfAbsent(code, Localizer::load);
    }

    /**
     * Loads the localizer of a language from its lang file, with English for the keys it lacks.
     *
     * @param code Language code, already checked. Never null.
     * @return The new localizer, or the English one when the mod has no lang file for the language.
     */
    private static Localizer load(String code) {
        Map<String, String> translations = readTable(code);
        if (translations.isEmpty()) return ENGLISH;
        Map<String, String> merged = new HashMap<>(ENGLISH.table);
        merged.putAll(translations);
        return new Localizer(Map.copyOf(merged));
    }

    /**
     * Returns the localizer of whoever runs a command: the player's client language, or English for the console,
     * command blocks and other non-player sources.
     *
     * @param source Command source. Never null.
     * @return The localizer.
     */
    public static Localizer forSource(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? of(player.getLanguage()) : ENGLISH;
    }

    /**
     * Creates translatable text of this mod with its English text as fallback, so places that never pass through
     * {@link #localize}, such as the dedicated server console, still read English instead of the raw key.
     *
     * @param key  Full translation key. Never null.
     * @param args Values of the key's placeholders. Never null.
     * @return The text.
     */
    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatableWithFallback(key, ENGLISH.table.get(key), args);
    }

    /**
     * Translates every key of this mod in a text, including its arguments, siblings and hover texts. Other keys
     * stay translatable.
     *
     * @param component Text to translate. Never null.
     * @return A new text with the same styles.
     */
    public MutableComponent localize(Component component) {
        MutableComponent result = component.getContents() instanceof TranslatableContents translatable
                && table.containsKey(translatable.getKey())
                ? fill(table.get(translatable.getKey()), translatable.getArgs())
                : MutableComponent.create(component.getContents());
        result.setStyle(localizeHover(component.getStyle()));
        for (Component sibling : component.getSiblings()) result.append(localize(sibling));
        return result;
    }

    /**
     * Translates a text to a plain string.
     *
     * @param component Text to translate. Never null.
     * @return The translated string, without styles.
     */
    public String text(Component component) {
        return localize(component).getString();
    }

    /**
     * Translates the hover text of a style.
     *
     * @param style Style to translate. Never null.
     * @return The style with a translated hover text, or the same style when it shows no text on hover.
     */
    private Style localizeHover(Style style) {
        HoverEvent hover = style.getHoverEvent();
        Component hoverText = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
        if (hoverText == null) return style;
        return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, localize(hoverText)));
    }

    /**
     * Fills a translation template with its arguments, the way vanilla does.
     *
     * @param template Translated template, such as {@code "%s chance"}. Never null.
     * @param args     Arguments of the key. Never null.
     * @return A text made of the template parts and the translated arguments.
     */
    private MutableComponent fill(String template, Object[] args) {
        MutableComponent result = Component.empty();
        Matcher matcher = PLACEHOLDER.matcher(template);
        int nextArgument = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) result.append(template.substring(end, matcher.start()));
            end = matcher.end();
            String type = matcher.group(2);
            if (type.equals("%") || type.isEmpty()) {
                result.append("%");
            } else if (type.equals("s")) {
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : nextArgument++;
                result.append(index >= 0 && index < args.length ? argument(args[index]) : Component.literal(matcher.group()));
            } else {
                result.append(matcher.group());
            }
        }
        if (end < template.length()) result.append(template.substring(end));
        return result;
    }

    /**
     * Turns one argument into text.
     *
     * @param argument Argument of a key: a text, or any value shown with {@code toString}. May be null.
     * @return The translated text, or the value as a literal.
     */
    private MutableComponent argument(@Nullable Object argument) {
        if (argument instanceof Component component) return localize(component);
        return Component.literal(String.valueOf(argument));
    }

    /**
     * Reads one lang file of the mod.
     *
     * @param code Language code, already checked. Never null.
     * @return The translations by key; empty when the file is missing or invalid.
     */
    private static Map<String, String> readTable(String code) {
        String path = LANG_FOLDER + code + ".json";
        try (InputStream stream = Localizer.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return Map.of();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, String> table = GSON.fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
                return table == null ? Map.of() : Map.copyOf(table);
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("[alkahestdrops] cannot read {}: {}", path, e.getMessage());
            return Map.of();
        }
    }
}
