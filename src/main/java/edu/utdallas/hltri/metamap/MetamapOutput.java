package edu.utdallas.hltri.metamap;

import com.google.common.collect.Lists;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.scribe.io.Corpus;
import edu.utdallas.hltri.scribe.io.JsonCorpus;
import edu.utdallas.hltri.scribe.text.BaseDocument;
import edu.utdallas.hltri.scribe.text.Document;
import edu.utdallas.hltri.scribe.text.DuplicateAnnotationException;
import edu.utdallas.hltri.scribe.text.annotation.Sentence;

/**
 * Created by rmm120030 on 8/2/16.
 */
public class MetamapOutput {
  private static final Logger log = Logger.get(MetamapOutput.class);
  private static final SAXBuilder sb = new SAXBuilder();
  public static final String ANNOTATION_SET = "metamap";

  public static void outputSentences(String outdir, int maxDocs, Corpus<?> corpus, String sentAnnset) {
    try {
      Files.createDirectories(Paths.get(outdir));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    final List<String> dids = corpus.getIdStream().collect(Collectors.toList());
    int numFiles = (dids.size() / maxDocs) + (dids.size() % maxDocs == 0 ? 0 : 1);
    log.info("Writing {} files of at most {} documents each with 1 sentence per line to {}...", numFiles, maxDocs, outdir);
    for (int i = 0; i < numFiles; i++) {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outdir, "mm_input-" + i + ".txt")))) {
        for (int j = i*maxDocs; j < Math.min((i+1)*maxDocs, dids.size()); j++) {
          final Document<?> doc = corpus.load(dids.get(j));
          final List<Sentence> sentences = doc.get(sentAnnset, Sentence.TYPE);
          for (int sidx = 0; sidx < sentences.size(); sidx++) {
            writer.write(doc.getId() + ":" + sidx + "|" + sentences.get(sidx).asString().replace("\n"," "));
            writer.newLine();
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      log.info("Done file {}", i);
    }
  }

  public static <T extends BaseDocument> void attachMetamapConcepts(String metamapDir, JsonCorpus<T> corpus) {
    for (final File metamapFile : new File(metamapDir).listFiles()) {
      log.info("Attaching concepts from {}", metamapFile.toString());
      try {
        final org.jdom2.Document xml = sb.build(metamapFile);
        assert xml.getRootElement().getName().equals("MMOs") : "Root: " + xml.getRootElement().getName();
        Document<?> document = null;
        // MMOs/MMO
        for (Element mmo : xml.getRootElement().getChildren()) {
          int idx = 0;
          // MMOs/MMO/Utterances/Utterance
          for (Element utterance : mmo.getChild("Utterances").getChildren()) {
            document = getDocument(utterance, document, corpus);
            final String docString = document.asString().replace("\n", " ");
            final String uttText = utterance.getChild("UttText").getValue();
//          log.info("Processing utterance: {}", uttText);
//          final int uttLength = Integer.parseInt(utterance.getChild("UttLength").getValue());
//          assert uttText.length() == uttLength
//              : "Utterance is not the length it said it was...";
            idx = docString.indexOf(uttText, idx);
            final int uidx = Integer.parseInt(utterance.getChild("UttStartPos").getValue());
            final int offset = idx - uidx;
//          assert idx == uidx : String.format("Mismatching utterance offsets: given(%d) actual(%d).", uidx, idx);
//          assert uttText.equals(docString.substring(uidx, uidx + uttLength))
//              : String.format("Mismatching utterance offsets for str (%s) in doc %s", uttText, document.getGlobalId());
            // MMOs/MMO/Utterances/Utterance/Phrases/Phrase
            for (Element phrase : utterance.getChild("Phrases").getChildren()) {
              final Element mappings = phrase.getChild("Mappings");
              // only create a Concept if there are Candidate Mappings for it
              if (mappings != null && Integer.parseInt(mappings.getAttributeValue("Count")) > 0) {
                final String ptext = phrase.getChild("PhraseText").getValue();
                final int start = Integer.parseInt(phrase.getChild("PhraseStartPos").getValue()) + offset;
                idx = docString.indexOf(ptext, Math.max(0, start - 1));
                assert idx == start : String.format("Mismatching phrase offsets: given(%d) actual(%d).", start, idx);
                assert ptext.length() == Integer.parseInt(phrase.getChild("PhraseLength").getValue())
                    : "Phrase is not the length it said it was...";
                final int end = start + Integer.parseInt(phrase.getChild("PhraseLength").getValue());
                assert ptext.equals(docString.substring(start, end))
                    : String.format("Mismatching phrase offsets for str (%s) in doc %s", uttText, document.getId());
                try {
                  final MetamapConcept concept = MetamapConcept.TYPE.create(document, ANNOTATION_SET, start, end);
                  final List<MetamapCandidate> candidates = Lists.newArrayList();
//              idx = end;
                  // MMOs/MMO/Utterances/Utterance/Phrases/Phrase/Mappings/Mapping
                  for (Element mapping : mappings.getChildren()) {
                    // MMOs/MMO/Utterances/Utterance/Phrases/Phrase/Mappings/Mapping/MappingCandidates/Candidate
                    for (Element candidate : mapping.getChild("MappingCandidates").getChildren()) {
                      final MetamapCandidate.Builder builder = new MetamapCandidate.Builder();
                      builder.cui(candidate.getChild("CandidateCUI").getValue());
                      builder.score(Integer.parseInt(candidate.getChild("CandidateScore").getValue()));
                      builder.name(candidate.getChild("CandidatePreferred").getValue());
                      candidate.getChild("SemTypes").getChildren().forEach(e -> builder.addSemanticType(e.getValue()));
                      if (candidate.getChild("Negated").getValue().equals("1")) {
                        builder.negated();
                      }
                      candidates.add(builder.build());
                    }
                  }
                  concept.set(MetamapConcept.candidates, candidates);
                } catch (DuplicateAnnotationException e) {
                  log.warn("Ignoring duplicate annotation exception |{}| in doc {}", e.old, document.getId());
                }
              }
            }
          }
        }
        log.info("Added {} concepts to doc {}", document.get("metamap", MetamapConcept.TYPE).size(), document.getId());
        document.sync();
        document.close();
      } catch (JDOMException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Document<?> getDocument(final Element utterance, final Document<?> doc,
                                                         Corpus<?> corpus) {
    String did = utterance.getChild("PMID").getValue();
    if (did.contains(":")) {
      did = did.substring(0,did.indexOf(":"));
    }
    if (doc == null) {
      log.info("Opening doc {}", did);
      final Document<?> ndoc = corpus.load(did);
      ndoc.clear("metmap");
      return ndoc;
    }
    else if (!doc.getId().equals(did)) {
      log.info("Added {} concepts to doc {}", doc.get("metamap", MetamapConcept.TYPE).size(), doc.getId());
      log.info("Closing doc {} and opening doc {}", doc.getId(), did);
      doc.sync();
      doc.close();
      final Document<?> ndoc = corpus.load(did);
      ndoc.clear("metmap");
      return ndoc;
    }
    else {
      return doc;
    }
  }
}
