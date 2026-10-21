// Shapes of the case-service API (see README, "API contracts").

export interface LetterType {
  code: string;
  name: string;
}

/** STAND-IN: in the real system plan types and plans come from the existing admin screen. */
export interface PlanType {
  id: string;
  name: string;
}

export interface Plan {
  id: string;
  planTypeId: string;
  name: string;
}

export interface EligibleCount {
  code: string;
  planId: string;
  eligibleCount: number;
}

/** Calculated by case-service on every read; never stored. */
export type RunStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETE' | 'NEEDS_ATTENTION';
export type RunVisibility = 'STAFF_ONLY' | 'STAFF_AND_EXTERNAL';

export interface LetterRun {
  runId: number;
  planId: string;
  letterType: string;
  letterTypeName: string;
  /** Deadline for claim submission. ISO date, e.g. 2026-02-01 */
  claimDeadlineDate: string;
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
  planId: string;
  letterType: string;
  /** ISO date */
  claimDeadlineDate: string;
  /** Required, 1 to 500 characters. Passed to the comment store; not returned in run responses. */
  comment: string;
}

export interface CaseDocument {
  docId: string;
  appId: string;
  documentType: string;
  createdAt: string;
}

/** A run that is not complete yet: queued, in progress, or stalled. */
export function isActive(run: LetterRun): boolean {
  return run.status !== 'COMPLETE';
}
