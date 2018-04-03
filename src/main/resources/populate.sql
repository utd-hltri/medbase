CREATE TABLE concept_types(
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  id TEXT NOT NULL
);

CREATE TABLE concept_synsets (
  name TEXT REFERENCES concept_types,
  expansion TEXT NOT NULL,
  relation TEXT NOT NULL,
  PRIMARY KEY (name, expansion, relation)
);

