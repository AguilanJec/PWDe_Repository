package com.pwde.app.sensors.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Pins the on-device keyword tokenizers to upstream's `sherpa-onnx-cli text2token` behaviour.
 *
 * Ported from the Java app's KeywordTokenizerTest so the copy that ships here keeps the same
 * guarantees. Every test in this file is pure logic; the ones that need the bundled model live in
 * `GigaSpeechTokenizerTest`.
 */
public class KeywordTokenizerTest {

  private static final String B = SentencePieceUnigramTokenizer.WORD_BOUNDARY;

  @Test
  public void spokenWords_upperCasesAndSpellsOutSingleDigits() {
    assertEquals(Arrays.asList("SKILL", "ONE"), KeywordTokenizer.spokenWords(" skill  1 "));
    assertEquals(Arrays.asList("LEVEL", "12"), KeywordTokenizer.spokenWords("level 12"));
  }

  @Test
  public void unigram_picksHighestScoringSegmentation() {
    Map<String, Float> scores = new HashMap<>();
    scores.put(B, -5f);
    scores.put("A", -3f);
    scores.put("B", -3f);
    scores.put(B + "A", -2f);
    scores.put("AB", -1f);
    SentencePieceUnigramTokenizer tokenizer = new SentencePieceUnigramTokenizer(scores);
    // Candidate splits: [▁A, B] = -5 beats [▁, AB] = -6 and [▁, A, B] = -11.
    assertEquals(Arrays.asList(B + "A", "B"), tokenizer.tokenize("ab"));
  }

  @Test
  public void unigram_unknownCharacterMakesPhraseUnsupported() {
    Map<String, Float> scores = new HashMap<>();
    scores.put(B, -1f);
    scores.put("A", -1f);
    assertNull(new SentencePieceUnigramTokenizer(scores).tokenize("az"));
  }

  @Test
  public void lexicon_loadsOnlyWantedWordsAndKeepsFirstPronunciation() {
    String dictionary = "FIRST F ER1 S T\nFIRST F ER S\nSECOND S EH1 K AH0 N D\n";
    LexiconTokenizer tokenizer =
        LexiconTokenizer.fromLexicon(
            new BufferedReader(new StringReader(dictionary)), new HashSet<>(Arrays.asList("FIRST")));
    assertEquals(Arrays.asList("F", "ER1", "S", "T"), tokenizer.tokenize("first"));
    assertNull(tokenizer.tokenize("second"));
  }

  @Test
  public void keywordList_labelsPhrasesAndSkipsUnsupported() {
    Map<String, List<String>> lexicon = new HashMap<>();
    lexicon.put("GO", Arrays.asList("G", "OW1"));
    lexicon.put("BACK", Arrays.asList("B", "AE1", "K"));
    lexicon.put("ODD", Arrays.asList("AA1", "ZZ"));
    Set<String> tokens = new HashSet<>(Arrays.asList("G", "OW1", "B", "AE1", "K", "AA1"));

    KeywordList list =
        KeywordList.build(
            Arrays.asList("go back", "go", "pwde", "odd", "go back"),
            new LexiconTokenizer(lexicon),
            tokens);

    assertEquals(Arrays.asList("G OW1 B AE1 K @k0", "G OW1 @k1"), list.lines);
    assertEquals("go back", list.phrasesByLabel.get("k0"));
    assertEquals("go", list.phrasesByLabel.get("k1"));
    // "odd" maps to a token the model lacks, which would crash the native spotter.
    assertEquals(Arrays.asList("pwde", "odd"), list.unsupported);
    assertEquals("G OW1 B AE1 K @k0\nG OW1 @k1\n", list.fileContent());
  }

  @Test
  public void keywordList_addsStressVariantsForReducedVowels() {
    Map<String, List<String>> lexicon = new HashMap<>();
    lexicon.put("JOYSTICK", Arrays.asList("JH", "OY1", "S", "T", "IH2", "K"));
    Set<String> tokens =
        new HashSet<>(Arrays.asList("JH", "OY1", "S", "T", "IH0", "IH1", "IH2", "K"));

    KeywordList list =
        KeywordList.build(
            Collections.singletonList("joystick"), new LexiconTokenizer(lexicon), tokens);

    // The dictionary says IH2 (secondary stress). A speaker who gives that syllable full stress
    // (IH1) or reduces it further (IH0) produces phones the single spelling can never match, so
    // both alternates must be registered -- each with its own label pointing at the same phrase.
    assertEquals(
        Arrays.asList("JH OY1 S T IH2 K @k0", "JH OY1 S T IH0 K @k1", "JH OY1 S T IH1 K @k2"),
        list.lines);
    assertEquals(1, list.phraseCount);
    for (String label : list.phrasesByLabel.keySet()) {
      assertEquals("joystick", list.phrasesByLabel.get(label));
    }
  }

