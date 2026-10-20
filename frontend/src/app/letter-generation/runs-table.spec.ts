import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LetterRun } from '../api/models';
import { RunsTable } from './runs-table';

function run(overrides: Partial<LetterRun>): LetterRun {
  return {
    runId: 1,
    letterType: 'LETTER_A',
    letterTypeName: 'Letter type A',
    claimSubmittedDate: '2026-02-01',
    status: 'COMPLETE',
    visibility: 'STAFF_ONLY',
    totalCount: 10,
    generatedCount: 10,
    requestedBy: 'admin',
    requestedAt: '2026-03-02T09:00:00Z',
    releasedBy: null,
    releasedAt: null,
    ...overrides,
  };
}

describe('RunsTable', () => {
  let fixture: ComponentFixture<RunsTable>;
  let rendered: LetterRun[] = [];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RunsTable] }).compileComponents();
    fixture = TestBed.createComponent(RunsTable);
    fixture.componentRef.setInput('canEnable', true);
  });

  async function render(runs: LetterRun[]) {
    rendered = runs;
    fixture.componentRef.setInput('runs', runs);
    fixture.componentRef.setInput('total', runs.length);
    await fixture.whenStable();
  }

  function rowFor(runId: number): HTMLElement {
    const rows = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('tr.mat-mdc-row'));
    return rows[rendered.findIndex((r) => r.runId === runId)];
  }

  function enableButton(runId: number): HTMLButtonElement | null {
    return Array.from<HTMLButtonElement>(rowFor(runId).querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Enable for external users'),
    ) ?? null;
  }

  it('enables "Enable for external users" only for a COMPLETE, STAFF_ONLY run', async () => {
    await render([
      run({ runId: 1, status: 'COMPLETE', visibility: 'STAFF_ONLY' }),
      run({ runId: 2, status: 'IN_PROGRESS', generatedCount: 3 }),
      run({ runId: 3, status: 'NEEDS_ATTENTION', generatedCount: 9 }),
      run({ runId: 5, status: 'QUEUED', generatedCount: 0 }),
      run({ runId: 4, visibility: 'STAFF_AND_EXTERNAL', releasedBy: 'staff', releasedAt: '2026-03-03T10:00:00Z' }),
    ]);

    expect(enableButton(1)?.disabled).toBe(false);
    expect(enableButton(2)?.disabled).toBe(true);
    expect(enableButton(3)?.disabled).toBe(true);
    expect(enableButton(5)?.disabled).toBe(true);
    expect(enableButton(4)).toBeNull();
  });

  it('explains why Enable is unavailable', async () => {
    await render([
      run({ runId: 2, status: 'IN_PROGRESS', generatedCount: 3 }),
      run({ runId: 3, status: 'NEEDS_ATTENTION', generatedCount: 9 }),
      run({ runId: 5, status: 'QUEUED', generatedCount: 0 }),
    ]);

    expect(rowFor(2).textContent).toContain('Available when generation completes');
    expect(rowFor(5).textContent).toContain('Available when generation completes');
    expect(rowFor(3).textContent).toContain('Generation stalled; contact support');
    expect(rowFor(3).textContent).not.toContain('Available when generation completes');
  });

  it('labels each calculated status', async () => {
    await render([
      run({ runId: 1, status: 'QUEUED', generatedCount: 0 }),
      run({ runId: 2, status: 'IN_PROGRESS', generatedCount: 3 }),
      run({ runId: 3, status: 'COMPLETE' }),
      run({ runId: 4, status: 'NEEDS_ATTENTION', generatedCount: 9 }),
    ]);

    expect(rowFor(1).textContent).toContain('Queued');
    expect(rowFor(2).textContent).toContain('In progress');
    expect(rowFor(3).textContent).toContain('Complete');
    expect(rowFor(4).textContent).toContain('Needs attention');
  });

  it('shows who enabled a released run', async () => {
    await render([
      run({ runId: 4, visibility: 'STAFF_AND_EXTERNAL', releasedBy: 'staff', releasedAt: '2026-03-03T10:00:00Z' }),
    ]);

    expect(rowFor(4).textContent).toContain('Staff + External');
    expect(rowFor(4).textContent).toContain('Enabled 03 Mar 2026 by staff');
  });

  it('hides Enable from users without the STAFF role', async () => {
    fixture.componentRef.setInput('canEnable', false);
    await render([run({ runId: 1 })]);

    expect(enableButton(1)).toBeNull();
  });

  it('labels the progress bar for assistive technology', async () => {
    await render([run({ runId: 2, status: 'IN_PROGRESS', generatedCount: 3 })]);

    const bar = rowFor(2).querySelector('mat-progress-bar');
    expect(bar?.getAttribute('aria-label')).toBe('Letters generated for Letter type A: 3 of 10');
    expect(rowFor(2).textContent).toContain('3 / 10 letters');
  });

  it('emits enable with the run when clicked', async () => {
    const target = run({ runId: 1 });
    await render([target]);
    let emitted: LetterRun | undefined;
    fixture.componentInstance.enable.subscribe((r) => (emitted = r));

    enableButton(1)?.click();

    expect(emitted).toEqual(target);
  });

  it('announces "No action" for a released run instead of the dash', async () => {
    await render([run({ runId: 4, visibility: 'STAFF_AND_EXTERNAL', releasedBy: 'staff', releasedAt: '2026-03-03T10:00:00Z' })]);

    const cell = rowFor(4).querySelector('td.action') as HTMLElement;
    expect(cell.querySelector('[aria-label]')).toBeNull();
    expect(cell.querySelector('[aria-hidden="true"]')?.textContent).toBe('—');
    expect(cell.querySelector('.cdk-visually-hidden')?.textContent).toBe('No action');
  });
});
