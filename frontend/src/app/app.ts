import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterOutlet } from '@angular/router';

import { DevUser, DevUserName } from './auth/dev-user';

@Component({
  imports: [RouterOutlet, MatChipsModule, MatFormFieldModule, MatIconModule, MatSelectModule, MatToolbarModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly devUser = inject(DevUser);
  private readonly router = inject(Router);

  /** Admin and staff land on the runs page; external users on the external view. */
  protected switchUser(name: DevUserName): void {
    this.devUser.name.set(name);
    this.router.navigateByUrl(this.devUser.isExternal() ? '/external' : '/');
  }
}
