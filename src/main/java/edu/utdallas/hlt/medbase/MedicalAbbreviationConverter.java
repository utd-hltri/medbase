package edu.utdallas.hlt.medbase;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.scribe.Stopwords;
import edu.utdallas.hltri.util.AbstractExpander;

/**
 *
 * @author travis
 */
@SuppressWarnings("unused")
public class MedicalAbbreviationConverter extends AbstractExpander<CharSequence, String> implements  AutoCloseable {
  private static final Logger log = Logger.get(MedicalAbbreviationConverter.class);

  private static final Config conf = Config.load("medbase.medical-abbreviations");

  private final File                abbreviationsFile = new File(conf.getString("path"));
  private final Map<String, String> abbreviations     = new HashMap<>();
  private final Stopwords stopwords;

  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final Lock read  = readWriteLock.readLock();
  private final Lock write = readWriteLock.writeLock();

  @SuppressWarnings("WeakerAccess")
  public MedicalAbbreviationConverter(final Stopwords stopwords) {
    super("MedAbbrev");
    this.stopwords = stopwords;
  }

  public static MedicalAbbreviationConverter getDefault() {
      try {
    return new MedicalAbbreviationConverter((Stopwords) Class.forName(conf.getString("stopwords")).newInstance());
  } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
    throw new RuntimeException(ex);
  }
  }

  @SuppressWarnings("UnusedAssignment")
  private Map<String, String> getAbbreviations() {
    if (abbreviations.isEmpty()) {
      write.lock();
      try {
        log.info("Loading medical abbrevations from {}", abbreviationsFile.getName());
        Splitter splitter = Splitter.on('\t');
        try (BufferedReader reader = Files.newReader(abbreviationsFile, Charset.defaultCharset())) {
          Iterator<String> it;
          String abbrev, value;
          for (String line = reader.readLine(); (line = reader.readLine()) != null; ) {
            it = splitter.split(line).iterator();
              abbrev = it.next();
              value = it.next();
              if (!abbrev.equals(value) &&
                  !Strings.isNullOrEmpty(abbrev) && !stopwords.contains(abbrev) &&
                  !Strings.isNullOrEmpty(value)  && !stopwords.contains(value)) {
                abbreviations.put(abbrev, value);
              }
            }
          } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      } finally {
        write.unlock();
      }
    }

    return abbreviations;
  }

  @Override public Set<String> getExpansions(CharSequence cs) {
    assert cs != null;
    final Map<String, String> abbreviations = getAbbreviations();

    read.lock();
    try {
      String result = abbreviations.get(cs.toString().toLowerCase());
      if (Strings.isNullOrEmpty(result)) {
        return Collections.emptySet();
      } else {
        log.trace("Convering {} to {}.", cs, result);
        return Collections.singleton(result);
      }
    } finally {
      read.unlock();
    }
  }

  /**
   * Closes this resource, relinquishing any underlying resources.
   * This method is invoked automatically on objects managed by the
   * {@code try}-with-resources statement.
   * <p>
   * <p>While this interface method is declared to throw {@code
   * Exception}, implementers are <em>strongly</em> encouraged to
   * declare concrete implementations of the {@code close} method to
   * throw more specific exceptions, or to throw no exception at all
   * if the close operation cannot fail.
   * <p>
   * <p> Cases where the close operation may fail require careful
   * attention by implementers. It is strongly advised to relinquish
   * the underlying resources and to internally <em>mark</em> the
   * resource as closed, prior to throwing the exception. The {@code
   * close} method is unlikely to be invoked more than once and so
   * this ensures that the resources are released in a timely manner.
   * Furthermore it reduces problems that could arise when the resource
   * wraps, or is wrapped, by another resource.
   * <p>
   * <p><em>Implementers of this interface are also strongly advised
   * to not have the {@code close} method throw {@link
   * InterruptedException}.</em>
   * <p>
   * This exception interacts with a thread's interrupted status,
   * and runtime misbehavior is likely to occur if an {@code
   * InterruptedException} is {@linkplain Throwable#addSuppressed
   * suppressed}.
   * <p>
   * More generally, if it would cause problems for an
   * exception to be suppressed, the {@code AutoCloseable.close}
   * method should not throw it.
   * <p>
   * <p>Note that unlike the {@link java.io.Closeable#close close}
   * method of {@link java.io.Closeable}, this {@code close} method
   * is <em>not</em> required to be idempotent.  In other words,
   * calling this {@code close} method more than once may have some
   * visible side effect, unlike {@code Closeable.close} which is
   * required to have no effect if called more than once.
   * <p>
   * However, implementers of this interface are strongly encouraged
   * to make their {@code close} methods idempotent.
   *
   * @throws Exception if this resource cannot be closed
   */
  @Override public void close() throws Exception {
    abbreviations.clear();
  }
}
