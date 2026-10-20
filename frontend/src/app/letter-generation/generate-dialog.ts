import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { LettersApi } from '../api/letters-api';
import { LetterRun } from '../api/models';
import { ApiProblem, isRetryable, problemMessage, toProblem } from '../api/problem';

export interface GenerateDialogData {
  letterType: string;
  letterTypeName: string;
  /** ISO date */
  claimSubmittedDate: string;
  eligibleCount: number;
}

/** Closed with the new run, with a business-rule problem (409/422/400), or undefined if cancelled. */
export type GenerateDialogResult = { run: LetterRun } | { problem: ApiProblem } | undefined;

@Component({
  selector: 'app-generate-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>Generate letters?</h2>
    <mat-dialog-content>
      <dl class="summary">
        <div>
          <dt>Letter type</dt>
          <dd>{{ data.letterTypeName }}</dd>
        </div>
        <div>
          <dt>Claim submitted date</dt>
          <dd>{{ data.claimSubmittedDate | date: 'dd/MM/yyyy' }}</dd>
        </div>
        <div>
          <dt>New applications to include</dt>
          <dd>{{ data.eligibleCount }}</dd>
        </div>
        <div>
          <dt>Initial visibility</dt>
          <dd>Staff only</dd>
        </div>
      </dl>
      <p>Letters are queued for generation and appear on staff screens as they complete.</p>
      @if (submitting()) {
        <mat-progress-bar mode="indeterminate" aria-label="Submitting" />
      }
      @if (retryError()) {
        <p class="error" role="alert">{{ retryError() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined" [disabled]="submitting()">Cancel</button>
      <button mat-flat-button type="button" (click)="confirm()" [disabled]="submitting()">
        {{ retryError() ? 'Retry' : 'Generate' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .summary {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      margin: 0 0 16px;
      padding: 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container);
    }
    .summary dt {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .summary dd {
      margin: 2px 0 0;
      font-weight: 500;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class GenerateDialog {
  protected readonly data = inject<GenerateDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<GenerateDialog, GenerateDialogResult>>(MatDialogRef);
  private readonly api = inject(LettersApi);

  protected readonly submitting = signal(false);
  protected readonly retryError = signal<string | null>(null);

  protected confirm(): void {
    this.submitting.set(true);
    this.retryError.set(null);
    this.dialogRef.disableClose = true;
    this.api
      .generate({ letterType: this.data.letterType, claimSubmittedDate: this.data.claimSubmittedDate })
      .subscribe({
        next: (run) => this.dialogRef.close({ run }),
        error: (err) => {
          const problem = toProblem(err);
          if (isRetryable(problem)) {
            this.submitting.set(false);
            this.dialogRef.disableClose = false;
            this.retryError.set(problemMessage(problem));
          } else {
            this.dialogRef.close({ problem });
          }
        },
      });
  }
}
