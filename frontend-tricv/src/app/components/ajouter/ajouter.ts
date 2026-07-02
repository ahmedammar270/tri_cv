import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CandidatService } from '../../services/candidat';

@Component({
  standalone: true,
  selector: 'app-ajouter',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ajouter.html',
  styleUrl: './ajouter.css'
})
export class AjouterComponent {

  fichiersSelectionnes: File[] = [];
  message: string = '';
  chargement: boolean = false;
  progression: string = '';

  private readonly TAILLE_LOT = 50;

  constructor(
    private candidatService: CandidatService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  onFichiersSelectionnes(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.fichiersSelectionnes = Array.from(input.files);
    }
  }

  async soumettre() {
    if (this.fichiersSelectionnes.length === 0) {
      this.message = '⚠️ Choisissez au moins un fichier PDF !';
      return;
    }

    this.chargement = true;
    this.message = '';

    const lots: File[][] = [];
    for (let i = 0; i < this.fichiersSelectionnes.length; i += this.TAILLE_LOT) {
      lots.push(this.fichiersSelectionnes.slice(i, i + this.TAILLE_LOT));
    }

    let totalAjoutes = 0;
    let totalErreurs = 0;

    for (let i = 0; i < lots.length; i++) {
      this.progression = `⏳ Lot ${i + 1}/${lots.length} (${totalAjoutes} ajoutés jusqu'ici)...`;
      this.cdr.detectChanges();

      try {
        const resultat = await this.candidatService.ajouterCandidatsPdf(lots[i]).toPromise();
        totalAjoutes += (resultat?.length ?? 0);
      } catch (err) {
        console.error(`Erreur lot ${i + 1}:`, err);
        totalErreurs += lots[i].length;
      }
    }

    this.chargement = false;
    this.progression = '';

    if (totalErreurs === 0) {
      this.message = `✅ ${totalAjoutes} CV(s) ajouté(s) avec succès !`;
    } else {
      this.message = `⚠️ ${totalAjoutes} ajouté(s), ${totalErreurs} en erreur.`;
    }

    this.fichiersSelectionnes = [];
    this.cdr.detectChanges();

    setTimeout(() => {
      this.router.navigate(['/candidats']);
    }, 2000);
  }
}