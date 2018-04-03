package edu.utdallas.hlt.medbase.pred;

import edu.utdallas.hltri.logging.Logger;
import org.jdom2.Element;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Ramon
 * Date: 7/11/14
 * Time: 3:57 PM
 */
public class Predicate implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger log = Logger.get(Predicate.class);

  private transient final org.jdom2.Document jdoc;
  private final List<RoleSet> rolesets = new ArrayList<>();
  private transient final Element root;
  private final boolean fromPropbank;

  public Predicate(org.jdom2.Document doc, boolean source) {
    fromPropbank = source;
    jdoc = doc;
    root = doc.getRootElement();
    for (Element predicate : root.getChildren()) {
      if (predicate.getName().equals("predicate")) {
        for (Element rs : predicate.getChildren()) {
          if (rs.getName().equals("roleset")) {
            rolesets.add(new RoleSet(rs, predicate.getAttributeValue("lemma")));
          }
        }
      }
    }
  }

  /**
   *
   * @param id the id of the desired roleset; e.g. "place.01"
   * @return the desired RoleSet if it exists, null if otherwise
   */
  public RoleSet getRoleSet (String id) {
    for (RoleSet rs : rolesets) {
      if (id.equals(rs.getId())) {
        return rs;
      }
    }
//        log.debug("No roleset found for {}",id);
    return null;
  }

  public boolean isFromPropbank() {
    return fromPropbank;
  }
}
