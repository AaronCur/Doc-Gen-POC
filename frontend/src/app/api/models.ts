// Shapes of the case-service API (see README, "API contracts").

export interface LetterType {
  code: string;
  name: string;
}

export interface EligibleCount {
  code: string;
  eligibleCount: number;
}

export type RunStatus = 'GENERATING' | 'COMPLETE' | 'NEEDS_ATTENTION';
export type RunVisibility = 'STAFF_ONLY' | 'STAFF_AND_EXTERNAL';

export interface LetterRun {
  runId: number;
  letterType: string;
  letterTypeName: string;
  /** ISO date, e.g. 2026-02-01 */
  claimSubmittedDate: string;
  status: RunStatus;
  visibility: RunVisibility;
  totalCount: number;
  generatedCount: number;
  requestedBy: string;
  /** ISO instant */
  requestedAt: string;
  releasedBy: string | null;
  releasedAt: string | null;
}

export interface RunPage {
  items: LetterRun[];
  total: number;
}

export interface GenerateRequest {
  letterType: string;
  claimSubmittedDate: string;
}

export interface CaseDocument {
  docId: string;
  appId: string;
  documentType: string;
  createdAt: string;
}

/** A run the generator is still working on (or has stalled on). */
export function isActive(run: LetterRun): boolean {
  return run.status === 'GENERATING' || run.status === 'NEEDS_ATTENTION';
}
