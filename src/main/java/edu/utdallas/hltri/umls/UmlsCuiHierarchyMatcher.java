package edu.utdallas.hltri.umls;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.util.HierarchicalPatternMatcher;

/**
 * Created by travis on 7/20/16.
 */
public class UmlsCuiHierarchyMatcher extends HierarchicalPatternMatcher {
  private final static Logger log = Logger.get(UmlsCuiHierarchyMatcher.class);

  protected static Path cachePath = Config.load("medbase").getPath("umls.cui-matcher-path");

  public static UmlsService umls = new UmlsService();

  protected final int limit;

  protected final Set<String> seedCuis;

  protected final Set<String> seen = new HashSet<>();

  protected void putAtoms(String cui, Collection<String> path) {
    log.debug("Getting atoms for {}", cui);
    for (String atom : umls.getAtomsForCui(cui)) {
      addPattern(atom, path);
    }
  }

  protected UmlsCuiHierarchyMatcher() {
    this.limit = Integer.MAX_VALUE;
    this.seedCuis = new HashSet<>();
  }

  protected UmlsCuiHierarchyMatcher(int limit, Iterable<String> seedCuis) {
    this.limit = limit;
    this.seedCuis = Sets.newHashSet(seedCuis);
  }

  public void initialize() {
    for (String cui : seedCuis) {
      final Collection<String> path = Collections.singleton(cui);
      putAtoms(cui, path);
      buildCuiTree(cui, path, limit - 1);
    }
  }

  protected void buildCuiTree(String cui, Collection<String> path, int limit) {
    if (limit > 0) {
      List<String> children = umls.getNarrowerCuis(cui);
      log.debug("Acquired {} children for {} at depth {} from root", children.size(), cui, this.limit - limit);
      for (String child : umls.getNarrowerCuis(cui)) {
        if (!labels.contains(child)) {
          Collection<String> childPath = Lists.newLinkedList(path);
          childPath.add(child);
          putAtoms(child, childPath);
          buildCuiTree(child, childPath, limit - 1);
        } else {
          log.warn("Encountered cycle for {}", child);
        }
      }
    }
  }


  public static UmlsCuiHierarchyMatcher forCui(String cui) {
    return forCuisLimited(Integer.MAX_VALUE, cui);
  }

  public static UmlsCuiHierarchyMatcher forCuis(String... cuis) {
    return forCuisLimited(Integer.MAX_VALUE, cuis);
  }

  public static UmlsCuiHierarchyMatcher forCuis(Iterable<String> cuis) {
    return forCuisLimited(Integer.MAX_VALUE, cuis);
  }

  public static UmlsCuiHierarchyMatcher forCuiLimited(int limit, String cui) {
    return forCuisLimited(limit, cui);
  }

  public static UmlsCuiHierarchyMatcher forCuisLimited(int limit, String... cuis) {
    return forCuisLimited(limit, Arrays.asList(cuis));
  }

  public static UmlsCuiHierarchyMatcher forCuisLimited(int limit, Iterable<String> cuis) {
    return new UmlsCuiHierarchyMatcher(limit, cuis);
  }


  public int getNumAtoms() {
    return patterns.size();
  }

  public int getNumCuis() {
    return labels.size();
  }

  public static UmlsCuiHierarchyMatcher fromTSV(Path tsv) throws IOException {
    final UmlsCuiHierarchyMatcher uchm = new UmlsCuiHierarchyMatcher();

    for (String line : Files.readAllLines(tsv)) {
      final int delim = line.lastIndexOf('\t');
      final String atom = line.substring(0, delim);
      final List<String> cuis = Lists.transform(Splitter.on(' ').splitToList(line.substring(delim + 1)), String::intern);
      uchm.patterns.put(formatContext(atom), cuis);
      uchm.labels.addAll(cuis);
    }

    log.info("Loaded {} patterns for {} CUIs from {}", uchm.patterns.size(), uchm.labels.size(), tsv);

    return uchm;
  }
}
