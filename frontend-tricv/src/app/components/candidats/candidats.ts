import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CandidatService } from '../../services/candidat';

@Component({
  standalone: true,
  selector: 'app-candidats',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './candidats.html',
  styleUrl: './candidats.css'
})
export class CandidatsComponent implements OnInit {

  candidatsTous: any[] = [];
  candidats: any[] = [];
  filtre: string = '';
  candidatSelectionne: any = null;
  candidatASupprimer: any = null;

  constructor(
    private candidatService: CandidatService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.chargerTous();
  }

  chargerTous() {
    this.candidatService.getCandidats().subscribe({
      next: (data: any[]) => {
        this.candidatsTous = data;
        this.appliquerFiltre();
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Erreur:', err)
    });
  }

  appliquerFiltre() {
    const f = this.filtre.trim().toLowerCase();
    if (!f) {
      this.candidats = this.candidatsTous;
      return;
    }
    this.candidats = this.candidatsTous.filter(c =>
      (c.nom ?? '').toLowerCase().includes(f) || String(c.id) === f
    );
  }

  reinitialiserFiltre() {
    this.filtre = '';
    this.appliquerFiltre();
  }

  demanderSuppression(candidat: any) {
    this.candidatASupprimer = candidat;
  }

  annulerSuppression() {
    this.candidatASupprimer = null;
  }

  confirmerSuppression() {
    if (!this.candidatASupprimer) return;
    const id = this.candidatASupprimer.id;

    this.candidatService.supprimerCandidat(id).subscribe({
      next: () => {
        this.candidatsTous = this.candidatsTous.filter(c => c.id !== id);
        this.appliquerFiltre();
        this.candidatASupprimer = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Erreur suppression:', err);
        this.candidatASupprimer = null;
        this.cdr.detectChanges();
      }
    });
  }

  voirCV(candidat: any) {
    this.candidatSelectionne = candidat;
  }

  fermerCV() {
    this.candidatSelectionne = null;
  }
}