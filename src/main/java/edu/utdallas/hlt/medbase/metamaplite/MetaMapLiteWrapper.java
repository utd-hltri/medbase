package edu.utdallas.hlt.medbase.metamaplite;

import bioc.BioCDocument;
import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.struct.Pair;
import gov.nih.nlm.nls.metamap.document.FreeText;
import gov.nih.nlm.nls.metamap.lite.types.ConceptInfo;
import gov.nih.nlm.nls.metamap.lite.types.Entity;
import gov.nih.nlm.nls.metamap.lite.types.Ev;
import gov.nih.nlm.nls.ner.MetaMapLite;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Created by rmm120030 on 2/10/17.
 */
public class MetaMapLiteWrapper {
  private static Config config = Config.load("medbase.metamap");
  private static final Logger log = Logger.get(MetaMapLiteWrapper.class);

  private final MetaMapLite mml;

  public static MetaMapLiteWrapper getInstance() {
    return new MetaMapLiteWrapper(false);
  }

  public static MetaMapLiteWrapper getInstanceForSingleTerms() {
    return new MetaMapLiteWrapper(true);
  }

  public static MetaMapLiteWrapper getInstance(Properties properties) {
    return new MetaMapLiteWrapper(properties);
  }

  /**
   * Creates a wrapper for MetaMap Lite using the models/index from the resources.conf file
   * @param forSingleTerms will this object be used to find CUIs for single terms or multi-term spans of text?
   */
  private MetaMapLiteWrapper(boolean forSingleTerms) {
    final Properties properties = new Properties();
    MetaMapLite.expandModelsDir(properties, config.getString("models"));
    MetaMapLite.expandIndexDir(properties, config.getString("index"));
    MetaMapLite.expandModelsDir(properties, config.getString("models"));
    if (forSingleTerms) {
      properties.setProperty("metamaplite.enable.postagging", "false");
    }
    try {
      log.info("Initializing MetaMap Lite with properties: {}", properties);
      mml = new MetaMapLite(properties);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private MetaMapLiteWrapper(final Properties properties) {
    MetaMapLite.expandModelsDir(properties, config.getString("models"));
    MetaMapLite.expandIndexDir(properties, config.getString("index"));
    MetaMapLite.expandModelsDir(properties, config.getString("models"));
    try {
      log.info("Initializing MetaMap Lite with properties: {}", properties);
      mml = new MetaMapLite(properties);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<Entity> getEntities(String str) {
    final BioCDocument bd = FreeText.instantiateBioCDocument(str);
    bd.setID("1");
    try {
      return mml.processDocument(bd);
    } catch (Exception e) {
      log.error("Failed to parse {}: {}", str, e.getMessage());
      log.error(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  public Optional<ConceptInfo> getBestConcept(String span, Set<String> preferredTypes) {
    int mostSources = -1;
    ConceptInfo bestConcept = null;
    // first limit the search to the preferred semantic types
    if (preferredTypes.size() > 0) {
      for (Entity entity : getEntities(span)) {
//      log.info("For span: |{}| with score: {}", entity.getMatchedText(), entity.getScore());
        for (Ev ev : entity.getEvSet()) {
          final ConceptInfo conc = ev.getConceptInfo();
          if (conc.getSemanticTypeSet().stream().anyMatch(preferredTypes::contains)) {
            final int numSources = conc.getSemanticTypeSet().size();
            if (numSources > mostSources) {
              //          log.info("-{}", ev);
              bestConcept = conc;
              mostSources = numSources;
            }
          }
        }
      }
    }
    // if no concepts found of the preferred types, find all
    if (bestConcept == null) {
      for (Entity entity : getEntities(span)) {
        for (Ev ev : entity.getEvSet()) {
          final ConceptInfo conc = ev.getConceptInfo();
          final int numSources = conc.getSemanticTypeSet().size();
          if (numSources > mostSources) {
            bestConcept = conc;
            mostSources = numSources;
          }
        }
      }
    }

    return Optional.ofNullable(bestConcept);
  }

  public Optional<Pair<String, String>> getBestCuiAndNameForEntireSpan(String span) {
    int mostSources = -1;
    String bestCui = null;
    String bestName = null;
    for (Entity entity : getEntities(span)) {
//      log.info("For span: |{}| with score: {}", entity.getMatchedText(), entity.getScore());
      for (Ev ev : entity.getEvSet()) {
        final int numSources = ev.getConceptInfo().getSemanticTypeSet().size();
        if (numSources > mostSources) {
//          log.info("-{}", ev);
          bestCui = ev.getConceptInfo().getCUI();
          bestName = ev.getConceptInfo().getPreferredName().replaceAll("\\s", "_");
          mostSources = numSources;
        }
      }
    }
    return (bestCui == null || bestName == null) ? Optional.empty() : Optional.of(Pair.of(bestCui, bestName));
  }

  public Optional<String> getBestCuiForEntireSpan(String span) {
    int mostSources = -1;
    String bestCui = null;
    for (Entity entity : getEntities(span)) {
//      log.info("For span: |{}| with score: {}", entity.getMatchedText(), entity.getScore());
      for (Ev ev : entity.getEvSet()) {
        final int numSources = ev.getConceptInfo().getSemanticTypeSet().size();
        if (numSources > mostSources) {
//          log.info("-{}", ev);
          bestCui = ev.getConceptInfo().getCUI();
          mostSources = numSources;
        }
      }
    }
    return Optional.ofNullable(bestCui);
  }

  public Optional<String> getBestNameForEntireSpan(String span) {
    int mostSources = -1;
    String bestName = null;
    for (Entity entity : getEntities(span)) {
//      log.info("For span: |{}| with score: {}", entity.getMatchedText(), entity.getScore());
      for (Ev ev : entity.getEvSet()) {
        final int numSources = ev.getConceptInfo().getSemanticTypeSet().size();
        if (numSources > mostSources) {
//          log.info("-{}", ev);
          bestName = ev.getConceptInfo().getPreferredName().replaceAll("\\s", "_");
          mostSources = numSources;
        }
      }
    }
    return Optional.ofNullable(bestName);
  }
}
