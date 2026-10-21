import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { LettersApi } from '../api/letters-api';
import { LetterRun } from '../api/models';
import { ApiProblem, isRetryable, problemMessage, toProblem } from '../api/problem';
import { toIsoDate } from './dates';

export interface GenerateDialogData {
  planId: string;
  letterType: string;
  letterTypeName: string;
  eligibleCount: number;
}

/** Closed with the new run, with a business-rule problem (409/422/400), or undefined if cancelled. */
export type GenerateDialogResult = { run: LetterRun } | { problem: ApiProblem } | undefined;

/** Matches case-service: the comment is required and at most 500 characters. */
export const COMMENT_MAX_LENGTH = 500;

/**
 * The Generate popup. Asks for the deadline for claim submission and the mandatory comment,
 * then posts the request itself. Both fields start empty every time it opens.
 */
@Component({
  selector: 'app-generate-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  template: `
    <h2 mat-dialog-title>Generate letters?</h2>
    <mat-dialog-content>
      <dl class="summary">
        <div class="full-width">
          <dt>Letter type</dt>
          <dd>{{ data.letterTypeName }}</dd>
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

      <form class="fields" [formGroup]="form">
        <mat-form-field appearance="outline">
          <mat-label>Deadline for claim submission</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="claimDeadlineDate" required />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
          <mat-hint>Recorded against each application in this run.</mat-hint>
          @if (form.controls.claimDeadlineDate.hasError('matDatepickerParse')) {
            <mat-error>Enter a valid date.</mat-error>
          } @else if (form.controls.claimDeadlineDate.hasError('required')) {
            <mat-error>Enter the deadline for claim submission.</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Comment</mat-label>
          <textarea
            matInput
            formControlName="comment"
            required
            rows="3"
            [maxlength]="commentMaxLength"
            placeholder="For example, why this run is being generated."
          ></textarea>
          <mat-hint align="end">{{ commentLength() }} / {{ commentMaxLength }}</mat-hint>
          @if (form.controls.comment.hasError('required') || form.controls.comment.hasError('pattern')) {
            <mat-error>Enter a comment.</mat-error>
          }
        </mat-form-field>
      </form>

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
      <button mat-flat-button type="button" (click)="confirm()" [disabled]="!formValid() || submitting()">
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
    .summary .full-width {
      grid-column: 1 / -1;
    }
    .summary dt {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .summary dd {
      margin: 2px 0 0;
      font-weight: 500;
    }
    .fields {
      display: flex;
      flex-direction: column;
      gap: 8px;
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

  protected readonly commentMaxLength = COMMENT_MAX_LENGTH;

  /** Both start empty each time the popup opens: a new dialog instance means a new form. */
  readonly form = new FormGroup({
    claimDeadlineDate: new FormControl<Date | null>(null, Validators.required),
    comment: new FormControl('', {
      nonNullable: true,
      // Not blank: at least one non-space character, as case-service requires.
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(COMMENT_MAX_LENGTH)],
    }),
  });

  private readonly formStatus = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  private readonly comment = toSignal(this.form.controls.comment.valueChanges, { initialValue: '' });

  protected readonly formValid = () => this.formStatus() === 'VALID';
  protected readonly commentLength = () => this.comment().length;

  protected readonly submitting = signal(false);
  protected readonly retryError = signal<string | null>(null);

  protected confirm(): void {
    const { claimDeadlineDate, comment } = this.form.getRawValue();
    if (!this.formValid() || !claimDeadlineDate) {
      return;
    }
    this.submitting.set(true);
    this.retryError.set(null);
    this.dialogRef.disableClose = true;
    this.api
      .generate({
        planId: this.data.planId,
        letterType: this.data.letterType,
        claimDeadlineDate: toIsoDate(claimDeadlineDate),
        comment: comment.trim(),
      })
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
