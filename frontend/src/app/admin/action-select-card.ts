import { Component, inject, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

import { LettersApi } from '../api/letters-api';
import { Plan, PlanType } from '../api/models';
import { problemMessage, toProblem } from '../api/problem';
import { ADMIN_ACTIONS, AdminSelection } from './admin-actions';

/**
 * "Select an action": Plan type, then Plan, then Action. Each select is disabled until the one
 * before it is chosen, and changing one clears everything after it.
 */
@Component({
  selector: 'app-action-select-card',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatSelectModule],
  template: `
    <mat-card>
      <mat-card-header>
        <h2 mat-card-title>Select an action</h2>
      </mat-card-header>
      <mat-card-content>
        <div class="fields">
          <mat-form-field appearance="outline">
            <mat-label>Plan type</mat-label>
            <mat-select [formControl]="planTypeId">
              @for (planType of planTypes(); track planType.id) {
                <mat-option [value]="planType.id">{{ planType.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Plan</mat-label>
            <mat-select [formControl]="planId">
              @for (plan of plans(); track plan.id) {
                <mat-option [value]="plan.id">{{ plan.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Action</mat-label>
            <mat-select [formControl]="actionId">
              @for (action of actions; track action.id) {
                <mat-option [value]="action.id">{{ action.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        @if (loadError(); as message) {
          <p class="error" role="alert">
            {{ message }}
            <button mat-button type="button" (click)="retryLoad()">Retry</button>
          </p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .fields {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding-top: 8px;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class ActionSelectCard implements OnInit {
  private readonly api = inject(LettersApi);

  readonly selectionChange = output<AdminSelection>();

  protected readonly actions = ADMIN_ACTIONS;
  protected readonly planTypes = signal<PlanType[]>([]);
  protected readonly plans = signal<Plan[]>([]);
  protected readonly loadError = signal<string | null>(null);

  readonly planTypeId = new FormControl<string | null>(null);
  readonly planId = new FormControl<string | null>({ value: null, disabled: true });
  readonly actionId = new FormControl<string | null>({ value: null, disabled: true });

  constructor() {
    this.planTypeId.valueChanges.pipe(takeUntilDestroyed()).subscribe((planTypeId) => {
      this.clear(this.planId, !planTypeId);
      this.clear(this.actionId, true);
      this.plans.set([]);
      if (planTypeId) {
        this.loadPlans(planTypeId);
      }
      this.emitSelection();
    });
    this.planId.valueChanges.pipe(takeUntilDestroyed()).subscribe((planId) => {
      this.clear(this.actionId, !planId);
      this.emitSelection();
    });
    this.actionId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.emitSelection());
  }

  ngOnInit(): void {
    this.loadPlanTypes();
  }

  protected retryLoad(): void {
    const planTypeId = this.planTypeId.value;
    if (planTypeId) {
      this.loadPlans(planTypeId);
    } else {
      this.loadPlanTypes();
    }
  }

  private loadPlanTypes(): void {
    this.loadError.set(null);
    this.api.planTypes().subscribe({
      next: (planTypes) => this.planTypes.set(planTypes),
      error: (err) => this.loadError.set(`Couldn't load plan types. ${problemMessage(toProblem(err))}`),
    });
  }

  private loadPlans(planTypeId: string): void {
    this.loadError.set(null);
    this.api.plans(planTypeId).subscribe({
      next: (plans) => {
        if (this.planTypeId.value === planTypeId) {
          this.plans.set(plans);
        }
      },
      error: (err) => this.loadError.set(`Couldn't load plans. ${problemMessage(toProblem(err))}`),
    });
  }

  /** Empties a later select without re-triggering the cascade, and disables it if asked. */
  private clear(control: FormControl<string | null>, disable: boolean): void {
    control.reset(null, { emitEvent: false });
    if (disable) {
      control.disable({ emitEvent: false });
    } else {
      control.enable({ emitEvent: false });
    }
  }

  private emitSelection(): void {
    this.selectionChange.emit({
      plan: this.plans().find((plan) => plan.id === this.planId.value) ?? null,
      action: this.actions.find((action) => action.id === this.actionId.value) ?? null,
    });
  }
}
