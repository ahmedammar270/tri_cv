import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class CandidatService {

  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getCandidats(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/candidats`);
  }

  rechercherParNom(nom: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/candidats/recherche`, {
      params: { nom }
    });
  }

  ajouterCandidatsPdf(fichiers: File[]): Observable<any[]> {
    const formData = new FormData();
    fichiers.forEach(f => formData.append('fichiers', f));
    return this.http.post<any[]>(`${this.apiUrl}/candidats/ajouter-pdf`, formData);
  }

  supprimerCandidat(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/candidats/${id}`);
  }

  rechercherParDomaine(domaine: string, modeProfil: string = 'auto', competencesPrioritaires: string[] = []): Observable<any[]> {
    const compsStr = competencesPrioritaires.length > 0 ? competencesPrioritaires.join(',') : '';
    const params: any = { domaine, modeProfil };
    if (compsStr) {
      params.competencesPrioritaires = compsStr;
    }
    return this.http.get<any[]>(`${this.apiUrl}/evaluations/recherche`, { params });
  }

  // Reponse attendue : { categories: [ { nom: string, sousCompetences: string[] } ] }
  suggererCompetences(domaine: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/competences/suggerer`, {
      params: { domaine }
    });
  }
}