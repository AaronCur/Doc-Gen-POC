import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
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
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { LettersApi } from '../api/letters-api';
import { LetterRun, LetterType } from '../api/models';
import { problemMessage, toProblem } from '../api/problem';
import { GenerateDialog, GenerateDialogData, GenerateDialogResult } from './generate-dialog';

/**
 * ADMIN only. Picks a letter type for the selected plan and opens the Generate popup, which asks
 * for the deadline for claim submission and the comment.
 */
@Component({
  selector: 'app-generate-card',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  templateUrl: './generate-card.html',
  styleUrl: './generate-card.scss',
})
export class GenerateCard {
  private readonly api = inject(LettersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** The plan chosen in "Select an action". Changing it clears the chosen letter type. */
  readonly planId = input.required<string>();
  readonly letterTypes = input.required<LetterType[]>();
  /**
   * Letter types with a QUEUED, IN_PROGRESS or NEEDS_ATTENTION run in this plan, from the runs
   * the page has loaded.
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
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  protected readonly hasPreviousRuns = computed(() => {
    const letterType = this.formValue().letterType;
    return !!letterType && this.letterTypesWithRuns().has(letterType);
  });

  protected readonly canGenerate = computed(() => {
    const { letterType } = this.formValue();
    const count = this.eligibleCount();
    return (
      !!letterType &&
      count !== null &&
      count > 0 &&
      !this.activeLetterTypes().has(letterType)
    );
  });

  constructor() {
    this.form.controls.letterType.valueChanges.subscribe((code) => this.loadEligibleCount(code));

    // A different plan: start again with no letter type chosen.
    effect(() => {
      this.planId();
      untracked(() => this.form.controls.letterType.setValue(null));
    });

    // Re-check the letter type when the set of active runs changes (polling).
    effect(() => {
      this.activeLetterTypes();
      this.revalidateLetterType();
    });
  }

  protected openConfirmation(): void {
    const { letterType } = this.form.getRawValue();
    if (!this.canGenerate() || !letterType) {
      return;
    }
    const data: GenerateDialogData = {
      planId: this.planId(),
      letterType,
      letterTypeName: this.letterTypes().find((t) => t.code === letterType)?.name ?? letterType,
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
    this.api.eligibleCount(code, this.planId()).subscribe({
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
