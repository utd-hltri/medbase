package edu.utdallas.hlt.medbase.qmkg;

import java.util.Objects;

/**
 * Created by trg19 on 12/21/2015.
 */
@SuppressWarnings("WeakerAccess")
public class TypedQualifiedMedicalConcept {
    public enum Type {
        PROBLEM,
        TREATMENT,
        TEST;
    }

    public final String concept;
    public final String assertion;
    public final Type type;

    public TypedQualifiedMedicalConcept(String concept, String assertion, Type type) {
        this.concept = concept;
        this.assertion = assertion.intern();
        this.type = type;
    }

    public QualifiedMedicalConcept toUntyped() {
        return new QualifiedMedicalConcept(concept, assertion);
    }

    public String toString() {
        return type.toString() + "/" + concept + "/" + assertion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypedQualifiedMedicalConcept that = (TypedQualifiedMedicalConcept) o;
        return Objects.equals(concept, that.concept) &&
                Objects.equals(assertion, that.assertion) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(concept, assertion, type);
    }
}
