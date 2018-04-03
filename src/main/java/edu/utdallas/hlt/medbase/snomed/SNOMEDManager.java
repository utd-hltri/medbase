package edu.utdallas.hlt.medbase.snomed;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.util.Expander;
import edu.utdallas.hltri.util.Expansion;

@SuppressWarnings("unused")
public class SNOMEDManager implements Closeable {
  private static final Logger LOGGER = Logger.get(SNOMEDManager.class);

  private Config conf = Config.load("medbase.snomed");

  /** Efficient thread-safety */
  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final Lock read  = readWriteLock.readLock();
  private final Lock write = readWriteLock.writeLock();

  private final String CORE_CONCEPTS_PATH      = conf.getString("core.concepts");
  //  private final String CORE_DESCRIPTIONS_PATH  = conf.getString("core.descriptions");
  private final String CORE_RELATIONSHIPS_PATH = conf.getString("core.relationships");
  private final String DRUG_CONCEPTS_PATH      = conf.getString("drug.concepts");
  //  private final String DRUG_DESCRIPTIONS_PATH  = conf.getString("drug.descriptions");
  private final String DRUG_RELATIONSHIPS_PATH = conf.getString("drug.relationships");

  private final String CACHE_PATH = conf.getString("cache-path");

  /* Internal maps */
  private transient SetMultimap<String, Long>            name2id     = HashMultimap.create();
  private transient TLongObjectMap<String>               id2name     = new TLongObjectHashMap<>();
  private transient TLongObjectMap<List<SNOMEDRelation>> id2relation = new TLongObjectHashMap<>();

  /* Caches */
  private SetMultimap<String, Long>            _name2id                     = HashMultimap.create();
  private TLongObjectMap<String>               _id2name                     = new TLongObjectHashMap<>();
  private TLongObjectMap<List<SNOMEDRelation>> _id2relation                 = new TLongObjectHashMap<>();

  private final Splitter splitter = Splitter.on('\t');

  private volatile boolean isInitialized = false;

