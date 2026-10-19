package com.example.letters.document;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.letters.run.LetterRunItemRepository;
import com.example.letters.run.RunVisibility;

/**
 * SECURITY-CRITICAL. The single place that decides whether an external user may see a document.
 * Every external read of case-documents must go through {@link #visibleToExternal(List)}.
 *
 * <p>Rule, for the document's (app ID, document type):
 * <ol>
 *   <li>Find the most recent letter_run_item for that pair.
 *   <li>No run item: visible. This is every letter that existed before admin generation, and
 *       keeps today's behaviour unchanged.
 *   <li>The item's run is STAFF_AND_EXTERNAL: visible.
 *   <li>Otherwise: hidden.
 * </ol>
 *
 * <p>The latest run item decides because the eligibility rule only puts an application in a run
 * when it has no document of that type. So a document that exists now was produced for the most
 * recent run that included its application. That is what makes regeneration work: delete the
 * letter, the next run picks the application up, and the new letter follows the new run.
 */
@Component
public class DocumentVisibilityPolicy {

    private final LetterRunItemRepository letterRunItemRepository;

    public DocumentVisibilityPolicy(LetterRunItemRepository letterRunItemRepository) {
        this.letterRunItemRepository = letterRunItemRepository;
    }

    public boolean isVisibleToExternal(CaseDocument document) {
        return letterRunItemRepository
                .findFirstByAppIdAndLetterTypeOrderByCreatedAtDescRunIdDesc(document.getAppId(),
                        document.getDocumentType())
                .map(item -> item.getRun().getVisibility() == RunVisibility.STAFF_AND_EXTERNAL)
                .orElse(true);
    }

    /** Keeps only the documents an external user may see. */
    public List<CaseDocument> visibleToExternal(List<CaseDocument> documents) {
        return documents.stream().filter(this::isVisibleToExternal).toList();
    }
}
