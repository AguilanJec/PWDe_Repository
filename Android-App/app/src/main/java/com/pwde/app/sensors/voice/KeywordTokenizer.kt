/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pwde.app.sensors.voice

import java.io.BufferedReader
import java.util.Locale

/**
 * Turns a configured voice phrase into the model tokens sherpa-onnx's `KeywordSpotter` expects.
 *
 * The native spotter only accepts **pre-tokenized** keywords (upstream generates them on a
 * workstation with the Python `sherpa-onnx-cli text2token`). PWDe's phrases are user-editable, so
 * the same encoding is reproduced here on-device:
 * - [SentencePieceUnigramTokenizer] for the GigaSpeech model, whose `bpe.model` is (despite the
 *   name) a SentencePiece **unigram** model: the tokens are the Viterbi-best segmentation by piece
 *   log-probability, not BPE merges.
 * - [LexiconTokenizer] for the zh-en model, whose English tokens are CMU phonemes looked up in its
 *   `en.phone` pronunciation dictionary.
 *
 * Both were checked token-for-token against `text2token` output for 15 reference phrases (see
 * `KeywordTokenizerTest`). Everything here is Android-free so it runs as a plain JVM test.
 */
interface KeywordTokenizer {

  /**
   * True when [tokenize] emits CMU phonemes carrying a stress digit (`IH2`) rather than text
   * pieces. Stress is the one thing a spelling cannot pin down: the dictionary holds a single
   * pronunciation per word, so a keyword registered as `IH2` never matches a speaker who says
   * `IH1` or reduces it to `IH0` (see [KeywordList.stressVariants]).
   */
  val usesStressMarkedPhones: Boolean
    get() = false

  /** @return the model tokens for [phrase], or null if any word can't be represented. */
  fun tokenize(phrase: String): List<String>?

  companion object {
    private val WHITESPACE = Regex("\\s+")
    private val DIGIT_WORDS =
        arrayOf("ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE")

    /**
     * Upper-cased words of a phrase, as both tokenizers were trained on upper-case text. A lone
     * digit is spelled out ("skill 1" -> SKILL ONE) because neither model has digit tokens; the
     * spotter still reports the original phrase through its keyword label.
     */
    @JvmStatic
    fun spokenWords(phrase: String): List<String> {
      val words = ArrayList<String>()
      for (raw in phrase.trim().split(WHITESPACE)) {
        if (raw.isEmpty()) {
          continue
        }
        val digit = if (raw.length == 1 && raw[0] in '0'..'9') raw[0] - '0' else -1
        words.add(if (digit >= 0) DIGIT_WORDS[digit] else raw.uppercase(Locale.US))
      }
      return words
    }
  }
}

/** Viterbi segmentation over a SentencePiece unigram vocabulary (`bpe.model`). */
class SentencePieceUnigramTokenizer(private val scores: Map<String, Float>) : KeywordTokenizer {

  private val maxPieceLength = scores.keys.maxOfOrNull { it.length } ?: 0

  override fun tokenize(phrase: String): List<String>? {
    val words = KeywordTokenizer.spokenWords(phrase)
    if (words.isEmpty()) {
      return null
    }
    val tokens = ArrayList<String>()
    for (word in words) {
      tokens.addAll(segment(WORD_BOUNDARY + word) ?: return null)
    }
    return tokens
  }

  /** Highest total log-probability split of [text]; ties keep the earliest split point. */
  private fun segment(text: String): List<String>? {
    val n = text.length
    val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
    val from = IntArray(n + 1) { -1 }
    best[0] = 0.0
    for (end in 1..n) {
      for (start in maxOf(0, end - maxPieceLength) until end) {
        if (best[start] == Double.NEGATIVE_INFINITY) {
          continue
        }
        val score = scores[text.substring(start, end)] ?: continue
        val total = best[start] + score
        if (total > best[end]) {
          best[end] = total
          from[end] = start
        }
      }
    }
    if (best[n] == Double.NEGATIVE_INFINITY) {
      return null
    }
    val pieces = ArrayList<String>()
    var end = n
    while (end > 0) {
      val start = from[end]
      pieces.add(text.substring(start, end))
      end = start
    }
    pieces.reverse()
    return pieces
  }

