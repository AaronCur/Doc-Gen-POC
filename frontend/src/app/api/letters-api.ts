import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CaseDocument,
  EligibleCount,
  GenerateRequest,
  LetterRun,
  LetterType,
  Plan,
  PlanType,
  RunPage,
} from './models';

/** case-service serves everything under /api; the dev-server proxy (proxy.conf.json) forwards it. */
const API = '/api';

@Injectable({ providedIn: 'root' })
export class LettersApi {
  private readonly http = inject(HttpClient);

  /** STAND-IN for the existing admin screen's plan types. */
  planTypes(): Observable<PlanType[]> {
    return this.http.get<PlanType[]>(`${API}/plan-types`);
  }

  /** STAND-IN for the existing admin screen's plans. */
  plans(planTypeId: string): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${API}/plan-types/${encodeURIComponent(planTypeId)}/plans`);
  }

  letterTypes(): Observable<LetterType[]> {
    return this.http.get<LetterType[]>(`${API}/letter-types`);
  }

  /** How many applications in the plan a run would include now. */
  eligibleCount(code: string, planId: string): Observable<EligibleCount> {
    return this.http.get<EligibleCount>(
      `${API}/letter-types/${encodeURIComponent(code)}/eligible-count`,
      { params: new HttpParams().set('planId', planId) },
    );
  }

  /** Duplicates are rejected by the server (409 RUN_IN_PROGRESS), so no idempotency key is sent. */
  generate(request: GenerateRequest): Observable<LetterRun> {
    return this.http.post<LetterRun>(`${API}/letter-runs`, request);
  }

  /** One plan's runs, newest first. */
  listRuns(planId: string, page: number, size: number): Observable<RunPage> {
    const params = new HttpParams().set('planId', planId).set('page', page).set('size', size);
    return this.http.get<RunPage>(`${API}/letter-runs`, { params });
  }

  release(runId: number): Observable<LetterRun> {
    return this.http.post<LetterRun>(`${API}/letter-runs/${runId}/release`, null);
  }

  externalDocuments(appId: string): Observable<CaseDocument[]> {
    return this.http.get<CaseDocument[]>(
      `${API}/external/applications/${encodeURIComponent(appId)}/documents`,
    );
  }
}