  @Test
  public void keywordList_leavesPrimaryStressAndAbsentAlternatesAlone() {
    Map<String, List<String>> lexicon = new HashMap<>();
    lexicon.put("GO", Arrays.asList("G", "OW1"));
    lexicon.put("BACK", Arrays.asList("B", "AE1", "K"));
    lexicon.put("STICK", Arrays.asList("S", "T", "IH2", "K"));
    // No IH0/IH1 here, so nothing may be added: an unknown token kills the native spotter.
    Set<String> tokens = new HashSet<>(Arrays.asList("G", "OW1", "B", "AE1", "K", "S", "T", "IH2"));

    KeywordList list =
        KeywordList.build(Arrays.asList("go back", "stick"), new LexiconTokenizer(lexicon), tokens);

    assertEquals(Arrays.asList("G OW1 B AE1 K @k0", "S T IH2 K @k1"), list.lines);
    assertEquals(2, list.phraseCount);
  }

  @Test
  public void keywordList_capsStressVariantsPerPhrase() {
    Map<String, List<String>> lexicon = new HashMap<>();
    lexicon.put("BANANA", Arrays.asList("B", "AH0", "N", "AE1", "N", "AH0"));
    Set<String> tokens = new HashSet<>(Arrays.asList("B", "AH0", "AH1", "AH2", "N", "AE1"));

    KeywordList list =
        KeywordList.build(
            Collections.singletonList("banana"), new LexiconTokenizer(lexicon), tokens);

    // Base plus two alternates only: the cap keeps the keyword trie in check.
    assertEquals(3, list.lines.size());
    assertEquals("B AH2 N AE1 N AH0 @k1", list.lines.get(1));
    assertEquals("B AH1 N AE1 N AH0 @k2", list.lines.get(2));
  }

  @Test
  public void keywordList_writesPerPhraseTuningAndInheritsForTheRest() {
    Map<String, List<String>> lexicon = new HashMap<>();
    lexicon.put("FIRST", Arrays.asList("F", "ER1", "S", "T"));
    lexicon.put("GO", Arrays.asList("G", "OW1"));
    // Primary stress only, so neither phrase grows stress alternates.
    Set<String> tokens = new HashSet<>(Arrays.asList("F", "ER1", "S", "T", "G", "OW1"));

    KeywordList tuned =
        KeywordList.build(
            Arrays.asList("first", "go"),
            new LexiconTokenizer(lexicon),
            tokens,
            Collections.singletonMap("first", WakeWordSensitivity.HIGH.getTuning()));

    // "first" carries its own boost and threshold; "go" inherits the spotter's defaults.
    assertEquals(Arrays.asList("F ER1 S T :3.0 #0.1 @k0", "G OW1 @k1"), tuned.lines);
    assertEquals("first", tuned.phrasesByLabel.get("k0"));
  }

  @Test
  public void keywordList_leavesNormalSensitivityOutOfTheLine() {
    Map<String, List<String>> lexicon = new HashMap<>();
    // Primary stress only, so no stress variants are added and the line count stays 1.
    lexicon.put("CURSOR", Arrays.asList("K", "ER1", "S", "OR1"));
    Set<String> tokens = new HashSet<>(Arrays.asList("K", "ER1", "S", "OR1"));

    KeywordList list =
        KeywordList.build(
            Collections.singletonList("cursor"),
            new LexiconTokenizer(lexicon),
            tokens,
            Collections.singletonMap("cursor", WakeWordSensitivity.NORMAL.getTuning()));

    // NORMAL must write no numbers at all, or it would pin the spotter's config to stale values.
    assertEquals(Collections.singletonList("K ER1 S OR1 @k0"), list.lines);
  }
}
