package com.tricv.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name="evaluations")
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "candidat_id")
    private Candidat candidat;

    private String domaine;
    private int score;

    @Column(columnDefinition = "TEXT")
    private String raison;

    @Column(columnDefinition = "TEXT")
    private String pointsForts;

    @Column(columnDefinition = "TEXT")
    private String pointsFaibles;

    // --- Nouveaux champs : detail des scores par critere (7 criteres, total 100) ---
    private Integer scoreTechnique;
    private Integer scoreExperience;
    private Integer scoreAcademique;
    private Integer scorePfe;
    private Integer scoreLangues;
    private Integer scoreSoftskills;
    private Integer scoreCertifs;
    private String profil;

    // Maximums par critere REELLEMENT utilises pour cette evaluation (varient selon le profil :
    // cf. IAService.maxScoresPourProfil). Necessaires pour afficher correctement chaque sous-score
    // (ex. "5/5" pour un stagiaire, pas "5/20") puisque les maximums ne sont plus fixes.
    private Integer maxTechnique;
    private Integer maxExperience;
    private Integer maxAcademique;
    private Integer maxPfe;
    private Integer maxLangues;
    private Integer maxSoftskills;
    private Integer maxCertifs;

    // Compétences prioritaires utilisées lors de cette évaluation, normalisées
    // (minuscules, triées, jointes par des virgules ; chaîne vide si aucune).
    // Fait partie de la clé de cache : cf. EvaluationService.rechercherParDomaine.
    @Column(columnDefinition = "TEXT")
    private String competencesPrioritaires;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidat getCandidat() { return candidat; }
    public void setCandidat(Candidat candidat) { this.candidat = candidat; }

    public String getDomaine() { return domaine; }
    public void setDomaine(String domaine) { this.domaine = domaine; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getRaison() { return raison; }
    public void setRaison(String raison) { this.raison = raison; }

    public String getPointsForts() { return pointsForts; }
    public void setPointsForts(String pointsForts) { this.pointsForts = pointsForts; }

    public String getPointsFaibles() { return pointsFaibles; }
    public void setPointsFaibles(String pointsFaibles) { this.pointsFaibles = pointsFaibles; }

    // --- Getters/setters des 7 sous-scores ---
    public Integer getScoreTechnique() { return scoreTechnique; }
    public void setScoreTechnique(Integer scoreTechnique) { this.scoreTechnique = scoreTechnique; }

    public Integer getScoreExperience() { return scoreExperience; }
    public void setScoreExperience(Integer scoreExperience) { this.scoreExperience = scoreExperience; }

    public Integer getScoreAcademique() { return scoreAcademique; }
    public void setScoreAcademique(Integer scoreAcademique) { this.scoreAcademique = scoreAcademique; }

    public Integer getScorePfe() { return scorePfe; }
    public void setScorePfe(Integer scorePfe) { this.scorePfe = scorePfe; }

    public Integer getScoreLangues() { return scoreLangues; }
    public void setScoreLangues(Integer scoreLangues) { this.scoreLangues = scoreLangues; }

    public Integer getScoreSoftskills() { return scoreSoftskills; }
    public void setScoreSoftskills(Integer scoreSoftskills) { this.scoreSoftskills = scoreSoftskills; }

    public Integer getScoreCertifs() { return scoreCertifs; }
    public void setScoreCertifs(Integer scoreCertifs) { this.scoreCertifs = scoreCertifs; }

    public String getProfil() { return profil; }
    public void setProfil(String profil) { this.profil = profil; }

    public String getCompetencesPrioritaires() { return competencesPrioritaires; }
    public void setCompetencesPrioritaires(String competencesPrioritaires) { this.competencesPrioritaires = competencesPrioritaires; }

    // --- Getters/setters des 7 maximums (dependants du profil) ---
    public Integer getMaxTechnique() { return maxTechnique; }
    public void setMaxTechnique(Integer maxTechnique) { this.maxTechnique = maxTechnique; }

    public Integer getMaxExperience() { return maxExperience; }
    public void setMaxExperience(Integer maxExperience) { this.maxExperience = maxExperience; }

    public Integer getMaxAcademique() { return maxAcademique; }
    public void setMaxAcademique(Integer maxAcademique) { this.maxAcademique = maxAcademique; }

    public Integer getMaxPfe() { return maxPfe; }
    public void setMaxPfe(Integer maxPfe) { this.maxPfe = maxPfe; }

    public Integer getMaxLangues() { return maxLangues; }
    public void setMaxLangues(Integer maxLangues) { this.maxLangues = maxLangues; }

    public Integer getMaxSoftskills() { return maxSoftskills; }
    public void setMaxSoftskills(Integer maxSoftskills) { this.maxSoftskills = maxSoftskills; }

    public Integer getMaxCertifs() { return maxCertifs; }
    public void setMaxCertifs(Integer maxCertifs) { this.maxCertifs = maxCertifs; }
}