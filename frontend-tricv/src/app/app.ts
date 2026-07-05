import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <div class="brand">
        <span class="brand-icon" aria-hidden="true">TC</span>
        <span class="brand-text">
          <span class="brand-name">Tri des CVs</span>
          <span class="brand-sub">Recrutement augmenté par l'IA</span>
        </span>
      </div>
      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">🔍 Rechercher</a>
        <a routerLink="/candidats" routerLinkActive="active">👥 Candidats</a>
        <a routerLink="/ajouter" routerLinkActive="active" class="nav-add">+ Ajouter</a>
      </nav>
    </header>
    <main>
      <router-outlet></router-outlet>
    </main>
  `,
  styleUrl: './app.css'
})
export class AppComponent {}