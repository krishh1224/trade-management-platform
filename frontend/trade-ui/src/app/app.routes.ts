import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './auth/auth.guards';
import { HomeComponent } from './home/home.component';
import { SignInComponent } from './sign-in/sign-in.component';

export const routes: Routes = [
	{
		path: '',
		component: HomeComponent,
		canActivate: [authGuard]
	},
	{
		path: 'sign-in',
		component: SignInComponent,
		canActivate: [guestGuard]
	},
	{
		path: '**',
		redirectTo: ''
	}
];
