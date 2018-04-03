package edu.utdallas.hlt.medbase.umls;

import com.google.common.base.CharMatcher;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.radix.node.concrete.SmartArrayBasedNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.scribe.annotators.Annotator;
import edu.utdallas.hltri.scribe.text.Attribute;
import edu.utdallas.hltri.scribe.text.BaseDocument;
import edu.utdallas.hltri.scribe.text.Document;
import edu.utdallas.hltri.scribe.text.DuplicateAnnotationException;
import edu.utdallas.hltri.scribe.text.annotation.Chunk;

/**
 *
 * Created by travis on 1/26/16.
 */
@SuppressWarnings("unused")
public class UMLSLexicon implements Annotator<BaseDocument> {
  private final Logger                         log     = Logger.get(UMLSLexicon.class);
  private final InvertedRadixTree<Set<String>> lexicon = new ConcurrentInvertedRadixTree<>(new SmartArrayBasedNodeFactory());


  private UMLSLexicon() {
    final String umlsPath = Config.load("medbase.umls").getString("path");
    log.info("Loading UMLS data from {}...", umlsPath);
    final StringBuilder sb = new StringBuilder();
    int field;
    boolean english;
    try (final BufferedReader reader = new BufferedReader(new FileReader(umlsPath))) {
      for (String line, phrase = null, id = null; (line = reader.readLine()) != null; ) {
        sb.setLength(0);
        english = true;
        field = 0;
        char c;
        for (int i = 0; i < line.length(); i++) {
          c = line.charAt(i);
          if ('|' == c) {
            if (field == 0) {
              id = sb.toString();
//              log.info("Got ID |{}| from line {}", id, line);
            } else if (field == 14) {
              phrase = '|' + sb.toString() + '|';
//              log.info("Got phrase |{}| from line {}", phrase, line);
            } else if (field == 1 && !"ENG".equals(sb.toString())) {
//              log.info("Got lang |{}| from line {}", sb.toString(), line);
              english = false;
              break;
            } else if (field == 16 && !"N".equals(sb.toString())) {
//              log.info("Got status |{}| from line {}", sb.toString(), line);
              english = false;
              break;
            } else if (field == 17) {
              boolean match = sb.length() > 0 && (Integer.valueOf(sb.toString()) & 512) != 0;
              if (!match) {
                english = false;
                break;
              }
            }
            sb.setLength(0);
            field++;
          } else {
            if (field == 0 || field == 1 || field == 16 || field == 17) {
              sb.append(c);
            } else if (field == 14) {
              if (CharMatcher.WHITESPACE.matches(c)) {
                sb.append('|');
              } else {
                if (Character.isUpperCase(line.charAt(i + 1))) {
                  english = false;
                  break;
                } else {
                  sb.append(Character.toLowerCase(c));
                }
              }
            }
          }
        }

        if (!english) {
          continue;
        }

        log.trace("Mapped {} to {}", phrase, id);
        final Set<String> previous = lexicon.putIfAbsent(phrase, Sets.newHashSet(id));
        if (previous != null) {
          previous.add(id);
        }
      }
      log.info("Loaded {} mappings for {} CUIs", lexicon.size());
    } catch (IOException ex) {
      throw Throwables.propagate(ex);
    }
  }

  @SuppressWarnings("WeakerAccess")
  public static Attribute<Chunk, Set<String>> cuis = Attribute.inferred("cuis");

  @Override public <B extends BaseDocument> void annotate(Document<B> document) {
    final String text = '|' + CharMatcher.WHITESPACE.replaceFrom(document, '|') + '|';
    for (KeyValuePair<Set<String>> hit : lexicon.getKeyValuePairsForKeysContainedIn(text)) {
      final String word = hit.getKey().toString();
      for (int index = text.indexOf(word); index >= 0; index = text.indexOf(word, index + 1)) {
        try {
          final Chunk umlsConcept = Chunk.TYPE.create(document, "umls", index , index + word.length() - 2);
          umlsConcept.set(cuis, hit.getValue());
          log.info("Annotated {}", umlsConcept.describe());
        } catch (DuplicateAnnotationException ex) {
          final Chunk umlsConcept = Chunk.TYPE.wrap(document, ex.old);
          umlsConcept.get(cuis).addAll(hit.getValue());
          log.info("Updated {}", umlsConcept.describe());
        }
      }
    }
  }

  private static class LazyUMLSLoader {
    private static final UMLSLexicon INSTANCE = new UMLSLexicon();
  }

  public static UMLSLexicon instance() {
    return LazyUMLSLoader.INSTANCE;
  }

  /**
   * Returns a lazy iterable which returns the set of keys in the tree which are contained in the given document.
   *
   * @param document A document to be scanned for keys contained in the tree
   * @return The set of keys in the tree which are contained in the given document
   */
  public Iterable<CharSequence> findMatches(CharSequence document) {
    return lexicon.getKeysContainedIn(document);
  }
}
