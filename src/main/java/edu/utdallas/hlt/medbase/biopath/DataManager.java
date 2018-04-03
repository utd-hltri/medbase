package edu.utdallas.hlt.medbase.biopath;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import edu.utdallas.hltri.logging.Logger;
import edu.utdallas.hltri.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DataManager implements AutoCloseable {
  private static final Logger log = Logger.get(DataManager.class);

  final Connection conn;
  volatile boolean initialized = false;

  // Private constructor because we don't want anyone to call it but us
  private DataManager() {
    try {
      Class.forName("org.sqlite.JDBC");
      conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    } catch (ClassNotFoundException | SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // Nested class loads on the first execution of .getInstance()
  private static class Holder {
    public static final DataManager INSTANCE = new DataManager();
  }

  // Get our singleton
  public static DataManager getInstance() {
    return Holder.INSTANCE;
  }

  private void loadBatch(String table, InputStream in) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in));
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO " + table + " VALUES (?,?,?);"))
    {
      Splitter splitter = Splitter.on(',');
      List<String> list;
      String line;
      while ((line = br.readLine()) != null) {
        list = Lists.newLinkedList(splitter.split(line));
        if (list.size() != 3) {
          log.warn("Unusual data on line {}", list);
          if (list.get(0).charAt(0) == '"') {
            list.set(0, list.get(0).substring(1) + list.get(1).substring(0, list.get(1).length() - 2));
            list.remove(1);
          }
          if (list.get(1).charAt(0) == '"') {
            list.set(1, list.get(1).substring(1) + list.get(2).substring(0, list.get(2).length() - 2));
            list.remove(2);
          }
        }

        ps.setString(1, list.get(0).trim());
        ps.setString(2, list.get(1).trim().toLowerCase());
        ps.setString(3, list.get(2).trim().toUpperCase());
        ps.addBatch();
      }
      ps.executeBatch();
    } catch (IOException | SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void init() {
    if (!initialized) {
      synchronized(this) {
        // Create tables
        try (Statement stmt = conn.createStatement()) {
          log.debug("Creating tables...");
          stmt.execute("CREATE TABLE concept_types(name TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, id TEXT NOT NULL);");
          stmt.execute("CREATE TABLE concept_synsets(name TEXT REFERENCES concept_types, expansion TEXT NOT NULL, relation TEXT NOT NULL, PRIMARY KEY (name, expansion, relation));");
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

        // Load data
        log.debug("Loading data...");
        loadBatch("concept_types", getClass().getResourceAsStream("/biopath/types.csv"));
        loadBatch("concept_synsets", getClass().getResourceAsStream("/biopath/synsets.csv"));

        try (Statement stmt = conn.createStatement()) {
          log.debug("Creating indices...");
          stmt.execute("CREATE INDEX type_names ON concept_types(name);");
          stmt.execute("CREATE INDEX synset_names ON concept_synsets(name);");
          stmt.execute("CREATE INDEX type_types ON concept_types(type);");
          stmt.execute("CREATE INDEX synset_relations ON concept_synsets(relation);");
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

        initialized = true;
      }
    }
  }

  public Set<String> getTypeSynsets(String type) {
    init();
    final Set<String> results = new TreeSet<>();
    try (PreparedStatement stmt = conn.prepareStatement("SELECT expansion FROM (SELECT name FROM concept_types WHERE type = ?) NATURAL JOIN concept_synsets;")) {
      stmt.setString(1, type.toLowerCase().trim());
      try (ResultSet rs = stmt.executeQuery()) {
        while(rs.next()) {
          results.add(rs.getString("expansion"));
//          log.trace("Added expansion {}", rs.getString("expansion"));
        }
        log.info("Found {} expansions for type {}", results.size(), type.toLowerCase().trim());
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return results;
  }

  @Override public void close() {
    try {
      conn.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String... args) {
    String[] types = {"protein", "macromolecular complex", "biological process", "molecular function", "cellular component"};
    try (DataManager biopath = DataManager.getInstance()) {
      for (String type : types) {
        log.info("Collecting synsets for type {}...", type);
        Set<String> synset = biopath.getTypeSynsets(type);
        System.out.printf("Synset for type %s:%n", type);
        for (String syn: synset) {
          System.out.printf("  • %s%n", syn);
        }
        log.info("Found {} synonyms. ", synset.size());
        System.out.println();
      }
    }
  }
}