  @SuppressWarnings("unchecked")
  public SNOMEDManager() {
    try (final ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(CACHE_PATH))))) {
      LOGGER.info("Restoring cached SNOMED data from {}", CACHE_PATH);
      _name2id = (SetMultimap<String, Long>) in.readObject();
      _id2name = (TLongObjectHashMap<String>) in.readObject();
      _id2relation = (TLongObjectHashMap<List<SNOMEDRelation>>) in.readObject();
    } catch (ClassCastException | ClassNotFoundException | IOException ex) {
      LOGGER.warn("Failed to load cached SNOMED data: ", ex.getMessage());
    }
  }

  private void initialize() {
    read.lock();
    try {
      if (isInitialized)
        return;
    } finally {
      read.unlock();
    }

    write.lock();
    try {
      if (isInitialized || !name2id.isEmpty())
        return;
      LOGGER.info("Initializing SNOMED core concepts.");
      parseConcepts(CORE_CONCEPTS_PATH, name2id, id2name);
      LOGGER.info("Initializing SNOMED drug concepts.");
      parseConcepts(DRUG_CONCEPTS_PATH, name2id, id2name);
      LOGGER.info("Initializing SNOMED core relations.");
      parseRelations(CORE_RELATIONSHIPS_PATH, id2relation);
      LOGGER.info("Initializing SNOMED drug relations.");
      parseRelations(DRUG_RELATIONSHIPS_PATH, id2relation);
      isInitialized = true;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    } finally {
      write.unlock();
    }
  }

  @Override public void close() {
    read.lock();
    try {
      final Path path = Paths.get(CACHE_PATH);
      //noinspection ResultOfMethodCallIgnored
      path.toFile().getParentFile().mkdirs();
      try (final ObjectOutputStream out = new ObjectOutputStream(
          new BufferedOutputStream(Files.newOutputStream(path)))) {
        out.writeObject(_name2id);
        out.writeObject(_id2name);
        out.writeObject(_id2relation);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    } finally {
      read.unlock();
    }
  }

  private void addRelation(TLongObjectMap<List<SNOMEDRelation>> idToRelations, long id, SNOMEDRelation relation) {
    write.lock();
    try {
      List<SNOMEDRelation> list = idToRelations.get(id);
      if (list == null) {
        list = new ArrayList<>();
      }
      list.add(relation);
      idToRelations.put(id, list);
    } finally {
      write.unlock();
    }
  }

  private void parseConcepts(String path, SetMultimap<String, Long> nameToId, TLongObjectMap<String> idToName) throws IOException {
    write.lock();
    try {
      try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
        // Skip the first line
        int lineNo = 0;
        String[] columns;
        for (String line = reader.readLine(); (line = reader.readLine()) != null; ) {

          columns = Iterables.toArray(splitter.split(line), String.class);

          // Assure we have the correct number of fields
          if (columns.length == 6) {
            try {
              long conceptId = Long.parseLong(columns[0]);
//          String conceptStatus = columns[1];
              String fullySpecifiedName = columns[2];
//          String ctv3Id = columns[3];
//          String snomedId = columns[4];
//          String isPrimitive = columns[5];

              // Parse the biopath and store it in both maps
              String parsedName = getParsedName(fullySpecifiedName);

              nameToId.put(parsedName, conceptId);
              idToName.put(conceptId, parsedName);
            } catch (NumberFormatException e) {
              long max = Long.MAX_VALUE;
              long number = Long.parseLong(columns[0]);
              LOGGER
                  .warn("Found illegal int value {} (max: {}) on line {}.", number, max, lineNo, e);
            } finally {
              lineNo++;
            }
          } else {
            // Something went wrong.
            LOGGER.warn("Found {} columns. Excepted 6.", columns.length);
          }
        }
      }
    } finally {
      write.unlock();
    }
    LOGGER.debug("Parsed {} concepts", idToName.size());
  }

  private void parseRelations(String path, TLongObjectMap<List<SNOMEDRelation>> idToRelations) throws IOException {
    write.lock();
    try {
      try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
        // Skip the first line
        int lineNo = 0;
        String[] columns;
        for (String line = reader.readLine(); (line = reader.readLine()) != null; ) {
          columns = Iterables.toArray(splitter.split(line), String.class);

          // Assure we have the correct number of fields
          if (columns.length == 7) {
            try {
//          long relationshipId = Long.parseLong(columns[0]);
              long conceptId1 = Long.parseLong(columns[1]);
              long relationshipType = Long.parseLong(columns[2]);
              long conceptId2 = Long.parseLong(columns[3]);
//          String characteristicType = columns[4];
//          String refinability = columns[5];
//          String relationshipGroup = columns[6];

              // Generate SNOMEDRelation object
              SNOMEDRelation relation = new SNOMEDRelation(
                  conceptId1,
                  relationshipType,
                  conceptId2);

              //  Store the relation under both concepts
              addRelation(idToRelations, conceptId1, relation);
              addRelation(idToRelations, conceptId2, relation);
            } catch (NumberFormatException e) {
              long max = Long.MAX_VALUE;
              long number = Long.parseLong(columns[0]);
              LOGGER.warn("Found illegal int value {} (max: {}) on line {}.", number, max, lineNo, e);
            } finally {
              lineNo++;
            }
          } else {
            // Something went wrong.
            LOGGER.warn("Found {} columns. Expected 7. Line: |{}|", columns.length, line);
          }
        }
      }
    } finally {
      write.unlock();
    }
    LOGGER.debug("Parsed {} relations", idToRelations.size());
  }

  @SuppressWarnings("unused")
  public void writeTriplesTransXStyle(Path path) throws IOException {
    final List<SNOMEDRelation> relations = new ArrayList<>();
    readRelations(CORE_RELATIONSHIPS_PATH, relations);
    readRelations(DRUG_RELATIONSHIPS_PATH, relations);
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.resolve("triple2id.tsv").toFile()))) {
      writer.write(relations.size() + "");
      writer.newLine();
      for (SNOMEDRelation relation : relations) {
        writer.write(relation.conceptId1 + "\t" + relation.conceptId2 + "\t" + relation.relationshipType);
        writer.newLine();
      }
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.resolve("relation2id.tsv").toFile()))) {
      final SNOMEDRelationshipType[] types = SNOMEDRelationshipType.values();
      writer.write(types.length + "");
      writer.newLine();
      for (SNOMEDRelationshipType type : types) {
        writer.write(type.getValue() + "\t" + type.name());
        writer.newLine();
      }
    }

    parseConcepts(CORE_CONCEPTS_PATH, name2id, id2name);
    parseConcepts(DRUG_CONCEPTS_PATH, name2id, id2name);
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.resolve("entity2id.tsv").toFile()))) {
      writer.write(id2name.size() + "");
      writer.newLine();
      for (long id : id2name.keys()) {
        writer.write(id + "\t" + id2name.get(id));
        writer.newLine();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void writeTriplesSimple(Path path, Function<String, Optional<String>> normalizer) throws IOException {
    parseConcepts(CORE_CONCEPTS_PATH, name2id, id2name);
    parseConcepts(DRUG_CONCEPTS_PATH, name2id, id2name);

    final List<SNOMEDRelation> relations = new ArrayList<>();
    readRelations(CORE_RELATIONSHIPS_PATH, relations);
    readRelations(DRUG_RELATIONSHIPS_PATH, relations);
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
      for (SNOMEDRelation relation : relations) {
        final Optional<String> op1 = normalizer.apply(id2name.get(relation.conceptId1));
        final Optional<String> op2 = normalizer.apply(id2name.get(relation.conceptId2));
        if (op1.isPresent() && op2.isPresent() && !op1.get().equals(op2.get())) {
          writer.write(op1.get() + "\t" + SNOMEDRelationshipType.forValue(relation.relationshipType).name() + "\t" + op2.get());
          writer.newLine();
        }
      }
    }
  }

  private void readRelations(String path, List<SNOMEDRelation> list) {
    write.lock();
    try {
      try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
        // Skip the first line
        int lineNo = 0;
        String[] columns;
        for (String line = reader.readLine(); (line = reader.readLine()) != null; ) {
          columns = Iterables.toArray(splitter.split(line), String.class);

          // Assure we have the correct number of fields
          if (columns.length == 7) {
            try {
//          long relationshipId = Long.parseLong(columns[0]);
              long conceptId1 = Long.parseLong(columns[1]);
              long relationshipType = Long.parseLong(columns[2]);
              long conceptId2 = Long.parseLong(columns[3]);
//          String characteristicType = columns[4];
//          String refinability = columns[5];
//          String relationshipGroup = columns[6];

              // Generate SNOMEDRelation object
              SNOMEDRelation relation = new SNOMEDRelation(
                  conceptId1,
                  relationshipType,
                  conceptId2);

              //  Store the relation under both concepts
              list.add(relation);
            } catch (NumberFormatException e) {
              long max = Long.MAX_VALUE;
              long number = Long.parseLong(columns[0]);
              LOGGER.warn("Found illegal int value {} (max: {}) on line {}.", number, max, lineNo, e);
            } finally {
              lineNo++;
            }
          } else {
            // Something went wrong.
            LOGGER.warn("Found {} columns. Expected 7. Line: |{}|", columns.length, line);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } finally {
      write.unlock();
    }
  }

  private Set<Long> getConceptId(String name) {
    read.lock();
    boolean empty = false;
    try {
      if (_name2id.containsKey(name)) {
        return _name2id.get(name);
      }
      empty = name2id == null || name2id.isEmpty();
    } finally {
      read.unlock();
    }

    Set<Long> ids = new HashSet<>();
    write.lock();
    try {
      if (empty) {
        initialize();
      }
      ids.addAll(name2id.get(name));
      _name2id.putAll(name, ids);
    } finally {
      write.unlock();
    }

    return ids;
  }

  private String getName(long id) {
    read.lock();
    boolean empty = false;
    try {
      if (_id2name.containsKey(id)) {
        return _id2name.get(id);
      }
      empty = id2name == null || id2name.isEmpty();
    } finally {
      read.unlock();
    }

    String name = "";
    write.lock();
    try {
      if (empty) {
        initialize();
      }
      name = Strings.nullToEmpty(id2name.get(id));
      _id2name.put(id, name);
    } finally {
      write.unlock();
    }
    return name;
  }

  private List<SNOMEDRelation> getRelations(long id) {
    read.lock();
    boolean empty = false;
    try {
      if (_id2relation.containsKey(id)) {
        return _id2relation.get(id);
      }
      empty = id2relation == null || id2relation.isEmpty();
    } finally {
      read.unlock();
    }

    List<SNOMEDRelation> list = new ArrayList<>();
    write.lock();
    try {
      if (empty) {
        initialize();
      }
      list.addAll(id2relation.get(id));
      _id2relation.put(id, list);
    } finally {
      write.unlock();
    }

    return list;
  }

  private static class SNOMEDPath {
    String name;
    long id;

    SNOMEDPath(String name, long id) {
      this.name = name;
      this.id = id;
    }
  }

  private Collection<SNOMEDPath> getRelatedConcepts(long conceptID, SNOMEDRelationshipType relationshipType, int levels, SNOMEDRelationshipDirection direction) {
    List<SNOMEDPath> matching = new ArrayList<>();
    List<SNOMEDRelation> all = getRelations(conceptID);

    if (all != null) {
      for (SNOMEDRelation relation : all) {
        long source = 0L;
        long target = 0L;
        switch (direction) {
          case CHILDREN:
            source = relation.conceptId2;
            target = relation.conceptId1;
            break;
          case PARENTS:
            source = relation.conceptId1;
            target = relation.conceptId2;
            break;
          case BOTH:
            matching.addAll(getRelatedConcepts(conceptID, relationshipType, levels, SNOMEDRelationshipDirection.CHILDREN));
            matching.addAll(getRelatedConcepts(conceptID, relationshipType, levels, SNOMEDRelationshipDirection.PARENTS));
            return matching;
        }

        if (relation.relationshipType == relationshipType.getValue() && source == conceptID) {
          String name = getName(target);
          if (name != null) {
            matching.add(new SNOMEDPath(name, target));
            if (levels > 1) {
              matching.addAll(getRelatedConcepts(target, relationshipType, levels - 1, direction));
            }
          }
        }
      }
    }

    return matching;
  }
  public Set<String> getRelatedConcepts(String name, SNOMEDRelationshipType relationshipType, int levels, SNOMEDRelationshipDirection direction) {
    // Return nothing if we are asked for nonsense
    if (levels < 0) {
      return new HashSet<>();
    }

    Set<Long> ids = getConceptId(getParsedName(name));
    Set<String> results = new HashSet<>();

    if (ids != null) {
      for (long id : ids) {
        for (SNOMEDPath path : getRelatedConcepts(id, relationshipType, levels, direction)) {
          results.add(path.name);
        }
      }
    }

    return results;
  }

  public Set<Long> getConceptIds(String name, SNOMEDRelationshipType relationshipType, int levels, SNOMEDRelationshipDirection direction) {
    Set<Long> ids = getConceptId(getParsedName(name));
    Set<Long> results = new HashSet<>(ids);
    for (long id : ids) {
      for (SNOMEDPath path : getRelatedConcepts(id, relationshipType, levels, direction)) {
        results.add(path.id);
      }
    }
    return ids;
  }


  public Set<String> getFilteredConcepts(CharSequence cs, SNOMEDRelationshipType relationshipType, int levels, SNOMEDRelationshipDirection direction) {
    final String name = cs.toString();
    Set<String> concepts = getRelatedConcepts(name, relationshipType, levels, direction);
    String parsedName = getParsedName(name);
    concepts.add(parsedName);
    filterExpandedConcepts(concepts);
    Expansion.reduceEntries(concepts);
    concepts.remove(parsedName);
    return concepts;
  }

  public static Set<String> filterExpandedConcepts(Set<String> list) {
    Set<String> concepts = new HashSet<>(list);
    Set<String> results = new HashSet<>();

    for (Iterator<String> it = concepts.iterator(); it.hasNext();) {
      String concept = it.next();

      boolean isPrefix = true;
      for (String other : concepts) {
        // Compare against all other elements
        if (!concept.equals(other) && concept.startsWith(other)) {
          isPrefix = false;
          break;
        }
      }

      if (isPrefix) {
        results.add(concept);
      } else {
        it.remove();
      }
    }

    return results;
  }

  public static String getParsedName(String name) {
    // Strip off ending " (description)" text
    int delim = name.lastIndexOf('(');
    String result = name;
    try {
      if (delim > 0) {
        result = name.substring(0, delim - 1);
      }
    } catch (StringIndexOutOfBoundsException e) {
      LOGGER.error("StringIndexOutOfBoundsException", e);
    }
    return result.toLowerCase();
  }

  public Expander<CharSequence, String> expandBy(final SNOMEDRelationshipType relationshipType,
                                                 final int levels,
                                                 final SNOMEDRelationshipDirection direction) {
    return Expander.fromFunction("SNOMED:" + relationshipType.name(),
        item -> getFilteredConcepts(item.toString(), relationshipType, levels, direction));
  }
}