  companion object {
    /** SentencePiece's word-boundary marker (U+2581). */
    const val WORD_BOUNDARY = "▁"

    private const val PIECE_TYPE_NORMAL = 1

    /**
     * Reads the piece table from a serialized SentencePiece `ModelProto`. Only the few fields
     * needed are decoded (ModelProto.pieces = 1; SentencePiece.piece = 1, score = 2, type = 3), so
     * no protobuf dependency is required. Control/user-defined pieces (`<blk>`, `<unk>`) are
     * skipped: the encoder never emits them for ordinary text.
     */
    @JvmStatic
    fun fromModelProto(bytes: ByteArray): SentencePieceUnigramTokenizer {
      val scores = HashMap<String, Float>()
      val model = ProtoReader(bytes, 0, bytes.size)
      while (model.hasMore()) {
        val tag = model.readVarint().toInt()
        if (tag ushr 3 == 1 && tag and 7 == 2) {
          val length = model.readVarint().toInt()
          val piece = ProtoReader(bytes, model.position, model.position + length)
          model.position += length
          var text: String? = null
          var score = 0f
          var type = PIECE_TYPE_NORMAL
          while (piece.hasMore()) {
            val pieceTag = piece.readVarint().toInt()
            when (pieceTag ushr 3) {
              1 -> {
                val textLength = piece.readVarint().toInt()
                text = String(bytes, piece.position, textLength, Charsets.UTF_8)
                piece.position += textLength
              }
              2 -> score = Float.fromBits(piece.readFixed32())
              3 -> type = piece.readVarint().toInt()
              else -> piece.skip(pieceTag and 7)
            }
          }
          if (text != null && type == PIECE_TYPE_NORMAL) {
            scores[text] = score
          }
        } else {
          model.skip(tag and 7)
        }
      }
      require(scores.isNotEmpty()) { "No pieces found in SentencePiece model" }
      return SentencePieceUnigramTokenizer(scores)
    }
  }

  /** Minimal protobuf wire-format reader over `bytes[position until limit]`. */
  private class ProtoReader(val bytes: ByteArray, var position: Int, private val limit: Int) {
    fun hasMore(): Boolean = position < limit

    fun readVarint(): Long {
      var result = 0L
      var shift = 0
      while (true) {
        val b = bytes[position++].toInt() and 0xff
        result = result or ((b and 0x7f).toLong() shl shift)
        if (b < 0x80) {
          return result
        }
        shift += 7
      }
    }

    fun readFixed32(): Int {
      var value = 0
      for (i in 0 until 4) {
        value = value or ((bytes[position++].toInt() and 0xff) shl (8 * i))
      }
      return value
    }

    fun skip(wireType: Int) {
      when (wireType) {
        0 -> readVarint()
        1 -> position += 8
        2 -> {
          // Read the length first: `position += readVarint()` would add it to the stale position.
          val length = readVarint().toInt()
          position += length
        }
        5 -> position += 4
        else -> throw IllegalArgumentException("Unsupported protobuf wire type $wireType")
      }
    }
  }
}

/** Word -> phoneme lookup in a pronunciation dictionary (`en.phone`: `WORD PH1 PH2 ...`). */
class LexiconTokenizer(private val lexicon: Map<String, List<String>>) : KeywordTokenizer {

  override val usesStressMarkedPhones: Boolean = true

  override fun tokenize(phrase: String): List<String>? {
    val words = KeywordTokenizer.spokenWords(phrase)
    if (words.isEmpty()) {
      return null
    }
    val tokens = ArrayList<String>()
    for (word in words) {
      tokens.addAll(lexicon[word] ?: return null)
    }
    return tokens
  }

  companion object {
    /**
     * Loads only the entries for [wantedWords] (upper case), so the 126k-line dictionary is
     * streamed rather than held in memory. The first pronunciation of a word wins, as in
     * `text2token`.
     */
    @JvmStatic
    fun fromLexicon(reader: BufferedReader, wantedWords: Set<String>): LexiconTokenizer {
      val lexicon = HashMap<String, List<String>>()
      reader.forEachLine { line ->
        val space = line.indexOf(' ')
        if (space > 0) {
          val word = line.substring(0, space)
          if (word in wantedWords && word !in lexicon) {
            lexicon[word] = line.substring(space + 1).trim().split(' ').filter { it.isNotEmpty() }
          }
        }
      }
      return LexiconTokenizer(lexicon)
    }
  }
}

