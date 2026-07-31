package com.tricv.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricv.backend.model.Candidat;
import com.tricv.backend.model.Evaluation;
import com.tricv.backend.repository.CandidatRepository;
import com.tricv.backend.repository.EvaluationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EvaluationService {

    @Autowired
    private CandidatRepository candidatRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private IAService iaService;

    private static final int MAX_CANDIDATS_EVALUES = 10;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Evaluation> rechercherParDomaine(String domaine, String modeProfil) {
        return rechercherParDomaine(domaine, modeProfil, null);
    }

    public List<Evaluation> rechercherParDomaine(String domaine, String modeProfil, String competencesPrioritaires) {
        List<String> comps = new ArrayList<>();
        if (competencesPrioritaires != null && !competencesPrioritaires.trim().isEmpty()) {
            String[] parts = competencesPrioritaires.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    comps.add(trimmed);
                }
            }
        }
        // Forme normalisee (minuscules, triee, jointe par virgules) utilisee comme cle de cache :
        // ainsi "Spring Boot, Hibernate" et "hibernate,spring boot" sont considerees identiques.
        String compsNormalisees = normaliserCompetences(comps);

        List<Candidat> tousLesCandidats = candidatRepository.findAll();

        // Étape 1 : pré-sélection par mots-clés (mot entier, pas sous-chaîne)
        String[] motsCles = domaine.toLowerCase().split("\\s+");
        List<Candidat> preselectionnes = tousLesCandidats.stream()
            .filter(c -> {
                if (c.getTexteCV() == null) return false;
                String texte = c.getTexteCV().toLowerCase();
                for (String mot : motsCles) {
                    if (texte.matches("(?s).*\\b" + java.util.regex.Pattern.quote(mot) + "\\b.*")) {
                        return true;
                    }
                }
                return false;
            })
            .limit(MAX_CANDIDATS_EVALUES)
            .collect(Collectors.toList());

        // Étape 2 : vérifier le cache par candidat + domaine + profil résolu + compétences prioritaires (normalisées).
        // Un changement de compétences prioritaires modifie donc la clé de cache et déclenche une réévaluation.
        List<Evaluation> dejaEvalues = new ArrayList<>();
        List<Evaluation> nouvelles = new ArrayList<>();
        for (Candidat candidat : preselectionnes) {
            String profilResolu = iaService.resoudreProfil(candidat.getTexteCV(), modeProfil);
            Optional<Evaluation> existante = evaluationRepository
                    .findByCandidatIdAndDomaineIgnoreCaseAndProfilIgnoreCaseAndCompetencesPrioritaires(
                            candidat.getId(), domaine, profilResolu, compsNormalisees);

            if (existante.isPresent()) {
                dejaEvalues.add(existante.get());
                continue;
            }

            Evaluation e = evaluerEtSauvegarder(candidat, domaine, profilResolu, comps, compsNormalisees);
            if (e != null) {          // on ignore les echecs
                nouvelles.add(e);
            }

            // Petite pause entre chaque candidat évalué par l'IA pour respecter la limite de Groq
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        // Étape 3 : fusionner et trier par score
        List<Evaluation> tous = new ArrayList<>();
        tous.addAll(dejaEvalues);
        tous.addAll(nouvelles);
        tous.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        return tous;
    }

    // Normalise une liste de compétences pour en faire une clé de cache stable :
    // minuscules + trim + tri alphabétique + jointure par virgules (liste vide -> "").
    private String normaliserCompetences(List<String> competences) {
        if (competences == null || competences.isEmpty()) {
            return "";
        }
        return competences.stream()
                .map(c -> c.trim().toLowerCase())
                .filter(c -> !c.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Evaluation evaluerEtSauvegarder(Candidat candidat, String domaine, String profilResolu,
                                             List<String> competencesPrioritaires, String competencesNormalisees) {
        String reponseIA = iaService.analyserCV(candidat.getTexteCV(), domaine, profilResolu, competencesPrioritaires);

        // Maximums par critere pour ce profil (meme source que celle utilisee pour construire le
        // prompt, cf. IAService.maxScoresPourProfil) : sert a la fois a clamper les sous-scores
        // renvoyes par l'IA et a les stocker pour un affichage correct cote frontend.
        Map<String, Integer> max = iaService.maxScoresPourProfil(profilResolu);

        Evaluation evaluation = new Evaluation();
        evaluation.setCandidat(candidat);
        evaluation.setDomaine(domaine);
        evaluation.setProfil(profilResolu);
        evaluation.setCompetencesPrioritaires(competencesNormalisees);
        evaluation.setMaxTechnique(max.get("technique"));
        evaluation.setMaxExperience(max.get("experience"));
        evaluation.setMaxAcademique(max.get("academique"));
        evaluation.setMaxPfe(max.get("pfe"));
        evaluation.setMaxLangues(max.get("langues"));
        evaluation.setMaxSoftskills(max.get("softskills"));
        evaluation.setMaxCertifs(max.get("certifs"));

        try {
            JsonNode root = objectMapper.readTree(reponseIA);

            evaluation.setScore(root.path("score").asInt(0));
            evaluation.setRaison(root.path("raison").asText(""));
            evaluation.setPointsForts(root.path("pointsForts").asText(""));
            evaluation.setPointsFaibles(root.path("pointsFaibles").asText(""));
            evaluation.setProfil(profilResolu);

            JsonNode d = root.path("detailScores");

            // --- SECURITE : Clamper chaque sous-score (7 critères) à son maximum, PROPRE AU PROFIL ---
            int technique = Math.max(Math.min(d.path("technique").asInt(0), max.get("technique")), 0);
            int experience = Math.max(Math.min(d.path("experience").asInt(0), max.get("experience")), 0);
            int academique = Math.max(Math.min(d.path("academique").asInt(0), max.get("academique")), 0);
            int pfe = Math.max(Math.min(d.path("pfe").asInt(0), max.get("pfe")), 0);
            int langues = Math.max(Math.min(d.path("langues").asInt(0), max.get("langues")), 0);
            int softskills = Math.max(Math.min(d.path("softskills").asInt(0), max.get("softskills")), 0);
            int certifs = Math.max(Math.min(d.path("certifs").asInt(0), max.get("certifs")), 0);

            evaluation.setScoreTechnique(technique);
            evaluation.setScoreExperience(experience);
            evaluation.setScoreAcademique(academique);
            evaluation.setScorePfe(pfe);
            evaluation.setScoreLangues(langues);
            evaluation.setScoreSoftskills(softskills);
            evaluation.setScoreCertifs(certifs);

            // Recalculer le score global comme somme des 7 sous-scores
            int scoreGlobal = technique + experience + academique + pfe + langues + softskills + certifs;
            evaluation.setScore(scoreGlobal);

        } catch (Exception e) {
            // En cas d'echec (ex: 429, reponse invalide), on ne sauvegarde pas
            // un candidat a 0 qui polluerait les resultats. On le signale et on l'ignore.
            System.err.println("Echec evaluation pour " + candidat.getNom() + " : " + e.getMessage());
            return null;
        }

        return evaluationRepository.save(evaluation);
    }
    public void viderToutesLesEvaluations() {
        evaluationRepository.deleteAll();
    }
}