import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CaseDocument,
  EligibleCount,
  GenerateRequest,
  LetterRun,
  LetterType,
  RunPage,
} from './models';

/** case-service serves everything under /api; the dev-server proxy (proxy.conf.json) forwards it. */
const API = '/api';

@Injectable({ providedIn: 'root' })
export class LettersApi {
  private readonly http = inject(HttpClient);

  letterTypes(): Observable<LetterType[]> {
    return this.http.get<LetterType[]>(`${API}/letter-types`);
  }

  eligibleCount(code: string): Observable<EligibleCount> {
    return this.http.get<EligibleCount>(
      `${API}/letter-types/${encodeURIComponent(code)}/eligible-count`,
    );
  }

  /** Duplicates are rejected by the server (409 RUN_IN_PROGRESS), so no idempotency key is sent. */
  generate(request: GenerateRequest): Observable<LetterRun> {
    return this.http.post<LetterRun>(`${API}/letter-runs`, request);
  }

  listRuns(page: number, size: number): Observable<RunPage> {
    const params = new HttpParams().set('page', page).set('size', size);
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
