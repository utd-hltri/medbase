package edu.utdallas.hlt.medbase.qmkg;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import edu.utdallas.hltri.inquire.lucene.CharMatcherAnalyzer;
import edu.utdallas.hltri.inquire.lucene.LuceneSearchEngine;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by trg19 on 12/21/2015.
 */
public class BasicQMKG {
    private static final Analyzer LUCENE_ANALYZER;

    static {
        final Analyzer keyword = new KeywordAnalyzer();
        final Analyzer comma = new CharMatcherAnalyzer(CharMatcher.is(','));
        LUCENE_ANALYZER = new PerFieldAnalyzerWrapper(new EnglishAnalyzer(),
                ImmutableMap.<String, Analyzer>builder()
                        .put("file_name", keyword)
                        .put("checksum", keyword)
                        .put("visit_id", keyword)
                        .put("year", keyword)
                        .put("discharge_diagnosis", comma)
                        .put("admit_diagnosis", comma)
                        .build());
    }

    // Exact inference = proportion with concepts against corpus
    LuceneSearchEngine<?> searcher = new LuceneSearchEngine<>(
            "/shared/aifiles/disk1/travis/data/indices/trec2011-concepts.reports.idx",
            LUCENE_ANALYZER,
            "full_text",
            (d, i) -> null);

    public double getSketchProbability(Iterable<TypedQualifiedMedicalConcept> concepts) {
        final Map<TypedQualifiedMedicalConcept.Type, List<QualifiedMedicalConcept>> tqmcs =
                StreamSupport.stream(concepts.spliterator(), false)
                        .collect(Collectors.groupingBy(t -> t.type, Collectors.mapping(TypedQualifiedMedicalConcept::toUntyped, Collectors.toList())));

        return getSketchProbability(
                tqmcs.getOrDefault(TypedQualifiedMedicalConcept.Type.PROBLEM, Collections.emptyList()),
                tqmcs.getOrDefault(TypedQualifiedMedicalConcept.Type.TREATMENT, Collections.emptyList()),
                tqmcs.getOrDefault(TypedQualifiedMedicalConcept.Type.TEST, Collections.emptyList()));
    }

    public double getSketchProbability(Iterable<QualifiedMedicalConcept> problems,
                                       Iterable<QualifiedMedicalConcept> treatments,
                                       Iterable<QualifiedMedicalConcept> tests) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        for (QualifiedMedicalConcept problem : problems) {
            query.add(searcher.newPhraseQuery("concept_problem", problem.concept), BooleanClause.Occur.SHOULD);
        }
        for (QualifiedMedicalConcept treatment : treatments) {
            query.add(searcher.newPhraseQuery("concept_treatment", treatment.concept), BooleanClause.Occur.SHOULD);
        }
        for (QualifiedMedicalConcept test : tests) {
            query.add(searcher.newPhraseQuery("concept_test", test.concept), BooleanClause.Occur.SHOULD);
        }

        return searcher.getHitCount(query.build()) / searcher.getNumberOfDocuments();
    }

}
