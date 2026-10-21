import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  InjectionToken,
  input,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';

import { LettersApi } from '../api/letters-api';
import { isActive, LetterRun, LetterType, Plan } from '../api/models';
import { problemMessage, toProblem } from '../api/problem';
import { DevUser } from '../auth/dev-user';
import { EnableDialog } from './enable-dialog';
import { GenerateCard } from './generate-card';
import { RunsTable } from './runs-table';

/** How often to poll GET /letter-runs while any run is QUEUED, IN_PROGRESS or NEEDS_ATTENTION. */
export const RUNS_POLL_INTERVAL_MS = new InjectionToken<number>('RUNS_POLL_INTERVAL_MS', {
  factory: () => 15_000,
});

/**
 * The "Generate decision letter" section of the admin screen, for one plan: the Generate card
 * (admins) and that plan's runs table.
 */
@Component({
  selector: 'app-letter-generation-page',
  imports: [MatCardModule, MatIconModule, GenerateCard, RunsTable],
  styleUrl: './letter-generation-page.scss',
  templateUrl: './letter-generation-page.html',
})
export class LetterGenerationPage implements OnInit {
  private readonly api = inject(LettersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly pollIntervalMs = inject(RUNS_POLL_INTERVAL_MS);
  protected readonly devUser = inject(DevUser);

  /** The plan chosen in "Select an action". Changing it reloads the runs. */
  readonly plan = input.required<Plan>();

  protected readonly letterTypes = signal<LetterType[]>([]);
  /** Shown in the generate card with a retry; only admins use the letter types. */
  protected readonly letterTypesError = signal<string | null>(null);
  protected readonly runs = signal<LetterRun[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);

  /**
   * Types with an active run among the plan's loaded runs. Active runs are the newest, so they are
   * on the first page in practice; case-service enforces the rule regardless (409 RUN_IN_PROGRESS).
   */
  protected readonly activeLetterTypes = computed(
    () => new Set(this.runs().filter(isActive).map((run) => run.letterType)),
  );

  protected readonly letterTypesWithRuns = computed(() => new Set(this.runs().map((run) => run.letterType)));

  private pollTimer: ReturnType<typeof setTimeout> | undefined;
  private loading: Subscription | undefined;
  private destroyed = false;

  constructor() {
    // Load the runs for the plan, and again from page 1 whenever the plan changes.
    effect(() => {
      this.plan();
      untracked(() => {
        this.pageIndex.set(0);
        this.loadRuns();
      });
    });

    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.stopPolling();
      this.loading?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.loadLetterTypes();
  }

  protected loadLetterTypes(): void {
    this.letterTypesError.set(null);
    this.api.letterTypes().subscribe({
      next: (types) => this.letterTypes.set(types),
      error: (err) => this.letterTypesError.set(problemMessage(toProblem(err))),
    });
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadRuns();
  }

  protected onGenerated(): void {
    this.pageIndex.set(0);
    this.loadRuns();
  }

  protected confirmEnable(run: LetterRun): void {
    this.dialog
      .open<EnableDialog, LetterRun, boolean>(EnableDialog, { data: run, width: '480px' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.release(run.runId).subscribe({
          next: (released) => {
            this.snackBar.open(`${released.letterTypeName} is now visible to external users`, 'Dismiss', {
              duration: 6000,
            });
            this.loadRuns();
          },
          error: (err) => {
            this.showProblem(err);
            this.loadRuns();
          },
        });
      });
  }

  /** Loads the current page, then keeps polling only while a run is still in progress. */
  private loadRuns(): void {
    this.stopPolling();
    this.loading?.unsubscribe();
    this.loading = this.api.listRuns(this.plan().id, this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.runs.set(page.items);
        this.total.set(page.total);
        if (page.items.some(isActive)) {
          this.schedulePoll();
        }
      },
      error: (err) => {
        this.showProblem(err);
        this.schedulePoll(); // try again later rather than going silent
      },
    });
  }

  private schedulePoll(): void {
    if (this.destroyed) {
      return;
    }
    this.pollTimer = setTimeout(() => this.loadRuns(), this.pollIntervalMs);
  }

  private stopPolling(): void {
    clearTimeout(this.pollTimer);
    this.pollTimer = undefined;
  }

  private showProblem(err: unknown): void {
    this.snackBar.open(problemMessage(toProblem(err)), 'Dismiss', { duration: 8000 });
  }
}
