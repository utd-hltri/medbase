package edu.utdallas.hltri.umls;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.utdallas.hltri.logging.Logger;

/**
 * Created by travis on 7/21/16.
 */
public class FilteredUmlsCuiMatcher extends UmlsCuiMatcher {
  private final static Logger log = Logger.get(FilteredUmlsCuiMatcher.class);

  private final Set<String> filteredCuis;

  private final Set<String> seen = new HashSet<>();

  protected FilteredUmlsCuiMatcher(int limit, Set<String> cui, Set<String> filteredCuis) {
    super(limit, cui);
    this.filteredCuis = filteredCuis;
    log.debug("Saved filtered CUIs {} as {}", filteredCuis, this.filteredCuis);
  }

  @Override
  protected void buildCuiTree(String cui, int limit) {
    if (limit > 0) {
      for (String child : umls.getNarrowerCuis(cui)) {
        if (seen.add(child)) {
          assert child != null;
          assert filteredCuis != null;
          if (!filteredCuis.contains(child)) {
            putAtoms(child);
            buildCuiTree(child, limit - 1);
          }
        }
      }
    }
  }

  public static UmlsCuiMatcher forCuiLimitedWithoutChildren(int limit, String cui, String... filteredCuis) {
    log.debug("Building Matcher for {} without {}", cui, Arrays.toString(filteredCuis));
    final Set<String> cuiSet = Collections.singleton(cui);
    log.debug("Created singleton CUI set {}", cuiSet);
    final Set<String> filteredCuiSet = Sets.newHashSet(filteredCuis);
    log.debug("Created filteredCuis CUI set {}", filteredCuiSet);
    final String name = getName(cuiSet, filteredCuiSet);
    log.debug("Constructed name {}", name);
    try {
      return fromTSVByName(name);
    } catch (IOException | NullPointerException e) {
      final FilteredUmlsCuiMatcher matcher = new FilteredUmlsCuiMatcher(limit, cuiSet, filteredCuiSet);
      matcher.initialize();
      matcher.toTSV();
      return matcher;
    }
  }

  public static UmlsCuiMatcher forCuiWithoutChildren(String cui, String... filteredCuis) {
    return forCuiLimitedWithoutChildren(Integer.MAX_VALUE, cui, filteredCuis);
  }

  protected static String getName(Set<String> presentCuis, Set<String> filteredCuis) {
    return UmlsCuiMatcher.getName(presentCuis) + "-" + filteredCuis.stream().collect(Collectors.joining("-"));
  }

  public static UmlsCuiMatcher fromTSVByName(String name) throws IOException {
    final List<String> cuis = Splitter.on('-').splitToList(name);
    final Set<String> filteredCuis = Sets.newHashSet(cuis.subList(1, cuis.size()));
    final FilteredUmlsCuiMatcher matcher = new FilteredUmlsCuiMatcher(Integer.MAX_VALUE, Collections.singleton(cuis.get(0)), filteredCuis);
    final Path tsv = cachePath.resolve(name + ".tsv");
    populateWithTSV(matcher, tsv);
    log.info("Loaded {} from {}", matcher, tsv);
    return matcher;
  }

  @Override
  public String getName() {
    return getName(getSeeds(), filteredCuis);
  }

}
