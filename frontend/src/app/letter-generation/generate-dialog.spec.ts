import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { vi } from 'vitest';

import { GenerateDialog, GenerateDialogData } from './generate-dialog';

const DATA: GenerateDialogData = {
  planId: 'PLAN-1A',
  letterType: 'LETTER_A',
  letterTypeName: 'Letter type A',
  eligibleCount: 18,
};

describe('GenerateDialog', () => {
  let fixture: ComponentFixture<GenerateDialog>;
  let dialog: GenerateDialog;
  let http: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [GenerateDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GenerateDialog);
    dialog = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function confirmButton(): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Generate' || b.textContent?.trim() === 'Retry',
    )!;
  }

  async function fill(date: Date | null, comment: string) {
    dialog.form.setValue({ claimDeadlineDate: date, comment });
    await fixture.whenStable();
  }

  it('starts with both fields empty and Generate disabled', () => {
    expect(dialog.form.getRawValue()).toEqual({ claimDeadlineDate: null, comment: '' });
    expect(confirmButton().disabled).toBe(true);
  });

  it('shows the summary, the field labels and the queued note', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Letter type A');
    expect(text).toContain('18');
    expect(text).toContain('Staff only');
    expect(text).toContain('Deadline for claim submission');
    expect(text).toContain('Recorded against each application in this run.');
    expect(text).toContain('Comment');
    expect(text).toContain('Letters are queued for generation and appear on staff screens as they complete.');
  });

  it('keeps Generate disabled until both the deadline and the comment are filled', async () => {
    await fill(new Date(2026, 1, 1), '');
    expect(confirmButton().disabled).toBe(true);

    await fill(null, 'Monthly run');
    expect(confirmButton().disabled).toBe(true);

    await fill(new Date(2026, 1, 1), 'Monthly run');
    expect(confirmButton().disabled).toBe(false);
  });

  it('treats a comment of only spaces as missing', async () => {
    await fill(new Date(2026, 1, 1), '   ');
    expect(confirmButton().disabled).toBe(true);
  });

  it('rejects a comment over 500 characters and counts the characters', async () => {
    await fill(new Date(2026, 1, 1), 'x'.repeat(501));
    expect(confirmButton().disabled).toBe(true);

    await fill(new Date(2026, 1, 1), 'x'.repeat(500));
    expect(confirmButton().disabled).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('500 / 500');
  });

  it('posts the plan, letter type, deadline and trimmed comment, then closes with the new run', async () => {
    await fill(new Date(2026, 1, 1), '  Monthly run  ');
    confirmButton().click();

    const request = http.expectOne('/api/letter-runs');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      planId: 'PLAN-1A',
      letterType: 'LETTER_A',
      claimDeadlineDate: '2026-02-01',
      comment: 'Monthly run',
    });
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);

    request.flush({ runId: 1 });
    expect(dialogRef.close).toHaveBeenCalledWith({ run: { runId: 1 } });
  });

  it('disables the confirm button while the request is in flight', async () => {
    await fill(new Date(2026, 1, 1), 'Monthly run');
    confirmButton().click();
    await fixture.whenStable();

    expect(confirmButton().disabled).toBe(true);

    http.expectOne('/api/letter-runs').flush({ runId: 1 });
  });

  it('offers Retry after a server error, and closes with the problem for a 409', async () => {
    await fill(new Date(2026, 1, 1), 'Monthly run');
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
