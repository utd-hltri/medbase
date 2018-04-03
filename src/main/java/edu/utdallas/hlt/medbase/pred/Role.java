package edu.utdallas.hlt.medbase.pred;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: Ramon
 * Date: 7/14/14
 * Time: 12:16 PM
 */
public class Role implements Serializable {
  private static final long serialVersionUID = 3L;
  private final String argNum;
  private final String argName;

  public Role (String num, String name) {
    argNum = num;
    argName = name;
  }

  public String getNum() {
    return argNum;
  }

  public String getName() {
    return argName;
  }

  @Override
  public String toString() {
    return getNum() + "=" + getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Role role = (Role) o;

    if (!argName.equals(role.argName)) return false;
    if (!argNum.equals(role.argNum)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = argNum.hashCode();
    result = 31 * result + argName.hashCode();
    return result;
  }
}

