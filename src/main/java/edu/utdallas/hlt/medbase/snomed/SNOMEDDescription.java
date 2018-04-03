package edu.utdallas.hlt.medbase.snomed;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Created by trg19 on 6/16/2017.
 */
public class SNOMEDDescription {
  long descriptionID;
  int descriptionStatus;
  long conceptID;
  String term;
  int initialCapitalStatus;
  int descriptionType;
  String languageCode;

  public SNOMEDDescription(String line) {
    final List<String> fields = Lists.newArrayList(Splitter.on('\t').split(line));
    assert fields.size() == 7 : "Expected 7 fields: " + line;
    this.descriptionID = Long.parseLong(fields.get(0));
    this.descriptionStatus = Integer.parseInt(fields.get(1));
    this.conceptID = Long.parseLong(fields.get(2));
    this.term = fields.get(3);
    this.initialCapitalStatus = Integer.parseInt(fields.get(4));
    this.descriptionType = Integer.parseInt(fields.get(5));
    this.languageCode = fields.get(6);
  }
}
