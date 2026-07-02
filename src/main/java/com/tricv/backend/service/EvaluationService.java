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
                comps.add(part.trim());
            }
        }

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

        // Étape 2 : vérifier le cache par candidat + domaine + profil résolu
        // Note : competencesPrioritaires n'entre PAS dans la clé de cache (simplicité) ;
        // deux recherches avec des priorités différentes peuvent donc réutiliser la même évaluation en cache.
        List<Evaluation> dejaEvalues = new ArrayList<>();
        List<Evaluation> nouvelles = new ArrayList<>();
        for (Candidat candidat : preselectionnes) {
            String profilResolu = iaService.resoudreProfil(candidat.getTexteCV(), modeProfil);
            Optional<Evaluation> existante = evaluationRepository
                    .findByCandidatIdAndDomaineIgnoreCaseAndProfilIgnoreCase(candidat.getId(), domaine, profilResolu);

            if (existante.isPresent()) {
                dejaEvalues.add(existante.get());
                continue;
            }

            Evaluation e = evaluerEtSauvegarder(candidat, domaine, profilResolu, comps);
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

    private Evaluation evaluerEtSauvegarder(Candidat candidat, String domaine, String profilResolu, List<String> competencesPrioritaires) {
        String reponseIA = iaService.analyserCV(candidat.getTexteCV(), domaine, profilResolu, competencesPrioritaires);

        Evaluation evaluation = new Evaluation();
        evaluation.setCandidat(candidat);
        evaluation.setDomaine(domaine);
        evaluation.setProfil(profilResolu);

        try {
            JsonNode root = objectMapper.readTree(reponseIA);

            evaluation.setScore(root.path("score").asInt(0));
            evaluation.setRaison(root.path("raison").asText(""));
            evaluation.setPointsForts(root.path("pointsForts").asText(""));
            evaluation.setPointsFaibles(root.path("pointsFaibles").asText(""));
            evaluation.setProfil(profilResolu);

            JsonNode d = root.path("detailScores");
            
            // --- SECURITE : Clamper chaque sous-score (7 critères) à son maximum ---
            int technique = Math.max(Math.min(d.path("technique").asInt(0), 25), 0);
            int experience = Math.max(Math.min(d.path("experience").asInt(0), 20), 0);
            int academique = Math.max(Math.min(d.path("academique").asInt(0), 15), 0);
            int pfe = Math.max(Math.min(d.path("pfe").asInt(0), 15), 0);
            int langues = Math.max(Math.min(d.path("langues").asInt(0), 8), 0);
            int softskills = Math.max(Math.min(d.path("softskills").asInt(0), 8), 0);
            int certifs = Math.max(Math.min(d.path("certifs").asInt(0), 9), 0);
            
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