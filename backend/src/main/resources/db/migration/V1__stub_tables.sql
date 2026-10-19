-- Stand-ins for tables that already exist in the real system.
-- Keep them minimal: only the columns this feature reads or writes.

-- Stand-in for the application table
CREATE TABLE case_applications (
  app_id      VARCHAR(40) PRIMARY KEY,
  status      VARCHAR(20) NOT NULL
);

-- Stand-in for the existing letter / document type reference data.
-- Letter type and document type are the same identifier.
CREATE TABLE letter_types (
  letter_type   VARCHAR(40)  PRIMARY KEY,
  display_name  VARCHAR(100) NOT NULL
);

-- Stand-in for "is this application eligible for letter type X".
-- Real rule lives elsewhere; isolated behind EligibilityRepository.
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

CREATE TABLE case_documents (
  doc_id         VARCHAR(40) PRIMARY KEY,
  app_id         VARCHAR(40) NOT NULL,
  document_type  VARCHAR(40) NOT NULL,
  created_at     TIMESTAMP   NOT NULL
);
CREATE INDEX ix_docs_app_type ON case_documents (app_id, document_type);
