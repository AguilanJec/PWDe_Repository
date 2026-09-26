package com.pwde.app.sensors.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Checks the on-device tokenizer against upstream's `text2token` output for the GigaSpeech model
 * the Testing Station loads.
 *
 * These read the model straight off disk at `src/debug/assets/...` (Gradle runs unit tests from the
 * module directory), so they only make sense for the debug variant -- hence `src/testDebug`. The
 * expected tokens are upstream's, which means a pass proves the copied `bpe.model` and `tokens.txt`
 * are intact and that `KeywordList` spells phrases the way the native spotter expects.
 */
public class GigaSpeechTokenizerTest {

  private static final String DIR =
      "src/debug/assets/models/sherpa-kws-zipformer-gigaspeech-3.3M-2024-01-01/";

  private static final String[] REFERENCE_PHRASES = {
    "first", "second", "third", "edit", "joystick mode", "keyboard mode", "mouse mode",
    "open menu", "close window", "scroll up", "scroll down", "go back", "select item",
    "start dictation", "stop listening"
  };

  private static final String[] REFERENCE_TOKENS = {
    "\u2581FIRST", "\u2581SECOND", "\u2581TH IR D", "\u2581 ED IT",
    "\u2581JO Y S TIC K \u2581MO DE", "\u2581K E Y B O ARD \u2581MO DE",
    "\u2581MO U SE \u2581MO DE", "\u2581O P EN \u2581ME N U", "\u2581C LO SE \u2581W IN D OW",
    "\u2581S C RO LL \u2581UP", "\u2581S C RO LL \u2581DOWN", "\u2581GO \u2581BACK",
    "\u2581SE LE C T \u2581IT E M", "\u2581START \u2581DI C T ATION",
    "\u2581ST O P \u2581LI S T EN ING"
  };

  @Test
  public void matchesText2TokenReference() throws Exception {
    SentencePieceUnigramTokenizer tokenizer =
        SentencePieceUnigramTokenizer.fromModelProto(
            Files.readAllBytes(new File(DIR + "bpe.model").toPath()));
    Set<String> symbols = readSymbols();

    for (int i = 0; i < REFERENCE_PHRASES.length; i++) {
      List<String> tokens = tokenizer.tokenize(REFERENCE_PHRASES[i]);
      assertEquals(REFERENCE_PHRASES[i], REFERENCE_TOKENS[i], String.join(" ", tokens));
      // An unknown token makes the native spotter terminate the process, so check every one.
      assertTrue(symbols.containsAll(tokens));
    }
  }

  /**
   * Pins what this model does with phrases shaped like PWDe's own wake words, so the panel's
   * "can't spot" list is a known quantity rather than a surprise on the device.
   */
  @Test
  public void pinsProductPhrasesTheModelCanSpell() throws Exception {
    SentencePieceUnigramTokenizer tokenizer =
        SentencePieceUnigramTokenizer.fromModelProto(
            Files.readAllBytes(new File(DIR + "bpe.model").toPath()));
    KeywordList list =
        KeywordList.build(
            Arrays.asList("hey pwde", "pwde", "porcupine", "start playing", "play game"),
            tokenizer,
            readSymbols());

    // "PWDE" is not an English word, so the model spells it as text pieces (P W DE) instead of
    // rejecting it. A speaker therefore has to say it the way it is written, not as letters.
    assertEquals(
        Arrays.asList(
            "\u2581HE Y \u2581P W DE @k0",
            "\u2581P W DE @k1",
            "\u2581P OR C U P IN E @k2",
            "\u2581START \u2581PLAY ING @k3",
            "\u2581PLAY \u2581GA ME @k4"),
        list.lines);
  }

  private static Set<String> readSymbols() throws Exception {
    try (BufferedReader reader =
        Files.newBufferedReader(new File(DIR + "tokens.txt").toPath(), StandardCharsets.UTF_8)) {
      return KeywordList.readTokenSymbols(reader);
    }
  }
}
