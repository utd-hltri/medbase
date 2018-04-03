package edu.utdallas.hlt.medbase.snomed;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.util.Expansion;
/**
 *
 * @author travis
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class SNOMEDICD9Mapper {
  private static final Logger log = Logger.get(SNOMEDICD9Mapper.class);

  private final SNOMEDManager snomed;
  private final Path path1, path2;


  static final Config conf = Config.load("medbase.snomed.icd9");

  public static SNOMEDICD9Mapper getDefault(SNOMEDManager snomed) {
    return new SNOMEDICD9Mapper(snomed, conf.getString("path-targets"), conf.getString("path"));
  }


  public SNOMEDICD9Mapper(SNOMEDManager snomed, String targetPath, String mappingPath) {
    this.snomed = snomed;
    this.path2 = Paths.get(targetPath);
    this.path1 = Paths.get(mappingPath);
    init();
  }

  Map<Long, Long>           target = Maps.newHashMap();
  SetMultimap<Long, String> icd9s  = HashMultimap.create();

  @SuppressWarnings("UnusedAssignment")
  private void init() {
    final Splitter fsplitter = Splitter.on('\t');

    try (final BufferedReader reader = Files.newBufferedReader(path1, Charsets.ISO_8859_1)) {
      for (String line = reader.readLine(); (line = reader.readLine()) != null; ) {
        List<String> fields = ImmutableList.copyOf(fsplitter.split(line));
        target.put(Long.valueOf(fields.get(1)), Long.valueOf(fields.get(4)));
      }
      log.info("Generated {} target mappings", target.size());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }


    try (final BufferedReader reader = Files.newBufferedReader(path2, Charsets.ISO_8859_1)) {
      final Splitter isplitter = Splitter.on('|');
      for (String line = reader.readLine(); (line = reader.readLine()) != null;) {
        List<String> fields = ImmutableList.copyOf(fsplitter.split(line));
        icd9s.putAll(Long.valueOf(fields.get(0)), isplitter.split(fields.get(2)));
      }
      log.info("Generated {} icd9 mappings", icd9s.size());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public Collection<String> getICD9s(CharSequence term) {
    Collection<String> results = new HashSet<>();
    Collection<Long> concepts = snomed.getConceptIds(term.toString(), SNOMEDRelationshipType.IS_A, 3, SNOMEDRelationshipDirection.CHILDREN);
    for (Long id : concepts) {
      if (target.containsKey(id)) {
        long tar = target.get(id);
        results.addAll(icd9s.get(tar));
      }
    }
    Expansion.reduceEntries(results);
    return results;
  }
}
