import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { LettersApi } from '../api/letters-api';
import { LetterRun, LetterType } from '../api/models';
import { problemMessage, toProblem } from '../api/problem';
import { toIsoDate } from './dates';
import { GenerateDialog, GenerateDialogData, GenerateDialogResult } from './generate-dialog';

/** ADMIN only. Picks a letter type and claim submitted date and starts a run. */
@Component({
  selector: 'app-generate-card',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './generate-card.html',
  styleUrl: './generate-card.scss',
})
export class GenerateCard {
  private readonly api = inject(LettersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly letterTypes = input.required<LetterType[]>();
  /**
   * Letter types with a QUEUED, IN_PROGRESS or NEEDS_ATTENTION run, from the runs the page has loaded.
   * case-service also rejects a second run (409 RUN_IN_PROGRESS), so this is guidance only.
   */
  readonly activeLetterTypes = input<ReadonlySet<string>>(new Set());
  /** Letter types with any run among the runs the page has loaded. */
  readonly letterTypesWithRuns = input<ReadonlySet<string>>(new Set());
  /** Why the page couldn't load the letter types; null once they have loaded. */
  readonly letterTypesError = input<string | null>(null);
  readonly generated = output<LetterRun>();
  readonly retryLetterTypes = output<void>();

  /** Eligible count for the selected type; null while unknown or loading. */
  readonly eligibleCount = signal<number | null>(null);
  protected readonly countLoading = signal(false);
  /** Why the eligible count couldn't be loaded; shown under the letter type with a retry. */
  protected readonly countError = signal<string | null>(null);

  readonly form = new FormGroup({
    letterType: new FormControl<string | null>(null, [
      Validators.required,
      (control) => this.letterTypeAvailable(control),
    ]),
    claimSubmittedDate: new FormControl<Date | null>(null, Validators.required),
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  protected readonly hasPreviousRuns = computed(() => {
    const letterType = this.formValue().letterType;
    return !!letterType && this.letterTypesWithRuns().has(letterType);
  });

  protected readonly canGenerate = computed(() => {
    const { letterType, claimSubmittedDate } = this.formValue();
    const count = this.eligibleCount();
    return (
      !!letterType &&
      !!claimSubmittedDate &&
      count !== null &&
      count > 0 &&
      !this.activeLetterTypes().has(letterType)
    );
  });

  constructor() {
    this.form.controls.letterType.valueChanges.subscribe((code) => this.loadEligibleCount(code));

    // Re-check the letter type when the set of active runs changes (polling).
    effect(() => {
      this.activeLetterTypes();
      this.revalidateLetterType();
    });
  }

  protected openConfirmation(): void {
    const { letterType, claimSubmittedDate } = this.form.getRawValue();
    if (!this.canGenerate() || !letterType || !claimSubmittedDate) {
      return;
    }
    const data: GenerateDialogData = {
      letterType,
      letterTypeName: this.letterTypes().find((t) => t.code === letterType)?.name ?? letterType,
      claimSubmittedDate: toIsoDate(claimSubmittedDate),
      eligibleCount: this.eligibleCount() ?? 0,
    };
    this.dialog
      .open<GenerateDialog, GenerateDialogData, GenerateDialogResult>(GenerateDialog, { data, width: '480px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }
        if ('run' in result) {
          this.snackBar.open(`${result.run.letterTypeName} queued: ${result.run.totalCount} letters`, 'Dismiss', {
            duration: 6000,
          });
          this.generated.emit(result.run);
        } else {
          this.snackBar.open(problemMessage(result.problem), 'Dismiss', { duration: 8000 });
        }
        this.loadEligibleCount(letterType);
      });
  }

  protected retryEligibleCount(): void {
    this.loadEligibleCount(this.form.controls.letterType.value);
  }

  private loadEligibleCount(code: string | null): void {
    this.eligibleCount.set(null);
    this.countError.set(null);
    this.countLoading.set(!!code);
    this.revalidateLetterType();
    if (!code) {
      return;
    }
    this.api.eligibleCount(code).subscribe({
      next: (result) => {
        if (this.form.controls.letterType.value === code) {
          this.countLoading.set(false);
          this.eligibleCount.set(result.eligibleCount);
          this.revalidateLetterType();
        }
      },
      error: (err) => {
        if (this.form.controls.letterType.value === code) {
          this.countLoading.set(false);
          this.countError.set(problemMessage(toProblem(err)));
          this.revalidateLetterType();
        }
      },
    });
  }

  private revalidateLetterType(): void {
    const control = this.form.controls.letterType;
    control.updateValueAndValidity({ emitEvent: false });
    if (control.value) {
      control.markAsTouched(); // so the mat-error shows straight away
    }
  }

  /**
   * Errors shown under the letter type: a run still generating, the eligible count failing to
   * load, or nothing left to generate.
   */
  private letterTypeAvailable(control: AbstractControl<string | null>): ValidationErrors | null {
    const code = control.value;
    if (!code) {
      return null;
    }
    if (this.activeLetterTypes?.().has(code)) {
      return { runActive: true };
    }
    if (this.countError?.()) {
      return { countUnavailable: true };
    }
    if (this.eligibleCount?.() === 0) {
      return { noEligible: true };
    }
    return null;
  }
}
