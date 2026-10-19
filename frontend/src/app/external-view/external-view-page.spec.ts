import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ExternalViewPage } from './external-view-page';

describe('ExternalViewPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExternalViewPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('submitting the form loads documents through the external endpoint (no native form submit)', async () => {
    const fixture = TestBed.createComponent(ExternalViewPage);
    await fixture.whenStable();
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    const submit = new Event('submit', { cancelable: true });

    form.dispatchEvent(submit);

    // Angular's form directive cancels the browser's own submit (which would reload the page).
    expect(submit.defaultPrevented).toBe(true);
    http
      .expectOne('/api/external/applications/APP-0001/documents')
      .flush([{ docId: 'DOC-1', appId: 'APP-0001', documentType: 'LETTER_A', createdAt: '2026-01-15T09:00:00Z' }]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('DOC-1');
  });

  it('says when nothing is visible', async () => {
    const fixture = TestBed.createComponent(ExternalViewPage);
    await fixture.whenStable();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    http.expectOne('/api/external/applications/APP-0001/documents').flush([]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('No documents are visible to external users for APP-0001.');
  });
});
