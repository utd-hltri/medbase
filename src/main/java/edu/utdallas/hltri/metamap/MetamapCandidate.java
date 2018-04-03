package edu.utdallas.hltri.metamap;

import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.List;

import edu.utdallas.hltri.Describable;

/**
 * Created by rmm120030 on 8/2/16.
 */
public class MetamapCandidate implements CharSequence, Serializable, Describable {
  private static final long serialVersionUID = 1L;
  private final String cui;
  private final String name;
  private final Integer score;
  private final Boolean negated;
  private final List<String> semanticTypes;

  private MetamapCandidate(Builder builder) {
    this.cui = builder.cui;
    this.name = builder.name;
    this.score = builder.score;
    this.negated = builder.negated;
    this.semanticTypes = builder.semanticTypes;
  }

  public String getCui() {
    return cui;
  }

  public String getName() {
    return name;
  }

  public Integer getScore() {
    return score;
  }

  public Boolean isNegated() {
    return negated;
  }

  public List<String> getSemanticTypes() {
    return semanticTypes;
  }

  @Override
  public String describe() {
    return getCui() + ": |" + getName() + "| - " + getScore() + " - Negated: " + (isNegated() ? "yes" : "no");
  }

  public static class Builder {
    private String cui;
    private String name;
    private Integer score;
    private Boolean negated = false;
    private List<String> semanticTypes = Lists.newArrayList();

    public Builder cui(String cui) {
      this.cui = cui;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder score(Integer score) {
      this.score = score;
      return this;
    }

    public Builder negated() {
      this.negated = true;
      return this;
    }

    public Builder addSemanticType(String semanticType) {
      this.semanticTypes.add(semanticType);
      return this;
    }

    public MetamapCandidate build() {
      return new MetamapCandidate(this);
    }
  }

  @Override
  public int length() {
    return name.length();
  }

  @Override
  public char charAt(int index) {
    return name.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return name.subSequence(start, end);
  }

  @Override
  public String toString() {
    return name;
  }
}
