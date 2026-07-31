import { Component, ChangeDetectorRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CandidatService } from '../../services/candidat';

@Component({
  standalone: true,
  selector: 'app-recherche',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './recherche.html',
  styleUrl: './recherche.css'
})
export class RechercheComponent {

  domaine: string = '';
  modeProfil: string = 'auto';        // 'auto' | 'debutant' | 'experimente' | 'stagiaire'
  competencesPrioritaires: string[] = [];
  categoriesSuggerees: { nom: string; sousCompetences: string[] }[] = [];
  categorieOuverte: string | null = null;
  chargementSuggestions: boolean = false;
  filtresVisibles: boolean = false;

  evaluations: any[] = [];
  rechercheLancee: boolean = false;
  chargement: boolean = false;
  candidatSelectionne: any = null;
  isScrolled: boolean = false;

  scoreMinimum: number = 0;           // filtre : score minimum a afficher
  critereTri: string = 'score';
  compteurNombreCandidats: number = 0;
  compteurScoreMoyen: number = 0;
  compteurMeilleurScore: number = 0;
  private animationStatsFrame: number | null = null;

  constructor(
    private candidatService: CandidatService,
    private cdr: ChangeDetectorRef
  ) {}

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.isScrolled = window.scrollY > 8;
  }

  rechercher() {
    if (!this.domaine.trim()) {
      return;
    }

    this.chargement = true;
    this.rechercheLancee = true;
    this.evaluations = [];
    this.scoreMinimum = 0;            // on remet le filtre a zero a chaque recherche
    this.annulerAnimationStats();
    this.compteurNombreCandidats = 0;
    this.compteurScoreMoyen = 0;
    this.compteurMeilleurScore = 0;

    this.candidatService.rechercherParDomaine(this.domaine, this.modeProfil, this.competencesPrioritaires).subscribe({
      next: (data: any[]) => {
        this.evaluations = data;
        this.demarrerAnimationStats(this.calculerStats(this.evaluationsFiltrees));
        this.chargement = false;
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        console.error('DEBUG erreur:', err);
        this.chargement = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ===== FILTRE : liste des candidats au-dessus du score minimum =====
  get evaluationsFiltrees(): any[] {
    const liste = this.evaluations.filter(e => e.score >= this.scoreMinimum);

    switch (this.critereTri) {
      case 'scoreAcademique':
        return [...liste].sort((a, b) => (b.scoreAcademique ?? 0) - (a.scoreAcademique ?? 0));
      case 'scoreExperience':
        return [...liste].sort((a, b) => (b.scoreExperience ?? 0) - (a.scoreExperience ?? 0));
      case 'scorePfe':
        return [...liste].sort((a, b) => (b.scorePfe ?? 0) - (a.scorePfe ?? 0));
      case 'nom':
        return [...liste].sort((a, b) => (a.candidat?.nom || '').localeCompare(b.candidat?.nom || '', 'fr', { sensitivity: 'base' }));
      case 'score':
      default:
        return [...liste].sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
    }
  }

  get candidatsMasquesParFiltre(): number {
    return Math.max(0, this.evaluations.length - this.evaluationsFiltrees.length);
  }

  // ===== STATISTIQUES =====
  get nombreCandidats(): number {
    return this.compteurNombreCandidats;
  }

  get scoreMoyen(): number {
    return this.compteurScoreMoyen;
  }

  get meilleurScore(): number {
    return this.compteurMeilleurScore;
  }

  get topCandidats(): any[] {
    return this.evaluationsFiltrees.slice(0, 5).map((evaluation, index) => ({
      nom: evaluation?.candidat?.nom || `Candidat ${index + 1}`,
      score: evaluation?.score ?? 0,
      pourcentage: this.evaluationsFiltrees.length > 0 ? Math.max(8, ((evaluation?.score ?? 0) / Math.max(...this.evaluationsFiltrees.map(e => e.score ?? 0), 1)) * 100) : 0
    }));
  }

  get repartitionProfils(): any[] {
    const map = new Map<string, number>();
    this.evaluationsFiltrees.forEach(evaluation => {
      const profil = evaluation?.profil || 'inconnu';
      map.set(profil, (map.get(profil) || 0) + 1);
    });

    return Array.from(map.entries())
      .map(([profil, count]) => ({
        label: this.libelleProfil(profil),
        count
      }))
      .sort((a, b) => b.count - a.count);
  }

  get libelleQualiteVivier(): string {
    if (this.scoreMoyen >= 80) return 'Vivier excellent';
    if (this.scoreMoyen >= 60) return 'Vivier correct';
    return 'Vivier limité';
  }

  get classeQualiteVivier(): string {
    if (this.scoreMoyen >= 80) return 'qualite-haut';
    if (this.scoreMoyen >= 60) return 'qualite-moyen';
    return 'qualite-bas';
  }

  private calculerStats(liste: any[]): { nombre: number; scoreMoyen: number; meilleurScore: number } {
    if (liste.length === 0) {
      return { nombre: 0, scoreMoyen: 0, meilleurScore: 0 };
    }

    const somme = liste.reduce((total, e) => total + (e.score || 0), 0);
    return {
      nombre: liste.length,
      scoreMoyen: Math.round(somme / liste.length),
      meilleurScore: Math.max(...liste.map(e => e.score || 0))
    };
  }

  private demarrerAnimationStats(cibles: { nombre: number; scoreMoyen: number; meilleurScore: number }) {
    this.annulerAnimationStats();

    const start = performance.now();
    const animer = (timestamp: number) => {
      const elapsed = timestamp - start;
      const progress = Math.min(1, elapsed / 1000);
      const ease = 1 - Math.pow(1 - progress, 3);

      this.compteurNombreCandidats = Math.round(cibles.nombre * ease);
      this.compteurScoreMoyen = Math.round(cibles.scoreMoyen * ease);
      this.compteurMeilleurScore = Math.round(cibles.meilleurScore * ease);
      this.cdr.detectChanges();

      if (progress < 1) {
        this.animationStatsFrame = requestAnimationFrame(animer);
      }
    };

    this.animationStatsFrame = requestAnimationFrame(animer);
  }

  private annulerAnimationStats() {
    if (this.animationStatsFrame != null) {
      cancelAnimationFrame(this.animationStatsFrame);
    }
    this.animationStatsFrame = null;
  }

  // ===== COMPETENCES PRIORITAIRES =====
  chargerSuggestions(): void {
    if (!this.domaine.trim() || this.chargementSuggestions) {
      return;
    }

    this.chargementSuggestions = true;
    this.categoriesSuggerees = [];
    this.categorieOuverte = null;

    this.candidatService.suggererCompetences(this.domaine).subscribe({
      next: (data: any) => {
        this.categoriesSuggerees = Array.isArray(data?.categories) ? data.categories : [];
        this.chargementSuggestions = false;
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        console.error('Erreur suggestion competences:', err);
        this.chargementSuggestions = false;
        this.cdr.detectChanges();
      }
    });
  }

  // Accordeon uniquement : ouvre/replie les sous-competences d'une categorie, sans toucher aux priorites.
  ouvrirCategorie(nom: string): void {
    this.categorieOuverte = this.categorieOuverte === nom ? null : nom;
  }

  // Selection uniquement : ajoute/retire une priorite (categorie entiere OU sous-competence precise).
  // Exclusivite par categorie : la categorie et ses codes sous-competences ne sont jamais
  // selectionnees en meme temps.
  // - Selectionner la categorie ENTIERE retire toutes ses sous-competences deja cochees
  //   (on repasse en mode "categorie globale").
  // - Selectionner une SOUS-COMPETENCE retire la categorie parente si elle etait cochee
  //   (on passe en mode "detaille" pour cette categorie).
  // `categorie` est fourni par le template (celle du tag cliqué, qu'il s'agisse du nom de la
  // categorie elle-meme ou d'une de ses sous-competences) pour retrouver la categorie parente
  // sans avoir a la re-chercher dans categoriesSuggerees.
  toggleCompetence(comp: string, categorie: { nom: string; sousCompetences: string[] }): void {
    if (this.competencesPrioritaires.includes(comp)) {
      this.retirerCompetence(comp);
      return;
    }

    this.ajouterCompetence(comp);

    const estCategorie = comp === categorie.nom;
    if (estCategorie) {
      categorie.sousCompetences.forEach(sous => this.retirerCompetence(sous));
    } else {
      this.retirerCompetence(categorie.nom);
    }
  }

  private ajouterCompetence(comp: string): void {
    if (!this.competencesPrioritaires.includes(comp)) {
      this.competencesPrioritaires.push(comp);
    }
  }

  private retirerCompetence(comp: string): void {
    const index = this.competencesPrioritaires.indexOf(comp);
    if (index >= 0) {
      this.competencesPrioritaires.splice(index, 1);
    }
  }

  isCompetenceSelectionnee(comp: string): boolean {
    return this.competencesPrioritaires.includes(comp);
  }

  toggleFiltres(): void {
    this.filtresVisibles = !this.filtresVisibles;
  }

  // Initiales (1 a 2 lettres) pour l'avatar d'un candidat, ex: "Jean Dupont" -> "JD"
  initiales(nom: string | undefined): string {
    if (!nom) return '?';
    const lettres = nom.trim().split(/\s+/).slice(0, 2).map(mot => mot.charAt(0).toUpperCase());
    return lettres.join('') || '?';
  }

  // ===== EXPORT CSV =====
  exporterCSV() {
    const liste = this.evaluationsFiltrees;
    if (liste.length === 0) return;

    // En-tetes des colonnes
    const entetes = ['Rang', 'Nom', 'Score', 'Profil', 'Technique', 'Expérience',
                     'Académique', 'PFE', 'Langues', 'Soft skills', 'Certifs', 'Raison'];

    // Lignes de donnees
    const lignes = liste.map((e, i) => {
      return [
        i + 1,
        this.nettoyerCSV(e.candidat?.nom),
        e.score,
        this.nettoyerCSV(this.libelleProfil(e.profil)),
        e.scoreTechnique ?? '',
        e.scoreExperience ?? '',
        e.scoreAcademique ?? '',
        e.scorePfe ?? '',
        e.scoreLangues ?? '',
        e.scoreSoftskills ?? '',
        e.scoreCertifs ?? '',
        this.nettoyerCSV(e.raison)
      ].join(';');   // point-virgule : Excel francais le reconnait mieux
    });

    // Assemblage du contenu CSV
    const contenu = [entetes.join(';'), ...lignes].join('\n');

    // BOM pour que les accents s'affichent bien dans Excel
    const blob = new Blob(['\uFEFF' + contenu], { type: 'text/csv;charset=utf-8;' });

    // Telechargement
    const url = URL.createObjectURL(blob);
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = `candidats_${this.domaine}_${new Date().toISOString().slice(0, 10)}.csv`;
    lien.click();
    URL.revokeObjectURL(url);
  }

  // Nettoie une valeur texte pour le CSV (enleve les ; et retours a la ligne)
  private nettoyerCSV(valeur: string): string {
    if (!valeur) return '';
    return valeur.replace(/;/g, ',').replace(/\n/g, ' ').replace(/\r/g, ' ');
  }

  classeScore(score: number): string {
    if (score >= 80) return 'score-haut';
    if (score >= 60) return 'score-moyen';
    return 'score-bas';
  }

  libelleProfil(p: string): string {
    if (p === 'debutant') return 'Jeune diplômé';
    if (p === 'experimente') return 'Expérimenté';
    if (p === 'stagiaire') return 'Stagiaire / PFE';
    return '—';
  }

  // ===== SOUS-SCORES : barres de progression a maximums variables (selon le profil) =====
  // Les 7 criteres avec leur libelle lisible ; "cle" sert a retrouver scoreXxx/maxXxx sur l'evaluation
  // (ex. cle "technique" -> scoreTechnique / maxTechnique), evite de dupliquer le gabarit 7 fois au template.
  readonly criteresScores: { cle: string; label: string }[] = [
    { cle: 'technique', label: 'Technique' },
    { cle: 'experience', label: 'Expérience' },
    { cle: 'academique', label: 'Académique' },
    { cle: 'pfe', label: 'PFE' },
    { cle: 'langues', label: 'Langues' },
    { cle: 'softskills', label: 'Soft skills' },
    { cle: 'certifs', label: 'Certifications' }
  ];

  private capitaliser(mot: string): string {
    return mot.charAt(0).toUpperCase() + mot.slice(1);
  }

  valeurCritere(evaluation: any, cle: string): number {
    return evaluation?.['score' + this.capitaliser(cle)] ?? 0;
  }

  maxCritere(evaluation: any, cle: string): number {
    return evaluation?.['max' + this.capitaliser(cle)] ?? 0;
  }

  // Pourcentage arrondi (valeur / max * 100). Retourne 0 si max absent/0 (ancienne evaluation en
  // cache sans maxScores) — le template masque alors la ligne plutot que d'afficher un pourcentage trompeur.
  pourcentageCritere(evaluation: any, cle: string): number {
    const max = this.maxCritere(evaluation, cle);
    if (!max || max <= 0) {
      return 0;
    }
    return Math.round((this.valeurCritere(evaluation, cle) / max) * 100);
  }

  reinitialiser() {
    this.domaine = '';
    this.scoreMinimum = 0;
    this.critereTri = 'score';
    this.evaluations = [];
    this.rechercheLancee = false;
    this.chargement = false;
    this.candidatSelectionne = null;
    this.categoriesSuggerees = [];
    this.categorieOuverte = null;
    this.competencesPrioritaires = [];
    this.chargementSuggestions = false;
    this.filtresVisibles = false;
    this.annulerAnimationStats();
    this.compteurNombreCandidats = 0;
    this.compteurScoreMoyen = 0;
    this.compteurMeilleurScore = 0;
    this.cdr.detectChanges();
  }

  voirCV(evaluation: any) {
    this.candidatSelectionne = {
      ...evaluation,
      nom: evaluation?.candidat?.nom,
      texteCV: evaluation?.candidat?.texteCV
    };
  }

  fermerCV() {
    this.candidatSelectionne = null;
  }
}