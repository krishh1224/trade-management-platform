import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './auth/auth.guards';
import { BlotterComponent } from './blotter/blotter.component';
import { HomeComponent } from './home/home.component';
import { OrdersComponent } from './orders/orders.component';
import { ProfileComponent } from './profile/profile.component';
import { SignInComponent } from './sign-in/sign-in.component';

export const routes: Routes = [
  {
    path: 'sign-in',
    component: SignInComponent,
    canActivate: [guestGuard]
  },
  {
    // Every route except sign-in lives under this guarded parent. Add new routes as children.
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', component: HomeComponent },
      { path: 'orders', component: OrdersComponent },
      { path: 'blotter', component: BlotterComponent },
      { path: 'profile', component: ProfileComponent },
      { path: '**', redirectTo: '' }
    ]
  }
];
