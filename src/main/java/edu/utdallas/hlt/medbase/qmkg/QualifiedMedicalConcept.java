package edu.utdallas.hlt.medbase.qmkg;

import edu.utdallas.hltri.struct.Pair;

/**
 * Created by trg19 on 12/21/2015.
 */
@SuppressWarnings("WeakerAccess")
public class QualifiedMedicalConcept {
    public final String concept;
    public final String assertion;

    public QualifiedMedicalConcept(String concept, String assertion) {
        this.concept = concept;
        this.assertion = assertion.intern();
    }

    public Pair<String, String> toPair() {
        return Pair.of(concept, assertion);
    }

    public static QualifiedMedicalConcept fromPair(Pair<String, String> pair) {
        return new QualifiedMedicalConcept(pair.first(), pair.second());
    }
}
