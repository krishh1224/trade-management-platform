import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type AppTheme = 'dark' | 'light';

const THEME_STORAGE_KEY = 'trade-ui.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private initialized = false;

  readonly theme = signal<AppTheme>('dark');

  initialize(): void {
    if (this.initialized) {
      return;
    }

    const stored = this.readStoredTheme();
    this.applyTheme(stored ?? 'dark');
    this.initialized = true;
  }

  toggleTheme(): void {
    const nextTheme: AppTheme = this.theme() === 'dark' ? 'light' : 'dark';
    this.applyTheme(nextTheme);
  }

  private applyTheme(theme: AppTheme): void {
    this.theme.set(theme);
    this.document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }

  private readStoredTheme(): AppTheme | null {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === 'dark' || stored === 'light' ? stored : null;
  }
}