/**
 * The numbers one keyword line carries, in sherpa-onnx's `:boost #threshold` form — the per-phrase
 * half of the spotter's tuning. A `null` field means the line omits that number and inherits the
 * spotter's own (see `WakeWordSpotterTuning`), which is what [WakeWordSensitivity.NORMAL] does.
 *
 * A higher boost keeps the keyword alive through beam search; a lower threshold triggers on weaker
 * acoustic evidence. Both raise the false-alarm rate. Nothing here clamps: the Testing Station lets
 * the user type these by hand, and a value that is out of range is the user's to see and fix.
 */
data class WakeWordTuning(val boost: Float?, val threshold: Float?) {
  /** True when the line writes no numbers at all and inherits the spotter's configured pair. */
  val inheritsBoth: Boolean
    get() = boost == null || threshold == null

  /** The line reads as `:3.0 #0.1`, or empty when it inherits. Shown next to the edit fields. */
  val display: String
    get() = if (inheritsBoth) "inherits" else " :$boost #$threshold".trim()

  companion object {
    /** What a phrase gets when it is not tuned at all. */
    val INHERIT: WakeWordTuning = WakeWordTuning(null, null)

    /**
     * The `:boost #threshold` suffix for one line, or nothing so the phrase inherits the spotter's
     * configured defaults. Both numbers go through `Float.toString` because `String.format` follows
     * the device locale and would write `3,5` where the parser needs `3.5`.
     */
    fun suffix(tuning: WakeWordTuning?): String {
      val boost = tuning?.boost ?: return ""
      val threshold = tuning.threshold ?: return ""
      return " :$boost #$threshold"
    }
  }
}

/**
 * One-tap presets. Each is a [WakeWordTuning] the user is free to edit further, so the toggle is a
 * starting point rather than a ceiling. Upstream's own defaults are 1.5 / 0.25.
 *
 * All three are set as eager as they are useful: a higher boost keeps the keyword alive through
 * beam search, and a lower threshold triggers on weaker acoustic evidence. Both raise the
 * false-alarm rate, which is why they are per phrase rather than global.
 */
enum class WakeWordSensitivity(val label: String, val tuning: WakeWordTuning) {
  /** Inherits the spotter's configured score and threshold. */
  NORMAL("Normal", WakeWordTuning.INHERIT),
  HIGH("High", WakeWordTuning(3.0f, 0.10f)),
  /** What every phrase starts on: the spotter's own defaults written out explicitly (see [WakeWordSpotterTuning]). */
  MAX("Max", WakeWordTuning(6.0f, 0.0f)),
  ;

  companion object {
    /**
     * The preset whose numbers match [tuning] exactly, or null once the numbers have been edited
     * by hand — so the toggle can show nothing selected instead of lying.
     */
    fun presetFor(tuning: WakeWordTuning): WakeWordSensitivity? =
        entries.firstOrNull { it.tuning == tuning }
  }
}

/**
 * The spotter's keyword list for a set of phrases: one `tok tok tok @label` line per accepted
 * spelling, the label -> phrase map to decode hits, and the phrases it can't represent.
 *
 * A phrase normally gets one line, but a phone-based model gets a few: its pronunciation dictionary
 * holds a single spelling per word, so [stressVariants] adds the other stress forms of every
 * reduced vowel. Each line carries its own label and [phrasesByLabel] maps all of them back to the
 * phrase.
 */
