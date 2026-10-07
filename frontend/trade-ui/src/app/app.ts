import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { BrowserPushService } from './notifications/browser-push.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor() {
    const router = inject(Router);
    // a clicked push pop-up opens the inbox in this tab
    inject(BrowserPushService).listenForPopUpClicks((path) => void router.navigateByUrl(path));
  }
}
