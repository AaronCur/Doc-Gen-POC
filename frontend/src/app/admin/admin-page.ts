import { Component, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

import { LetterGenerationPage } from '../letter-generation/letter-generation-page';
import { ActionSelectCard } from './action-select-card';
import { AdminSelection, GENERATE_DECISION_LETTER } from './admin-actions';

/**
 * The admin screen. Letter generation sits inside it: pick a plan type, plan and action, and
 * "Generate decision letter" shows the Generate card and runs table for that plan.
 */
@Component({
  selector: 'app-admin-page',
  imports: [MatCardModule, MatIconModule, ActionSelectCard, LetterGenerationPage],
  template: `
    <app-action-select-card (selectionChange)="selection.set($event)" />

    @let plan = selection().plan;
    @let action = selection().action;
    @if (!plan || !action) {
      <mat-card appearance="filled" role="status">
        <mat-card-content class="banner">
          <mat-icon aria-hidden="true">info</mat-icon>
          <span>Choose a plan type, plan and action to continue.</span>
        </mat-card-content>
      </mat-card>
    } @else if (action.id === generateDecisionLetter) {
      <app-letter-generation-page [plan]="plan" />
    } @else {
      <mat-card>
        <mat-card-header>
          <h2 mat-card-title>{{ action.name }}</h2>
        </mat-card-header>
        <mat-card-content>
          <p>Placeholder: this action's screen is part of the existing admin screen, not this POC.</p>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .banner {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  `,
})
export class AdminPage {
  protected readonly generateDecisionLetter = GENERATE_DECISION_LETTER;
  protected readonly selection = signal<AdminSelection>({ plan: null, action: null });
}
