package edu.utdallas.hlt.medbase.biopath;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.logging.LoggerFactory;
import edu.utdallas.hltri.util.Trie;

import java.util.*;

/**
 *
 * @author bryan sucks
 */
public class PairContextPatternGenerator {

  private static final Logger log = Logger.get(PairContextPatternGenerator.class);

  private static final Joiner joiner = Joiner.on(' ');
  private static final Splitter tokenizer = Splitter.on(' ');

  private static final String E1 = "<E1>",
                              E2 = "<E2>";

  private static boolean replace(Trie<String> seed, List<String> tokens, String value, List<String> result) {
    final List<String> match = new ArrayList<>();
    result.clear();
    Trie<String> trie = seed;
    boolean matched = false;

    for  (final String token : tokens) {
      // Try to advance the trie
      trie = trie.get(token);

      // We didn't have a match
      if (trie == null) {
        // We were on an in-progress match, so add everything we matched and start over
        if (!match.isEmpty()) {
          result.addAll(match);
          match.clear();
        }

        // Reset the trie & add the current token
        trie = seed;
        result.add(token);
      }

      // We did have a match
      else {
        // We finished our match so add the new value instead of the match
        if (trie.isLeaf()) {
          result.add(value);
          matched = true;
        }

        // We haven't finished our match yet so add it to the in-progress match list
        else {
          match.add(token);
        }
      }
    }

    // Return our fancy updated list of tokens, for science!
    return matched;
  }

  private static final ThreadLocal<List<String>> localResult = new ThreadLocal<List<String>>() {
    @Override protected List<String> initialValue() {
      return new ArrayList<>();
    }
  };

  public static Set<String> collect(Trie<String> s1, Trie<String> s2, String line) {
    List<String> result = localResult.get();
    result.clear();

    if (!replace(s1, Lists.newArrayList(tokenizer.split(line)), E1, result)) return Collections.emptySet();
    if (!replace(s2, new ArrayList<>(result), E2, result)) return Collections.emptySet();

    return generate(result);
  }

  public static Trie<String> newTrie(Iterable<String> seeds) {
    // Initialize a new empty trie
    Trie<String> root = new Trie<>();

    for (String seed : seeds) {
      // For each seed, we reset the trie
      Trie<String> node = root;

      // For each token, we add that token to the current node in the trie
      for (String token : tokenizer.split(seed)) {
        node = node.getOrAdd(token);
      }
    }

    return root;
  }

  private static final ThreadLocal<Set<String>> localPatterns = new ThreadLocal<Set<String>>() {
    @Override protected Set<String> initialValue() {
      return new TreeSet<>();
    }
  };

  private static final int MAX = 7;
  private static final int MAX_SENTENCE = 20;

  private static Set<String> generate(final List<String> tokensWithE1AndE2) {
    final Set<String> patterns = localPatterns.get();
    patterns.clear();
    final int e1 = tokensWithE1AndE2.indexOf(E1);
    final int e2 = tokensWithE1AndE2.indexOf(E2);

    if (e1 < 0 || e2 < 0 || e2 < e1) {
      log.warn("Skipping malformed line {}" + tokensWithE1AndE2);
    } else if (e2 - e1 > MAX || tokensWithE1AndE2.size() > MAX_SENTENCE) {
      log.warn("Skipping exceptionally long pattern");
    } else {
      addInternalWords(patterns, tokensWithE1AndE2, e1, e2, MAX);
      addSingleWordWildcards(patterns, tokensWithE1AndE2, e1, e2);
      addShortenedContexts(patterns, tokensWithE1AndE2, e1, e2);
      for (final String patt : Lists.newArrayList(patterns)) {
        addShortenedContexts(patterns, Lists.newArrayList(tokenizer.split(patt)), e1, e2);
      }
    }
    return patterns;
  }

  private static final ThreadLocal<StringBuilder> localSB = new ThreadLocal<StringBuilder>() {
    @Override protected StringBuilder initialValue() {
      return new StringBuilder();
    }
  };


  private static void addInternalWords(final Set<String> patterns,
                                       final List<String> tokens,
                                       final int e1,
                                       final int e2,
                                       final int max) {
    StringBuilder builder = localSB.get();
    for (int t = e1 + 1; t < e2; t++) {
      //for (; m >= (e2-e1-1); m--) {
      for (int p = 0; p <2; p++) {
        builder.setLength(0);
        builder.append(joiner.join(tokens.subList(0, e1+1)));
        builder.append(" .* ");
        if (p == 0) {
          builder.append(tokens.get(t));
        } else {
          builder.append(tokens.get(t).substring(0, Math.min(5, tokens.get(t).length())));
          builder.append(".*");
        }
        builder.append(" [^ ]*{0,").append(max).append("} ");
        joiner.appendTo(builder, tokens.subList(e2, tokens.size()));

        patterns.add(builder.toString());
        //}
      }
    }
  }

  private static void addSingleWordWildcards(final Set<String> contexts,
                                             final List<String> tokens,
                                             final int e1,
                                             final int e2) {
    StringBuilder builder = localSB.get();
    for (int t = e1 + 1; t < e2; t++) {
      builder.setLength(0);
      builder.append(joiner.join(tokens.subList(0, t)));
      builder.append(" [^ ]+ ");
      builder.append(joiner.join(tokens.subList(t+1,tokens.size())));
      contexts.add(builder.toString());
    }
  }

  private static void addShortenedContexts(final Set<String> buffer,
                                           final List<String> tokens,
                                           final int e1,
                                           final int e2) {
    int numToks = tokens.size();
    for (int b = 0; b <= e1; b++) {
      for (int e = numToks; e > e2; e--) {
        //if (b == e1 && e == e2+1) { continue; }
        if (b == 0 && e == numToks) { continue; }
        buffer.add(joiner.join(tokens.subList(b, e)));
      }
    }
  }

  public static String reverse(String context) {
    int index1 = context.indexOf(E1);
    int index2 = context.lastIndexOf(E2);
    char[] chars = context.toCharArray();
    chars[index1+2] = '2';
    chars[index2+2] = '1';
    return new String(chars);
  }

}
