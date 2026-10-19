import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { vi } from 'vitest';

import { LetterRun, RunPage } from '../api/models';
import { LetterGenerationPage, RUNS_POLL_INTERVAL_MS } from './letter-generation-page';

const POLL_MS = 1000;
const RUNS_URL = '/api/letter-runs?page=0&size=10';

function page(...statuses: LetterRun['status'][]): RunPage {
  return {
    total: statuses.length,
    items: statuses.map((status, i) => ({
      runId: i + 1,
      letterType: 'LETTER_A',
      letterTypeName: 'Letter type A',
      claimSubmittedDate: '2026-02-01',
      status,
      visibility: 'STAFF_ONLY',
      totalCount: 10,
      generatedCount: status === 'COMPLETE' ? 10 : 4,
      requestedBy: 'admin',
      requestedAt: '2026-03-02T09:00:00Z',
      releasedBy: null,
      releasedAt: null,
    })),
  };
}

describe('LetterGenerationPage polling', () => {
  let fixture: ComponentFixture<LetterGenerationPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    // Only setTimeout is faked, so Angular's own scheduling is unaffected.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    await TestBed.configureTestingModule({
      imports: [LetterGenerationPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        { provide: RUNS_POLL_INTERVAL_MS, useValue: POLL_MS },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LetterGenerationPage);
    fixture.detectChanges(); // ngOnInit
    http.expectOne('/api/letter-types').flush([{ code: 'LETTER_A', name: 'Letter type A' }]);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('polls while a run is generating and stops when none are active', () => {
    http.expectOne(RUNS_URL).flush(page('GENERATING', 'COMPLETE'));

    vi.advanceTimersByTime(POLL_MS);
    http.expectOne(RUNS_URL).flush(page('NEEDS_ATTENTION', 'COMPLETE')); // still active

    vi.advanceTimersByTime(POLL_MS);
    http.expectOne(RUNS_URL).flush(page('COMPLETE', 'COMPLETE')); // nothing active now

    vi.advanceTimersByTime(POLL_MS * 5);
    http.expectNone(RUNS_URL);
  });

  it('does not poll when no run is active', () => {
    http.expectOne(RUNS_URL).flush(page('COMPLETE'));

    vi.advanceTimersByTime(POLL_MS * 5);
    http.expectNone(RUNS_URL);
  });

  it('does not poll before the interval has passed', () => {
    http.expectOne(RUNS_URL).flush(page('GENERATING'));

    vi.advanceTimersByTime(POLL_MS - 1);
    http.expectNone(RUNS_URL);

    vi.advanceTimersByTime(1);
    http.expectOne(RUNS_URL).flush(page('COMPLETE'));
  });

  it('stops polling when the page is destroyed', () => {
    http.expectOne(RUNS_URL).flush(page('GENERATING'));

    fixture.destroy();
    vi.advanceTimersByTime(POLL_MS * 5);

    http.expectNone(RUNS_URL);
  });
});

describe('LetterGenerationPage letter types', () => {
  let fixture: ComponentFixture<LetterGenerationPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LetterGenerationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNativeDateAdapter()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LetterGenerationPage);
    fixture.detectChanges(); // ngOnInit
    http.expectOne(RUNS_URL).flush(page('COMPLETE'));
  });

  afterEach(() => http.verify());

  it('shows a failed letter types load inline and retries it', async () => {
    http.expectOne('/api/letter-types').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    const alert = fixture.nativeElement.querySelector('app-generate-card [role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain("Couldn't load letter types.");

    alert.querySelector('button')!.click();
    http.expectOne('/api/letter-types').flush([{ code: 'LETTER_A', name: 'Letter type A' }]);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('app-generate-card [role="alert"]')).toBeNull();
  });
});
