package edu.utdallas.hlt.medbase.snomed;

import java.io.Serializable;

/**
 * Created by trg19 on 6/16/2017.
 */
public class SNOMEDRelation implements Serializable {
  long conceptId1;
  long conceptId2;
  long relationshipType;

  SNOMEDRelation(long a, long type, long b) {
    conceptId1 = a;
    conceptId2 = b;
    relationshipType = type;
  }
}
