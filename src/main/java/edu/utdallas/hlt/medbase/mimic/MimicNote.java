package edu.utdallas.hlt.medbase.mimic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntConsumer;

import edu.utdallas.hltri.scribe.text.BaseDocument;
import edu.utdallas.hltri.scribe.text.DocumentAttribute;

/**
 * Created by travis on 3/11/16.
 */
public class MimicNote extends BaseDocument {
  public static final DocumentAttribute<MimicNote, String> subjectId =
      DocumentAttribute.typed("subject_id", String.class);
  public static final DocumentAttribute<MimicNote, String> admissionId =
      DocumentAttribute.typed("admission_id", String.class);

  public static final DocumentAttribute<MimicNote, LocalDateTime> chartDate =
      DocumentAttribute.typed("chart_date", LocalDateTime.class);

  public static final DocumentAttribute<MimicNote, String> category =
      DocumentAttribute.typed("category", String.class);

  public static final DocumentAttribute<MimicNote, List<String>> admissionDiagnoses =
      DocumentAttribute.inferred("admit_diag");
  public static final DocumentAttribute<MimicNote, List<String>> admissionProcedures =
      DocumentAttribute.inferred("admit_proc");

  public static final DocumentAttribute<MimicNote, String> description = DocumentAttribute.typed("description", String.class);
}
