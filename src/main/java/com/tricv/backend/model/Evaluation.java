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
    private Integer scoreTechnique;        // max 25
    private Integer scoreExperience;       // max 20
    private Integer scoreAcademique;       // max 15
    private Integer scorePfe;              // max 15
    private Integer scoreLangues;          // max 8
    private Integer scoreSoftskills;       // max 8
    private Integer scoreCertifs;          // max 9
    private String profil;

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
}