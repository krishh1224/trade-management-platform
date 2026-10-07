import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { AdvisorChatWidgetComponent } from './advisor/advisor-chat-widget.component';
import { BrowserPushService } from './notifications/browser-push.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AdvisorChatWidgetComponent],
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
