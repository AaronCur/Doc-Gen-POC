import { HttpErrorResponse } from '@angular/common/http';

/** The parts of an RFC 9457 ProblemDetail the UI uses. `code` is case-service's stable code. */
export interface ApiProblem {
  status: number;
  code?: string;
  detail?: string;
}

export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiProblem> | null;
    return { status: error.status, code: body?.code, detail: body?.detail };
  }
  return { status: 0 };
}

/**
 * Worth retrying: the request may not have reached the server. If it did, the retry is rejected
 * with 409 RUN_IN_PROGRESS rather than creating a second run.
 */
export function isRetryable(problem: ApiProblem): boolean {
  return problem.status === 0 || problem.status >= 500;
}

const MESSAGES: Record<string, string> = {
  RUN_IN_PROGRESS: 'A run for this letter type is still generating.',
  NO_ELIGIBLE_APPLICATIONS: 'No new applications are awaiting this letter type.',
  NOT_COMPLETE: 'This run can be enabled when generation completes.',
  ALREADY_RELEASED: 'This run is already visible to external users.',
  VALIDATION_FAILED: 'Please check the letter type, deadline for claim submission and comment.',
};

export function problemMessage(problem: ApiProblem): string {
  if (problem.code && MESSAGES[problem.code]) {
    return MESSAGES[problem.code];
  }
  if (problem.status === 403) {
    return 'You do not have permission to do that.';
  }
  if (isRetryable(problem)) {
    return 'Could not reach the server. Please try again.';
  }
  return problem.detail ?? `Something went wrong (HTTP ${problem.status}).`;
}
