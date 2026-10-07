import { Routes } from '@angular/router';

import { AdvisorComponent } from './advisor/advisor.component';
import { AlertsComponent } from './alerts/alerts.component';
import { authGuard, guestGuard } from './auth/auth.guards';
import { BlotterComponent } from './blotter/blotter.component';
import { HomeComponent } from './home/home.component';
import { NotificationsComponent } from './notifications/notifications.component';
import { OrdersComponent } from './orders/orders.component';
import { PortfolioComponent } from './portfolio/portfolio.component';
import { ProfileComponent } from './profile/profile.component';
import { SignInComponent } from './sign-in/sign-in.component';
import { WatchlistComponent } from './watchlist/watchlist.component';

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
      { path: 'portfolio', component: PortfolioComponent },
      { path: 'orders', component: OrdersComponent },
      { path: 'blotter', component: BlotterComponent },
      { path: 'watchlist', component: WatchlistComponent },
      { path: 'alerts', component: AlertsComponent },
      { path: 'advisor', component: AdvisorComponent },
      { path: 'notifications', component: NotificationsComponent },
      { path: 'profile', component: ProfileComponent },
      { path: '**', redirectTo: '' }
    ]
  }
];
