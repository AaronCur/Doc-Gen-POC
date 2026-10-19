import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

import { DevUser } from './auth/dev-user';
import { ExternalViewPage } from './external-view/external-view-page';
import { LetterGenerationPage } from './letter-generation/letter-generation-page';

// These guards only route people to the page that fits their role. Authorisation is enforced
// by case-service on every request.
const staffOnly: CanActivateFn = () =>
  inject(DevUser).isStaff() || inject(Router).parseUrl('/external');

const externalOnly: CanActivateFn = () =>
  inject(DevUser).isExternal() || inject(Router).parseUrl('/');

export const routes: Routes = [
  { path: '', component: LetterGenerationPage, canActivate: [staffOnly], title: 'Letter generation' },
  { path: 'external', component: ExternalViewPage, canActivate: [externalOnly], title: 'External view' },
  { path: '**', redirectTo: '' },
];
