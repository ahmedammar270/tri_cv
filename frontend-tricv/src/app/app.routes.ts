import { Routes } from '@angular/router';
import { RechercheComponent } from './components/recherche/recherche';
import { CandidatsComponent } from './components/candidats/candidats';
import { AjouterComponent } from './components/ajouter/ajouter';

export const routes: Routes = [
  { path: '', component: RechercheComponent },
  { path: 'candidats', component: CandidatsComponent },
  { path: 'ajouter', component: AjouterComponent }
];