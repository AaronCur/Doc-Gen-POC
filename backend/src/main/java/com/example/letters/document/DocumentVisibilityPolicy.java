package com.example.letters.document;

/**
 * SECURITY-CRITICAL. The single definition of whether an external user may see a document. It is
 * a JPQL condition, applied inside the external queries in {@link CaseDocumentRepository}, so
 * paging and counts over external documents stay correct (a Java filter after the query would
 * break them). Every external read must use one of those queries.
 *
 * <p>Rule, for the document {@code d}'s (app ID, document type):
 * <ol>
 *   <li>No run item for that pair: visible. This is every letter that existed before admin
 *       generation, and keeps today's behaviour unchanged.
 *   <li>Otherwise the latest run item decides (highest run_id): visible only if that run is
 *       STAFF_AND_EXTERNAL.
 * </ol>
 *
 * <p>The latest run item decides because the eligibility rule only puts an application in a run
 * when it has no document of that type. So a document that exists now was produced for the most
 * recent run that included its application. That is what makes regeneration work: delete the
 * letter, the next run picks the application up, and the new letter follows the new run.
 */
public final class DocumentVisibilityPolicy {

    /** Use with the document aliased as {@code d}. */
    public static final String VISIBLE_TO_EXTERNAL = """
            (
              not exists (select 1 from LetterRunItem i
                          where i.appId = d.appId and i.letterType = d.documentType)
              or exists (select 1 from LetterRunItem i join i.run r
                         where i.appId = d.appId and i.letterType = d.documentType
                         and r.visibility = com.example.letters.run.RunVisibility.STAFF_AND_EXTERNAL
                         and i.runId = (select max(latest.runId) from LetterRunItem latest
                                        where latest.appId = d.appId and latest.letterType = d.documentType))
            )
            """;

    private DocumentVisibilityPolicy() {
    }
}
