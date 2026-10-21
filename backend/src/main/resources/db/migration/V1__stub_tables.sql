-- Stand-ins for tables that already exist in the real system.
-- Keep them minimal: only the columns this feature reads or writes.

-- Stand-ins for the existing admin screen's plan types and plans. In the real system these
-- come from the existing admin screen; the POC only needs an id and a name.
CREATE TABLE plan_type (
  plan_type_id  VARCHAR(40)  PRIMARY KEY,
  name          VARCHAR(100) NOT NULL
);

CREATE TABLE plan (
  plan_id       VARCHAR(40)  PRIMARY KEY,
  plan_type_id  VARCHAR(40)  NOT NULL REFERENCES plan_type(plan_type_id),
  name          VARCHAR(100) NOT NULL
);

-- Stand-in for the application table. Each application belongs to exactly one plan.
CREATE TABLE case_applications (
  app_id      VARCHAR(40) PRIMARY KEY,
  status      VARCHAR(20) NOT NULL,
  plan_id     VARCHAR(40) NOT NULL
);

-- Stand-in for the existing letter / document type reference data.
-- Letter type and document type are the same identifier.
CREATE TABLE letter_types (
  letter_type   VARCHAR(40)  PRIMARY KEY,
  display_name  VARCHAR(100) NOT NULL
);

-- Stand-in for "is this application eligible for letter type X".
-- Real rule lives elsewhere; isolated behind ApplicationLetterEligibilityRepository.
CREATE TABLE application_letter_eligibility (
  app_id       VARCHAR(40) NOT NULL,
  letter_type  VARCHAR(40) NOT NULL,
  PRIMARY KEY (app_id, letter_type)
);

-- Shared queue polled by the external letter-generator.
-- Mirrors the real table: NO primary key, and queue_key is NOT unique
-- (a regeneration adds a second row with the same key). We may only INSERT
-- into it and read generated_flag; no columns may be added.
CREATE TABLE letter_request_queue (
  queue_key       VARCHAR(100) NOT NULL,  -- appid_lettertype
  letter_type     VARCHAR(40)  NOT NULL,
  generated_flag  CHAR(1)      NOT NULL   -- 'N' or 'Y'
);

-- Stand-in for the existing documents table. Read only for case-service: nothing beyond
-- this app_id index (standing in for whatever exists in production) is added to it.
CREATE TABLE case_documents (
  doc_id         VARCHAR(40) PRIMARY KEY,
  app_id         VARCHAR(40) NOT NULL,
  document_type  VARCHAR(40) NOT NULL,
  created_at     TIMESTAMP   NOT NULL
);
CREATE INDEX ix_docs_app ON case_documents (app_id);
