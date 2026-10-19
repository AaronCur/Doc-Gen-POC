import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';

import { LettersApi } from '../api/letters-api';
import { CaseDocument } from '../api/models';
import { problemMessage, toProblem } from '../api/problem';

/**
 * Demonstrates the staged reveal: lists an application's documents as an external user sees
 * them, through the external endpoint that applies DocumentVisibilityPolicy.
 */
@Component({
  selector: 'app-external-view-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>External view</mat-card-title>
        <mat-card-subtitle>Documents an external user can see for an application</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <form class="lookup" [formGroup]="lookup" (ngSubmit)="load()">
          <mat-form-field appearance="outline">
            <mat-label>Application ID</mat-label>
            <input matInput formControlName="appId" required />
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="lookup.invalid">Show documents</button>
        </form>

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }

        @if (documents(); as docs) {
          @if (docs.length === 0) {
            <p role="status">No documents are visible to external users for {{ shownAppId() }}.</p>
          } @else {
            <table mat-table [dataSource]="docs" aria-label="Documents visible to external users">
              <ng-container matColumnDef="docId">
                <th mat-header-cell *matHeaderCellDef>Document ID</th>
                <td mat-cell *matCellDef="let doc">{{ doc.docId }}</td>
              </ng-container>
              <ng-container matColumnDef="documentType">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let doc">{{ doc.documentType }}</td>
              </ng-container>
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>Created</th>
                <td mat-cell *matCellDef="let doc">{{ doc.createdAt | date: 'dd MMM yyyy, HH:mm' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
          }
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .lookup {
      display: flex;
      gap: 16px;
      align-items: baseline;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class ExternalViewPage {
  private readonly api = inject(LettersApi);

  protected readonly columns = ['docId', 'documentType', 'createdAt'];
  protected readonly lookup = new FormGroup({
    appId: new FormControl('APP-0001', { nonNullable: true, validators: Validators.required }),
  });
  protected readonly documents = signal<CaseDocument[] | null>(null);
  protected readonly shownAppId = signal('');
  protected readonly error = signal<string | null>(null);

  protected load(): void {
    const appId = this.lookup.controls.appId.value.trim();
    this.error.set(null);
    this.api.externalDocuments(appId).subscribe({
      next: (docs) => {
        this.shownAppId.set(appId);
        this.documents.set(docs);
      },
      error: (err) => {
        this.documents.set(null);
        this.error.set(problemMessage(toProblem(err)));
      },
    });
  }
}
