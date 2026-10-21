import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { ActionSelectCard } from './action-select-card';
import { AdminPage } from './admin-page';
import { GENERATE_DECISION_LETTER } from './admin-actions';

describe('AdminPage', () => {
  let fixture: ComponentFixture<AdminPage>;
  let http: HttpTestingController;
  let card: ActionSelectCard;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    http.expectOne('/api/plan-types').flush([{ id: 'PT-1', name: '[Plan type 1]' }]);
    card = fixture.debugElement.query(By.directive(ActionSelectCard)).componentInstance;

    card.planTypeId.setValue('PT-1');
    http.expectOne('/api/plan-types/PT-1/plans').flush([{ id: 'PLAN-1A', planTypeId: 'PT-1', name: '[Plan 1A]' }]);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('asks for a plan type, plan and action until an action is chosen', async () => {
    expect(text()).toContain('Choose a plan type, plan and action to continue.');

    card.planId.setValue('PLAN-1A');
    await fixture.whenStable();
    expect(text()).toContain('Choose a plan type, plan and action to continue.');
    expect(fixture.nativeElement.querySelector('app-letter-generation-page')).toBeNull();
  });

  it('shows the Generate card and runs table for "Generate decision letter"', async () => {
    card.planId.setValue('PLAN-1A');
    card.actionId.setValue(GENERATE_DECISION_LETTER);
    await fixture.whenStable();

    http.expectOne('/api/letter-types').flush([]);
    http.expectOne('/api/letter-runs?planId=PLAN-1A&page=0&size=10').flush({ total: 0, items: [] });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('app-letter-generation-page')).not.toBeNull();
    expect(text()).toContain('Letter generation runs for [Plan 1A]');
    expect(text()).not.toContain('Choose a plan type, plan and action to continue.');
  });

  it('shows a placeholder for any other action', async () => {
    card.planId.setValue('PLAN-1A');
    card.actionId.setValue('PLACEHOLDER_ACTION_1');
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('app-letter-generation-page')).toBeNull();
    expect(text()).toContain('[Placeholder action 1]');
    expect(text()).toContain('Placeholder:');
  });
});
