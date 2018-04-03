package edu.utdallas.hltri.metamap;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.utdallas.hltri.scribe.text.Attribute;
import edu.utdallas.hltri.scribe.text.Document;
import edu.utdallas.hltri.scribe.text.annotation.AbstractAnnotation;
import edu.utdallas.hltri.scribe.text.annotation.AbstractAnnotationType;
import edu.utdallas.hltri.scribe.text.annotation.AnnotationType;
import gate.Annotation;

import java.util.List;
import java.util.Set;

/**
 * Created by rmm120030 on 8/2/16.
 */
public class MetamapConcept extends AbstractAnnotation<MetamapConcept> {
  private transient Set<String> types = null;

  protected MetamapConcept(Document<?> document, Annotation gateAnnotation) {
    super(document, gateAnnotation);
  }

  public static final Attribute<MetamapConcept, List<MetamapCandidate>> candidates = Attribute.inferred("candidates");

  public static final AnnotationType<MetamapConcept> TYPE = new AbstractAnnotationType<MetamapConcept>("Chunk") {
    @Override public MetamapConcept wrap(Document<?> parent, gate.Annotation ann) {
      return new MetamapConcept(parent, ann);
    }
  };

  public MetamapCandidate getBestCandidate() {
    final List<MetamapCandidate> candidates = get(MetamapConcept.candidates);
    if (candidates.size() > 0) {
      return candidates.stream().reduce((c1, c2) -> (c1.getScore() > c2.getScore()) ? c1 : c2).get();
    }
    else {
      throw new RuntimeException(String.format("MetamapConcept %s has no candidates.", getDocument().getId() + getId()));
    }
  }

  public void addCandidate(MetamapCandidate candidate) {
    List<MetamapCandidate> list = this.get(MetamapConcept.candidates);
    if (list == null) {
      list = Lists.newArrayList();
    }
    list.add(candidate);
    this.set(candidates, list);
  }

  public String getTypeString(Set<String> validTypes) {
    if (types == null) {
      initTypeSet();
    }
    return types.stream().filter(validTypes::contains).reduce("", (t1, t2) -> t1 + t2 + ",");
  }

  private void initTypeSet() {
    types = Sets.newHashSet();
    for (MetamapCandidate candidate : this.get(MetamapConcept.candidates)) {
      types.addAll(candidate.getSemanticTypes());
    }
  }
}
