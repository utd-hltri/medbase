package edu.utdallas.hlt.medbase.pred;

import edu.utdallas.hltri.conf.Config;
import edu.utdallas.hltri.logging.Logger;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 * User: Ramon
 * Date: 8/26/14
 * Time: 11:05 AM
 */
public enum PredBank {
  PROPBANK {
    public void init() {
      if (!isInitialized()) {
        log.info("Propbank initializing...");
        final String inFile = conf.getString("pbpath");
        try {
          for (File file : new File(inFile).listFiles(filter)) {
            org.jdom2.Document doc = builder.build(file);
            propbank.put(file.getName().replace(".xml", ""), new Predicate(doc, true));
            isInitializedPB = true;
          }

          log.info("Propbank initialized");
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    public Predicate getPred(String id) {
      if (isInitialized()) {
        return propbank.get(id.substring(0, id.indexOf('.')));
      } else {
        init();
        return getPred(id);
      }
    }

    public boolean isInitialized() {
      return isInitializedPB;
    }
  },

  NOMBANK {
    public void init() {
      if (!isInitialized()) {
        log.info("Nombank initializing...");
        final String inFile = conf.getString("nbpath");
        try {
          for (File file : new File(inFile).listFiles(filter)) {
            org.jdom2.Document doc = builder.build(file);
            nombank.put(file.getName().replace(".xml", ""), new Predicate(doc, false));
            isInitializedNB = true;
          }

          log.info("Nombank initialized");
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    public Predicate getPred(String id) {
      if (isInitialized()) {
        return nombank.get(id.substring(0, id.indexOf('.')));
      } else {
        init();
        return getPred(id);
      }
    }

    public boolean isInitialized() {
      return isInitializedNB;
    }
  };

  private static final Logger log = Logger.get(PredBank.class);
  private static final SAXBuilder builder = new SAXBuilder();
  private static final FilenameFilter filter = (dir, name) -> name.endsWith(".xml");
  private static final Config conf = Config.load("medbase.predcorpus");

  private static Map<String, Predicate> propbank = new TreeMap<>();
  private static Map<String, Predicate> nombank = new TreeMap<>();
  private static boolean isInitializedPB = false;
  private static boolean isInitializedNB = false;

  public abstract void init();
  public abstract Predicate getPred(String id);
  public abstract boolean isInitialized();

  public static void initAll() {
    PROPBANK.init();
    NOMBANK.init();
  }

  /**
   *
   * @param id the id of the desired roleset
   * @return the desired RoleSet if it exists, null if otherwise
   */
  public RoleSet getRoleSet(String id) {
    if (isInitialized()) {
      Predicate pred = getPred(id);
      if (pred == null) return null;
      return pred.getRoleSet(id);
    } else {
      init();
      return getRoleSet(id);
    }
  }

  public static void main (String... args) {
    PredBank.initAll();
  }
}