class KeywordList(
    @JvmField val lines: List<String>,
    @JvmField val phrasesByLabel: Map<String, String>,
    @JvmField val unsupported: List<String>,
) {

  /** Phrases the spotter listens for; a phrase with stress variants counts once. */
  @JvmField val phraseCount: Int = phrasesByLabel.values.toSet().size

  /** The keywords file content sherpa-onnx reads. */
  fun fileContent(): String = lines.joinToString("\n", postfix = "\n")

  companion object {
    /**
     * @param validTokens the model's `tokens.txt` symbols. Every emitted token is checked against
     *     it because the native spotter **terminates the process** on an unknown token instead of
     *     returning an error.
     * @param tuning per-phrase `boost, threshold` pairs. A phrase missing from it — or mapped to
     *     [WakeWordTuning.INHERIT] — writes no numbers and uses the spotter's configured pair.
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        phrases: Collection<String>,
        tokenizer: KeywordTokenizer,
        validTokens: Set<String>,
        tuning: Map<String, WakeWordTuning> = emptyMap(),
    ): KeywordList {
      val lines = ArrayList<String>()
      val phrasesByLabel = LinkedHashMap<String, String>()
      val unsupported = ArrayList<String>()
      for (phrase in LinkedHashSet(phrases)) {
        val tokens = tokenizer.tokenize(phrase)
        if (tokens.isNullOrEmpty() || !validTokens.containsAll(tokens)) {
          unsupported.add(phrase)
          continue
        }
        val variants =
            if (tokenizer.usesStressMarkedPhones) stressVariants(tokens, validTokens)
            else listOf(tokens)
        for (variant in variants) {
          // Labels are opaque ids: phrases contain spaces, which the keywords format forbids.
          val label = "k" + phrasesByLabel.size
          phrasesByLabel[label] = phrase
          lines.add(variant.joinToString(" ") + WakeWordTuning.suffix(tuning[phrase]) + " @" + label)
        }
      }
      return KeywordList(lines, phrasesByLabel, unsupported)
    }

    /**
     * [tokens] first, then a few variants differing only in the stress digit of a **non-primary**
     * vowel, at most [MAX_VARIANTS_PER_PHRASE] lines in total.
     *
     * The dictionary pins one pronunciation per word (`JOYSTICK JH OY1 S T IH2 K`) while
     * `tokens.txt` can decode all three stress forms of a phone (`IH0`, `IH1`, `IH2`). A speaker who
     * does not reduce that syllable the same way produces phones the single spelling can never
     * match, so the keyword silently fails; registering the alternates lets any of them fire the
     * same phrase. Primary-stressed vowels (`OW1`) are left alone: they carry the word and are
     * already the form speakers produce.
     */
    @JvmStatic
    fun stressVariants(tokens: List<String>, validTokens: Set<String>): List<List<String>> {
      val variants = ArrayList<List<String>>()
      variants.add(tokens)
      for (index in tokens.indices) {
        if (variants.size >= MAX_VARIANTS_PER_PHRASE) {
          break
        }
        val token = tokens[index]
        if (!STRESSED_PHONE.matches(token)) {
          continue
        }
        val stress = token.last()
        if (stress == PRIMARY_STRESS) {
          continue
        }
        for (alternate in stressAlternates(stress)) {
          if (variants.size >= MAX_VARIANTS_PER_PHRASE) {
            break
          }
          val candidate = token.dropLast(1) + alternate
          if (candidate in validTokens) {
            val variant = ArrayList(tokens)
            variant[index] = candidate
            variants.add(variant)
          }
        }
      }
      return variants
    }

    /** Symbols of a sherpa-onnx `tokens.txt` (`symbol id` per line). */
    @JvmStatic
    fun readTokenSymbols(reader: BufferedReader): Set<String> {
      val symbols = HashSet<String>()
      reader.forEachLine { line ->
        val space = line.lastIndexOf(' ')
        if (space > 0) {
          symbols.add(line.substring(0, space))
        }
      }
      return symbols
    }

    /**
     * Upper bound on keyword lines per phrase. Every line is another path the trie can match, so
     * this keeps the added false-trigger surface (and `maxActivePaths`) in check.
     */
    private const val MAX_VARIANTS_PER_PHRASE = 3

    /** CMU marks primary stress with `1`, secondary with `2` and no stress with `0`. */
    private const val PRIMARY_STRESS = '1'

    /** A stress-marked phoneme: two or three letters followed by the stress digit (`IH2`). */
    private val STRESSED_PHONE = Regex("[A-Z]{2,3}[012]")

    /** The other stress forms to try for [stress], most different first. */
    private fun stressAlternates(stress: Char): List<Char> =
        if (stress == '2') listOf('0', '1') else listOf('2', '1')
  }
}
