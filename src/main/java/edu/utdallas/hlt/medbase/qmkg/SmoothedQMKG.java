package edu.utdallas.hlt.medbase.qmkg;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.BitSet;

/**
 * Created by trg19 on 12/21/2015.
 */
public class SmoothedQMKG extends BasicQMKG {

    private final double a;
    private final int b;

    public SmoothedQMKG(double a, int b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public double getSketchProbability(Iterable<TypedQualifiedMedicalConcept> concepts) {
        final int N = (int) searcher.getNumberOfDocuments();

        final TIntIntMap m = new TIntIntHashMap();
        BitSet n;
        int L = 0;
        for (TypedQualifiedMedicalConcept tqmc : concepts) {
            n = searcher.getHits(searcher.newParsedQuery(tqmc.concept));
            for (int i = n.nextSetBit(0); i != -1; i = n.nextSetBit(i + 1)) {
                m.increment(i);
            }
            L ++;
        }
        int[] C = new int [ L + 1 ];
        m.forEachValue(i -> { C[i]++; return true; });
        double score = b;
        for (int i = 0; i < L; i++) {
            score += Math.pow(i - a, Math.pow(2, i)) * a * C[i + 1];
        }
        return score;
    }
}
