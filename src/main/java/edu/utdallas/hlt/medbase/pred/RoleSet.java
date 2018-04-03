package edu.utdallas.hlt.medbase.pred;

import org.jdom2.Element;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Ramon
 * Date: 7/11/14
 * Time: 5:10 PM
 */
public class RoleSet implements Serializable {
  private static final long serialVersionUID = 3L;
  private final String lemma;
  private final String id;
  private final List<Role> roles;

  public static final RoleSet NULL_ROLESET = new RoleSet();

  public RoleSet(Element e, String l) {
    id = e.getAttributeValue("id");
    lemma = l;

    roles = new ArrayList<>();
    for (Element r : e.getChildren()) {
      if (r.getName().equals("roles")) {
        for (Element role : r.getChildren()) {
          if (role.getName().equals("role")) {
            roles.add(new Role(role.getAttributeValue("n"), role.getAttributeValue("descr")));
          }
        }
      }
    }
  }

  private RoleSet() {
    lemma = "null";
    id = "null";
    roles = new ArrayList<>();
  }

  /**
   * @param number the desired arg number of the form A0, A1, AM-LOC, etc.
   * @return the Role object representing the desired role if it exists, null otherwise
   */
  public Role getRole(String number) {
    for (Role role : roles) {
      if (role.getNum().equals(number))
        return role;
    }
    return null;
  }

  public String getLemma() {
    return lemma;
  }

  public String getId() {

    return id;
  }

  @Override
  public String toString() {
//    StringBuilder builder = new StringBuilder();
//    builder.append(getId()).append("(");
//    for (Role role : roles) {
//      builder.append(role).append(", ");
//    }
//    builder.delete(builder.length() - 2, builder.length());
//    builder.append(")");
//    return builder.toString();
    return getId();
  }
}
