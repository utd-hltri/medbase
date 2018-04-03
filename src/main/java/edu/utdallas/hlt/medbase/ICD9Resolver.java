package edu.utdallas.hlt.medbase;

import com.google.common.base.Splitter;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class ICD9Resolver {

  // Cache for factory methods
  private static WeakHashMap<File, ICD9Resolver> cache = new WeakHashMap<>();

  /*
   * Expects a file of the format <ICD-9> <tab> <Text> Where an ICD-9 matches
   * [E|V]?\d+(\.\d+)?
   */
  public static ICD9Resolver getResolver(String path) {
    return getResolver(new File(path));
  }

  public static ICD9Resolver getResolver(Path path) {
    return getResolver(path.toFile());
  }

  public static ICD9Resolver getResolver(File path) {
    if (!cache.containsKey(path)) {
      cache.put(path, new ICD9Resolver(path));
    }
    return cache.get(path);
  }
  private File path;
  private Map<String, String> icd9s = new HashMap<>();

  // Private constructor does nothing but save the path
  private ICD9Resolver(File path) {
    this.path = path;
  }

  // Lazy initializer
  private Pattern  pattern  = Pattern.compile("[E|V]?\\d+(?:\\.\\d+)?");
  private Splitter splitter = Splitter.on(',').limit(3);

  private void init() {
    System.err.printf("Lazily initializing ICD-9 codes from %s.%n", path);
    try {
      icd9s = new HashMap<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
        String line, name, value;
        Iterator<String> it;

        // Skip first line (header)
        while ((line = reader.readLine()) != null) {
          it = splitter.split(line).iterator();
          name = it.next();
          value = it.next() + '\n' + it.next();
          // Check for valid ICD-9 Code
          if (pattern.matcher(name).matches()) {
            icd9s.put(name, value);
          }
        }
      }
      System.err.printf("Loaded %,d ICD-9 mappings.%n", icd9s.size());
    } catch (IOException ex) {
      System.err.printf("Failed to initialize ICD-9 map from %s.", path);
      throw new RuntimeException(ex);
    }
  }

  protected String broaden(String ICD9) {
    if (icd9s.isEmpty()) {
      init();
    }

    ICD9 = ICD9.toUpperCase();
    ICD9 = ICD9.replaceAll("[^A-Z0-9.]", "");

    if (icd9s.get(ICD9) != null) {
      if (ICD9.contains(".")) {
        String first = broaden(ICD9.substring(0, ICD9.length() - 1));
        if (!first.isEmpty()) { first += " > "; }
        return first + icd9s.get(ICD9);
      } else {
        return icd9s.get(ICD9);
      }
    } else if (ICD9.contains(".")) {
      return broaden(ICD9.substring(0, ICD9.length() - 1));
    }
    return "";
  }

  public String decode(String ICD9) {
    String string = broaden(ICD9);
    if (string == null) {
      string = broaden("0" + ICD9);
      if (string == null) {
        string = broaden("00" + ICD9);
        if (string == null) {
          return "";
        } else {
          return string;
        }
      } else {
        return string;
      }
    } else {
      return string;
    }
  }

  public static void main(String... args) {
    ICD9Resolver r = ICD9Resolver.getResolver(args[0]);
    Console cons = System.console();
    String line;
    while ((line = cons.readLine("Decode: ")) != null && !line.equals("exit") && !line.equals("quit")) {
      cons.printf("\nResolved %s to \"%s\".\n", line, r.decode(line));
    }
  }
}