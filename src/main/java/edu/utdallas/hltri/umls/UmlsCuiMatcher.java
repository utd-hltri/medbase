package edu.utdallas.hltri.umls;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.util.CharMatchers;
import edu.utdallas.hltri.util.PatternMatcher;

/**
 * Created by travis on 7/20/16.
 */
public class UmlsCuiMatcher extends PatternMatcher {
  private final static Logger log = Logger.get(UmlsCuiMatcher.class);

  protected static Path cachePath = Config.load("medbase").getPath("umls.cui-matcher-path");


  public static UmlsService umls = new UmlsService();

  protected int numCuis = 0;
  protected final int limit;

  protected final Set<String> seedCuis;

  protected final Set<String> seen = new HashSet<>();

  protected void putAtoms(String cui) {
    numCuis++;
    log.debug("Getting atoms for {}", cui);
    for (String atom : umls.getAtomsForCui(cui)) {
      patterns.put(formatContext(atom), cui);
    }
  }

  protected UmlsCuiMatcher() {
    this.limit = Integer.MAX_VALUE;
    this.seedCuis = new HashSet<>();
  }

  protected UmlsCuiMatcher(int limit, Set<String> seedCuis) {
    this.limit = limit;
    this.seedCuis = seedCuis;
  }

  protected void initialize() {
    for (String cui : seedCuis) {
      putAtoms(cui);
      buildCuiTree(cui, limit - 1);
    }
  }

  protected void buildCuiTree(String cui, int limit) {
    if (limit > 0) {
      for (String child : umls.getNarrowerCuis(cui)) {
        if (seen.add(child)) {
          putAtoms(child);
          buildCuiTree(child, limit - 1);
        }
      }
    }
  }

  public static UmlsCuiMatcher forCui(String cui) {
    return forCuiLimited(Integer.MAX_VALUE, cui);
  }

  public static UmlsCuiMatcher forCuiLimited(int limit, String cui) {
    return forCuisLimited(limit, cui);
  }

  public static UmlsCuiMatcher forCuis(String... cuis) {
    return forCuisLimited(Integer.MAX_VALUE, cuis);
  }

  public static UmlsCuiMatcher forCuisLimited(int limit, String... cuis) {
    final Set<String> cuiSet = Sets.newHashSet(cuis);
    final String name = getName(cuiSet);
    try {
      return fromTSVByName(name);
    } catch (IOException e) {
      final UmlsCuiMatcher matcher = new UmlsCuiMatcher(limit, cuiSet);
      matcher.initialize();
      matcher.toTSV();
      return matcher;
    }
  }

  public int getNumAtoms() {
    return patterns.size();
  }

  public int getNumCuis() {
    return numCuis;
  }

  private static CharMatcher formatter = CharMatchers.PUNCTUATION.or(CharMatcher.WHITESPACE).precomputed();

  public boolean hasMatchWithin(CharSequence context) {
    return !Iterables.isEmpty(getAtomsWithin(context));
  }

  private static CharMatcher unformatter = CharMatcher.is('|').precomputed();

  public Iterable<String> getAtomsWithin(CharSequence context) {
    return Iterables.transform(patterns.getKeysContainedIn(formatContext(context)), s -> unformatter.trimAndCollapseFrom(s, ' '));
  }

  public Iterable<String> getCuisWithin(CharSequence context) {
    return patterns.getValuesForKeysContainedIn(formatContext(context));
  }

  protected static <T extends UmlsCuiMatcher> void populateWithTSV(T matcher, Path tsv) throws IOException {
    final Set<String> cuis = new HashSet<>();
    for (String line : Files.readAllLines(tsv)) {
      final int delim = line.lastIndexOf('\t');
      final String atom = line.substring(0, delim);
      final String cui = line.substring(delim + 1);
      System.out.printf("Restoring %s -> %s == %s\n", atom, formatContext(atom), cui);
      cuis.add(cui);
      matcher.patterns.put(formatContext(atom), cui);
    }
    for (String seedCui : Splitter.on(CharMatcher.anyOf("+-")).split(tsv.getFileName().toString().substring(0, tsv.getFileName().toString().length() - 4))) {
      matcher.seedCuis.add(seedCui);
    }
    matcher.numCuis = cuis.size();
  }

  public static UmlsCuiMatcher fromTSVByName(String name) throws IOException {
    final UmlsCuiMatcher matcher = new UmlsCuiMatcher();
    final Path tsv = cachePath.resolve(name + ".tsv");
    populateWithTSV(matcher, tsv);
    log.info("Loaded {} from {}", matcher, tsv);
    return matcher;
  }

  public final void toTSV() {
    final List<String> lines = StreamSupport.stream(patterns.getKeyValuePairsForKeysStartingWith("|").spliterator(), false)
                                            .map(p -> unformatter.trimAndCollapseFrom(p.getKey(), ' ') + '\t' + p.getValue())
                                            .collect(Collectors.toList());
    try {
      Files.write(cachePath.resolve(getName() + ".tsv"), lines);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  protected static String getName(Set<String> cuis) {
    return cuis.stream().collect(Collectors.joining("+"));
  }

  public String getName() {
    return getName(getSeeds());
  }

  public String toString() {
    return "UmlsCuiMatcher(" + getName() + ")";
  }

  public Set<String> getSeeds() {
    return seedCuis;
  }
}
