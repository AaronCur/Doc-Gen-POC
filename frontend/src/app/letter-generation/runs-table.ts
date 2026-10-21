import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { LetterRun, RunStatus } from '../api/models';

const STATUS_LABELS: Record<RunStatus, string> = {
  QUEUED: 'Queued',
  IN_PROGRESS: 'In progress',
  COMPLETE: 'Complete',
  NEEDS_ATTENTION: 'Needs attention',
};

/** "Letter generation runs": presentation only. The page loads data and handles actions. */
@Component({
  selector: 'app-runs-table',
  imports: [DatePipe, MatButtonModule, MatChipsModule, MatIconModule, MatPaginatorModule, MatProgressBarModule, MatTableModule],
  templateUrl: './runs-table.html',
  styleUrl: './runs-table.scss',
})
export class RunsTable {
  readonly runs = input.required<LetterRun[]>();
  readonly total = input.required<number>();
  readonly pageIndex = input(0);
  readonly pageSize = input(10);
  /** Show the Enable action (users with the STAFF role). */
  readonly canEnable = input(false);

  readonly pageChange = output<PageEvent>();
  readonly enable = output<LetterRun>();

  protected readonly columns = ['letterType', 'claimDeadlineDate', 'requested', 'generation', 'visibility', 'action'];

  protected statusLabel(run: LetterRun): string {
    return STATUS_LABELS[run.status];
  }

  protected percent(run: LetterRun): number {
    return run.totalCount === 0 ? 0 : Math.round((run.generatedCount / run.totalCount) * 100);
  }

  /** Enable is offered only for a finished run that external users cannot see yet. */
  protected isEnableable(run: LetterRun): boolean {
    return run.status === 'COMPLETE' && run.visibility === 'STAFF_ONLY';
  }
}
