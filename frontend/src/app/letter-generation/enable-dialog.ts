import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { LetterRun } from '../api/models';

/** Confirms releasing a run to external users. Closes with true to proceed. */
@Component({
  selector: 'app-enable-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Enable for external users?</h2>
    <mat-dialog-content>
      <p>
        {{ run.totalCount }} letters of <strong>{{ run.letterTypeName }}</strong> (claim submitted date
        {{ run.claimSubmittedDate | date: 'dd/MM/yyyy' }}) will become visible to external users.
      </p>
      <p>This can't be undone from this screen. Your name and the time are recorded.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true">Enable</button>
    </mat-dialog-actions>
  `,
})
export class EnableDialog {
  protected readonly run = inject<LetterRun>(MAT_DIALOG_DATA);
}
