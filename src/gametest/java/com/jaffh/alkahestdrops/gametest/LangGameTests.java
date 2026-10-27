package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.drops.DropFileFormat;
import com.jaffh.alkahestdrops.server.drops.DropIssue;
import com.jaffh.alkahestdrops.server.lang.Localizer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for the server-side translation of command and problem text: the lang files in the mod jar, placeholder
 * filling, nested texts, hover texts and the English fallbacks.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class LangGameTests {

    /**
     * Portuguese fills positional placeholders, and English renders the same key for logs.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void translatesPositionalPlaceholders(GameTestHelper helper) {
        Component issue = DropIssue.text("clamped_max", 5, 3);
        expect(Localizer.of("pt_br").text(issue), "é 5, acima do limite 3; reduzido para 3");
        expect(Localizer.english().text(issue), "is 5, above the limit 3; clamped to 3");
        helper.succeed();
    }

    /**
     * Texts passed as arguments are translated too.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void translatesNestedArguments(GameTestHelper helper) {
        Component issue = DropIssue.text("per_player_unused", DropFileFormat.BLOCK.filesName());
        expect(Localizer.of("pt_br").text(issue), "não tem efeito em arquivos de drops de blocos; só quem quebra recebe o drop");
        helper.succeed();
    }

    /**
     * Hover texts are translated with the text that holds them.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void translatesHoverText(GameTestHelper helper) {
        Component hint = Localizer.translatable("command." + AlkahestDrops.ID + ".page.show", 2);
        Component arrow = Component.literal("[>]").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hint)));
        HoverEvent hover = Localizer.of("pt_br").localize(arrow).getStyle().getHoverEvent();
        DropTestKit.check(hover != null, "hover text was dropped");
        expect(hover.getValue(HoverEvent.Action.SHOW_TEXT).getString(), "Mostrar página 2");
        helper.succeed();
    }

    /**
     * A language without a lang file and an invalid language code both read English.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void unknownLanguageFallsBackToEnglish(GameTestHelper helper) {
        Component issue = DropIssue.text("missing_key");
        expect(Localizer.of("xx_yy").text(issue), "missing required key");
        expect(Localizer.of("../en_us").text(issue), "missing required key");
        helper.succeed();
    }

    /**
     * The mod's keys carry their English text as fallback for places that never translate them, such as the
     * dedicated server console.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void keysCarryEnglishFallback(GameTestHelper helper) {
        Component issue = DropIssue.text("unknown_type", "spiral");
        DropTestKit.check(issue.getContents() instanceof TranslatableContents, "expected a translatable text");
        expect(((TranslatableContents) issue.getContents()).getFallback(), "unknown type '%s'");
        helper.succeed();
    }

    /**
     * Fails the test when a translated text is not the expected one.
     *
     * @param actual   Translated text. Never null.
     * @param expected Expected text. Never null.
     */
    private static void expect(String actual, String expected) {
        DropTestKit.check(actual.equals(expected), "expected \"" + expected + "\", got \"" + actual + "\"");
    }
}
