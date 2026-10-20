import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { vi } from 'vitest';

import { GenerateDialog, GenerateDialogData } from './generate-dialog';

const DATA: GenerateDialogData = {
  letterType: 'LETTER_A',
  letterTypeName: 'Letter type A',
  claimSubmittedDate: '2026-02-01',
  eligibleCount: 18,
};

describe('GenerateDialog', () => {
  let fixture: ComponentFixture<GenerateDialog>;
  let http: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [GenerateDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GenerateDialog);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function confirmButton(): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Generate' || b.textContent?.trim() === 'Retry',
    )!;
  }

  it('posts the request without an Idempotency-Key and closes with the new run', async () => {
    confirmButton().click();

    const request = http.expectOne('/api/letter-runs');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ letterType: 'LETTER_A', claimSubmittedDate: '2026-02-01' });
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);

    request.flush({ runId: 1 });
    expect(dialogRef.close).toHaveBeenCalledWith({ run: { runId: 1 } });
  });

  it('disables the confirm button while the request is in flight', async () => {
    confirmButton().click();
    await fixture.whenStable();

    expect(confirmButton().disabled).toBe(true);

    http.expectOne('/api/letter-runs').flush({ runId: 1 });
  });

  it('offers Retry after a server error, and closes with the problem for a 409', async () => {
    confirmButton().click();
    http.expectOne('/api/letter-runs').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(confirmButton().textContent?.trim()).toBe('Retry');
    expect(confirmButton().disabled).toBe(false);

    // The first attempt may have reached the server; the retry is then rejected, not duplicated.
    confirmButton().click();
    http
      .expectOne('/api/letter-runs')
      .flush({ status: 409, code: 'RUN_IN_PROGRESS' }, { status: 409, statusText: 'Conflict' });

    expect(dialogRef.close).toHaveBeenCalledWith({ problem: expect.objectContaining({ code: 'RUN_IN_PROGRESS' }) });
  });
});
