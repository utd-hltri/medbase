package edu.utdallas.hlt.medbase.umls;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.util.AbstractExpander;
import edu.utdallas.hltri.util.Expansion;
import edu.utdallas.hltri.logging.Logger;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author travis
 */
public class UMLSManager extends AbstractExpander<CharSequence, String> implements Closeable {
  private static final Logger LOGGER = Logger.get(UMLSManager.class);

  private SetMultimap<String, String> PHRASE_TO_IDS       = null;
  private SetMultimap<String, String> ID_TO_PHRASES       = null;
  private SetMultimap<String, String> CACHE_PHRASE_TO_IDS = HashMultimap.create();
  private SetMultimap<String, String> CACHE_ID_TO_PHRASES = HashMultimap.create();
  private HashSet<String>             CACHE_PHRASES       = new HashSet<>();
  private HashSet<String>             CACHE_IDS           = new HashSet<>();

  private final String umlsPath, cachePath;

  private final static Config conf = Config.load("medbase.umls");

  public UMLSManager() {
    this(conf.getString("path"), conf.getString("cache-path"));
  }

  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final Lock read  = readWriteLock.readLock();
  private final Lock write = readWriteLock.writeLock();

  @SuppressWarnings({"unchecked", "WeakerAccess"})
  public UMLSManager(String umlsPath, String cachePath) {
    super("UMLS");
    this.umlsPath = umlsPath;
    this.cachePath = cachePath;
    try {
      LOGGER.debug("Loading UMLS cache from {}.", this.cachePath);
      try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(cachePath)))) {
        CACHE_PHRASE_TO_IDS = (SetMultimap<String, String>) in.readObject();
        CACHE_ID_TO_PHRASES = (SetMultimap<String, String>) in.readObject();
        CACHE_PHRASES = (HashSet<String>) in.readObject();
        CACHE_IDS = (HashSet<String>) in.readObject();
      }
    } catch (ClassNotFoundException | IOException ex) {
      LOGGER.warn("Failed to load cached data: {}");
    }
  }

  private synchronized void loadUMLSData() {
    write.lock();
    try {
      PHRASE_TO_IDS = HashMultimap.create();
      ID_TO_PHRASES = HashMultimap.create();
      LOGGER.info("Loading UMLS data from {}...", umlsPath);
      try (BufferedReader reader = new BufferedReader(new FileReader(umlsPath))) {
        //noinspection UnusedAssignment
        String line, phrase = "", id = "";
        Splitter splitter = Splitter.on('|');
        List<String> fields;
        while ((line = reader.readLine()) != null) {
          fields = splitter.splitToList(line);
          id = fields.get(0).intern();
          phrase = fields.get(14).toLowerCase();

          if ("ENG".equals(fields.get(1))) {
            PHRASE_TO_IDS.put(phrase, id);
            ID_TO_PHRASES.put(id, phrase);
          }
        }
        LOGGER.info("Loaded {} mappings for {} CUIs", PHRASE_TO_IDS.size(), ID_TO_PHRASES.size());
      } catch (IOException ex) {
        LOGGER.error("IOException", ex);
      }
    } finally {
      write.unlock();
    }
  }

  @Override public void close() {
    read.lock();
    try {
      LOGGER.info("Saving UMLS cache to {}.", cachePath);
      //noinspection ResultOfMethodCallIgnored
      new File(cachePath).getParentFile().mkdirs();
      try (ObjectOutputStream out = new ObjectOutputStream(
          new BufferedOutputStream(new FileOutputStream(cachePath)))) {
        out.writeObject(CACHE_PHRASE_TO_IDS);
        out.writeObject(CACHE_ID_TO_PHRASES);
        out.writeObject(CACHE_PHRASES);
        out.writeObject(CACHE_IDS);
      } catch (IOException ex) {
        LOGGER.error("Failed to save cache: ", ex);
      }
    } finally {
      read.unlock();
    }
  }

  @SuppressWarnings("WeakerAccess")
  public Set<String> getIds(String phrase) {
    read.lock();
    boolean empty;
    Set<String> ids;
    try {
      ids = CACHE_PHRASE_TO_IDS.get(phrase);
      if (CACHE_PHRASES.contains(phrase))
        return ids;
      empty = PHRASE_TO_IDS == null;
    } finally {
      read.unlock();
    }

    write.lock();
    try {
      LOGGER.debug("Phrase \"{}\" not found in UMLS cache.", phrase);
      if (empty) {
        loadUMLSData();
      }

      ids = PHRASE_TO_IDS.get(phrase);
      CACHE_PHRASE_TO_IDS.putAll(phrase, ids);
      CACHE_PHRASES.add(phrase);
      return ids;
    } finally {
      write.unlock();
    }
  }

  @SuppressWarnings("WeakerAccess")
  public Set<String> getPhrases(String id) {
    read.lock();
    boolean empty;
    Set<String> phrases;
    try {
      phrases = CACHE_ID_TO_PHRASES.get(id);
      if (CACHE_IDS.contains(id))
        return phrases;
      empty = ID_TO_PHRASES == null;
    } finally {
      read.unlock();
    }

    write.lock();
    try {
      if (empty) {
        loadUMLSData();
      }
      phrases = ID_TO_PHRASES.get(id);
      CACHE_ID_TO_PHRASES.putAll(id, phrases);
      CACHE_IDS.add(id);
      return phrases;
    } finally {
      write.unlock();
    }
  }

  @Override public Set<String> getExpansions(CharSequence cs) {
    String phrase = cs.toString();
    phrase = phrase.toLowerCase();
    Set<String> ids = getIds(phrase);
    Set<String> results = new HashSet<>();

    for (String id : ids) {
      results.addAll(getPhrases(id));
    }

    Expansion.reduceEntries(results);

    return results;
  }
}
