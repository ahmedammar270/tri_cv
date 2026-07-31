package com.tricv.backend.service;

import com.tricv.backend.config.RecrutementConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IAService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Autowired
    private RecrutementConfig config;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.groq.com/openai/v1")
            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(30))
            ))
            .build();

    private String appelerGroq(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", "openai/gpt-oss-120b",
                "max_tokens", 4000,
                "reasoning_effort", "low",
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        Map response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .block();

        List<Map> choices = (List<Map>) response.get("choices");
        Map message = (Map) choices.get(0).get("message");
        String content = message.get("content").toString();

        return content.replaceAll("```json", "").replaceAll("```", "").trim();
    }

    public String extraireNom(String texteCV) {
        String prompt = "Voici le texte d'un CV. Extrais uniquement le nom complet du candidat. "
                + "CV : " + texteCV
                + ". Reponds UNIQUEMENT en JSON sans markdown : {\"nom\": \"Prenom Nom\"}";

        return appelerGroq(prompt);
    }

    public String resoudreProfil(String texteCV, String modeProfil) {
        if (modeProfil == null || modeProfil.equalsIgnoreCase("auto")) {
            return detecterProfil(texteCV);
        }
        return modeProfil;
    }

    // --- Suggestion de compétences selon le domaine ---
    public String suggererCompetences(String domaine) {
        String prompt = "Tu es un expert en recrutement. Pour le domaine ou métier suivant : " + domaine + ", propose les compétences les plus PERTINENTES et COURANTES à prioriser chez un candidat, celles qu'un recruteur recherche réellement en priorité pour ce poste. "
            + "Fournis entre 5 et 7 grandes CATÉGORIES de compétences clairement liées à ce métier. Pour CHAQUE catégorie, donne entre 4 et 6 SOUS-COMPÉTENCES concrètes. "
            + "RÈGLES IMPORTANTES : "
            + "- Privilégie les compétences, technologies et outils les plus RÉPANDUS et DEMANDÉS sur le marché pour ce métier. Évite les technologies rares, de niche ou anecdotiques. "
            + "- Les catégories doivent être directement pertinentes pour le domaine demandé. "
            + "- Classe les compétences de la plus importante/courante à la moins courante. "
            + "- Adapte tout au métier réel : les compétences d'un développeur Java, d'un comptable ou d'un ingénieur génie civil sont totalement différentes. "
            + "Exemples de bon niveau de pertinence : "
            + "- Développeur Java -> catégories comme 'Langage & Fondamentaux' (Java, POO, Collections, Exceptions), 'Frameworks' (Spring Boot, Spring MVC, Hibernate, Spring Security), 'Bases de données' (SQL, MySQL, PostgreSQL, JPA), 'Outils & Build' (Maven, Git, JUnit), 'API & Web' (API REST, JSON, Microservices). NE PAS proposer de frameworks rares comme Micronaut ou Quarkus en priorité. "
            + "- Comptable -> 'Comptabilité générale', 'Fiscalité', 'Logiciels' (Sage, SAP, Excel), 'Audit & Contrôle', 'Reporting'. "
            + "Chaque libellé (catégorie et sous-compétence) doit être court (1 à 4 mots). "
            + "Réponds UNIQUEMENT en JSON, sans texte ni markdown autour, au format exact : "
            + "{\"categories\": [{\"nom\": \"...\", \"sousCompetences\": [\"...\", \"...\"]}]}";

        return appelerGroq(prompt);
    }

    // --- Maximums par critere, VARIABLES selon le profil (chaque colonne totalise 100) ---
    // Utilise a la fois pour construire le prompt (grille + bornes JSON) ET, cote EvaluationService,
    // pour clamper/stocker les sous-scores : une seule source de verite, calculee en Java (fiable,
    // ne depend pas de l'IA pour renvoyer les bons maximums).
    public Map<String, Integer> maxScoresPourProfil(String profil) {
        Map<String, Integer> max = new LinkedHashMap<>();
        if (profil != null && profil.equalsIgnoreCase("stagiaire")) {
            max.put("technique", 20);
            max.put("experience", 5);
            max.put("academique", 25);
            max.put("pfe", 27);
            max.put("langues", 8);
            max.put("softskills", 8);
            max.put("certifs", 7);
        } else if (profil != null && profil.equalsIgnoreCase("debutant")) {
            max.put("technique", 22);
            max.put("experience", 8);
            max.put("academique", 22);
            max.put("pfe", 24);
            max.put("langues", 8);
            max.put("softskills", 8);
            max.put("certifs", 8);
        } else { // "experimente" (defaut)
            max.put("technique", 25);
            max.put("experience", 28);
            max.put("academique", 10);
            max.put("pfe", 8);
            max.put("langues", 8);
            max.put("softskills", 8);
            max.put("certifs", 13);
        }
        return max;
    }

    public String analyserCV(String texteCV, String specialite, String modeProfil) {
        return analyserCV(texteCV, specialite, modeProfil, null);
    }

    public String analyserCV(String texteCV, String specialite, String modeProfil, List<String> competencesPrioritaires) {

        // --- Detection auto du profil si demande ---
        String profil = resoudreProfil(texteCV, modeProfil);

        // --- Contexte selon profil (influence l'interpretation, PAS les maximums) ---
        String contexte;
        if (profil.equalsIgnoreCase("debutant")) {
            contexte = "PROFIL : Jeune diplômé. L'expérience professionnelle compte TRÈS PEU (quasi inexistante). "
                + "Valorise : formation académique solide, projets académiques (PFE/mémoire), potentiel, alignement secteur, soft skills et capacité d'apprentissage. ";
        } else if (profil.equalsIgnoreCase("stagiaire")) {
            contexte = "PROFIL : Stagiaire ou étudiant. Pas d'expérience professionnelle réelle. "
                + "Valorise : parcours académique, projets académiques/PFE, potentiel d'apprentissage, soft skills, motivation et alignement secteur. ";
        } else {
            contexte = "PROFIL : Expérimenté. L'expérience professionnelle est centrale. "
                + "Valorise : années pertinentes, qualité des entreprises (réputation, environnement), maîtrise techniques, soft skills, certifications et stabilité. ";
        }

        // --- Maximums par critere pour ce profil (cf. maxScoresPourProfil) ---
        Map<String, Integer> max = maxScoresPourProfil(profil);
        int maxTechnique = max.get("technique");
        int maxExperience = max.get("experience");
        int maxAcademique = max.get("academique");
        int maxPfe = max.get("pfe");
        int maxLangues = max.get("langues");
        int maxSoftskills = max.get("softskills");
        int maxCertifs = max.get("certifs");

        // --- Instructions pour compétences prioritaires (si fournies) ---
        String instructionCompsPrio = "";
        if (competencesPrioritaires != null && !competencesPrioritaires.isEmpty()) {
            String listComps = String.join(", ", competencesPrioritaires);
            instructionCompsPrio = "COMPÉTENCES PRIORITAIRES : Le recruteur privilégie particulièrement ces compétences : " + listComps + ". "
                + "Un candidat qui maîtrise fortement ces compétences prioritaires doit recevoir un score technique élevé, proche du maximum de " + maxTechnique + " points. "
                + "Un candidat qui maîtrise le reste du domaine mais PAS les compétences prioritaires doit recevoir un score technique nettement plus faible (environ 40 à 70% du maximum de " + maxTechnique + "). "
                + "Un candidat qui maîtrise TOUT (prioritaire + le reste du domaine) est le meilleur et doit approcher le maximum de " + maxTechnique + ". "
                + "Explique clairement dans la raison comment le candidat se positionne sur les compétences prioritaires. "
                + "IMPORTANT : dans ta réponse, ne cite QUE les compétences prioritaires listées ci-dessus (" + listComps + ") comme étant \"prioritaires\" — "
                + "ne mentionne aucune autre compétence sous ce terme, même si elle te semble pertinente pour le poste. ";
        }

        String prompt = "Tu es un expert RH senior capable d'évaluer des candidats dans tous les secteurs tunisiens (comptabilité, finance, marketing, RH, logistique, commerce, génie civil, santé, industrie, services, etc.). "
            + "Analyse ce CV pour le poste : " + specialite + ". "
            + "Adapte l'évaluation au secteur, aux métiers et compétences requis, sans limiter à l'informatique. "
            + "Pour postes commerciaux : valorise résultats, relation client, prospection. "
            + "Pour finance/comptabilité : valorise rigueur, logiciels de gestion, conformité. "
            + "Pour RH : valorise gestion personnel, recrutement, administration. "
            + "Pour engineering/génie civil : valorise conception, normes, chantiers, planification. "
            + "Pour santé : valorise formation, certifications, expérience clinique/paramédicale. "
            + "CV : " + texteCV + ". "
            + "Profil détecté : " + profil + ". "
            + "Contexte : " + contexte + " "
            + instructionCompsPrio

            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "
            + "GRILLE ADAPTÉE AU PROFIL (7 CRITÈRES, TOTAL TOUJOURS 100) : "
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "

            + "1. COMPÉTENCES TECHNIQUES / MÉTIER (MAX " + maxTechnique + " POINTS) : "
            + "   Maîtrise des outils, logiciels, méthodes, savoir-faire spécifiques du domaine/poste. "
            + "   Sois STRICT : ne donne le maximum que si le CV justifie clairement cette maîtrise. "

            + "2. EXPÉRIENCE PROFESSIONNELLE (MAX " + maxExperience + " POINTS) : "
            + "   Années pertinentes + qualité des entreprises/environnements. "
            + "   Grande entreprise, multinationale, institution réputée = plus que structure inconnue. "
            + "   Débutant/stagiaire : évalue stages/projets avec la même logique de pertinence. "
            + "   Sois STRICT : expérience vague = peu de points. "

            + "3. PARCOURS ACADÉMIQUE (MAX " + maxAcademique + " POINTS) : "
            + "   HIÉRARCHIE STRICTE : Diplôme INGÉNIEUR > TECHNICIEN. École PUBLIQUE > PRIVÉE. "
            + "   Grandes écoles publiques tunisiennes (" + config.getEcolesPubliquesIngenieur() + ") = maximum. "
            + "   Écoles privées reconnues (" + config.getEcolesPriveesIngenieur() + ") = bien. "
            + "   Master public / licence appliquée = bon. Technicien ISET public = correct. "
            + "   Formation privée non reconnue = minimum. "

            + "4. PROJETS ET PFE (MAX " + maxPfe + " POINTS) : "
            + "   Évalue l'IDÉE, ORIGINALITÉ, CRÉATIVITÉ, ambition, pertinence au poste. "
            + "   ⚠️ SÉVÈRE : Un PFE/projet NON DÉTAILLÉ, peu créatif ou banal ≠ note maximale. "
            + "   - PFE ambitieux, bien documenté, pertinent = proche du maximum (" + maxPfe + "). "
            + "   - Projet solide mais classique = environ 60 à 75% du maximum. "
            + "   - Projet peu documenté ou standard = environ 25 à 50% du maximum. "
            + "   - Aucun projet ou minimal = proche de 0. "

            + "5. LANGUES (MAX " + maxLangues + " POINTS) : "
            + "   Nombre et niveau de maîtrise des langues (arabe, français, anglais, autres). "
            + "   Arabe + français + anglais courant = excellent. Seulement français/arabe = bon. "
            + "   Langue supplémentaire (allemand, espagnol, etc.) = bonus. "
            + "   Aucune langue documentée = très faible. "

            + "6. SOFT SKILLS (MAX " + maxSoftskills + " POINTS) : "
            + "   Travail d'équipe, communication, leadership, autonomie, gestion de projet. "
            + "   Cherche des indices : présidence de clubs, animation de projets, références à la collaboration. "
            + "   Candidat solo, peu de preuves de travail d'équipe = faible. "

            + "7. CERTIFICATIONS ET STABILITÉ (MAX " + maxCertifs + " POINTS) : "
            + "   Certifications professionnelles PERTINENTES + stabilité professionnelle. "
            + "   Stabilité = durée dans les postes, peu de changements fréquents. "
            + "   Certifications fortes (AWS, PMO, etc.) = bonus. "
            + "   Changements fréquents d'emploi = points perdus. "

            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "
            + "RÈGLES DE CALCUL (IMPÉRATIF ABSOLU) : "
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "

            + "1. MAXIMUMS STRICTS : Aucun sous-score ne dépasse son max POUR CE PROFIL. "
            + "   Technique ≤ " + maxTechnique + " | Expérience ≤ " + maxExperience + " | Académique ≤ " + maxAcademique
            + " | PFE ≤ " + maxPfe + " | Langues ≤ " + maxLangues + " | Softskills ≤ " + maxSoftskills + " | Certifs ≤ " + maxCertifs + ". "

            + "2. SOMME = SCORE GLOBAL : technique + expérience + académique + pfe + langues + softskills + certifs = score global (toujours sur 100). "
            + "   Exemple correct pour ce profil : " + Math.round(maxTechnique * 0.8f) + " + " + Math.round(maxExperience * 0.8f) + " + "
            + Math.round(maxAcademique * 0.8f) + " + " + Math.round(maxPfe * 0.8f) + " + " + Math.round(maxLangues * 0.8f) + " + "
            + Math.round(maxSoftskills * 0.8f) + " + " + Math.round(maxCertifs * 0.8f) + " = "
            + (Math.round(maxTechnique * 0.8f) + Math.round(maxExperience * 0.8f) + Math.round(maxAcademique * 0.8f)
               + Math.round(maxPfe * 0.8f) + Math.round(maxLangues * 0.8f) + Math.round(maxSoftskills * 0.8f) + Math.round(maxCertifs * 0.8f))
            + " (score global). "
            + "   Exemple INTERDIT : technique > " + maxTechnique + ", ou somme ≠ score. "

            + "3. VÉRIFICATION AVANT RÉPONSE : "
            + "   Recalcule la somme. Si ≠ score global annoncé, corrige-la. "
            + "   Vérifie que chaque sous-score ≤ max et que somme = score global. "

            + "4. SOIS DISCRIMINANT : "
            + "   Évite les scores ronds (80, 75). Utilise toute l'échelle (73, 82, 64, 91). "
            + "   Deux candidats proches reçoivent des scores légèrement différents. "

            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "
            + "FORMAT JSON (OBLIGATOIRE, SANS MARKDOWN NI BACKTICKS) : "
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "

            + "{\"score\": 85, "
            + "\"raison\": \"Résumé court expliquant chaque catégorie et le calcul final (ex: 22+17+13+12+7+7+7=85). Inclus spécifiquement la position sur les compétences prioritaires si applicable.\", "
            + "\"pointsForts\": \"Liste des points forts\", "
            + "\"pointsFaibles\": \"Liste des points faibles\", "
            + "\"profil\": \"" + profil + "\", "
            + "\"detailScores\": {"
            + "\"technique\": [0-" + maxTechnique + "], "
            + "\"experience\": [0-" + maxExperience + "], "
            + "\"academique\": [0-" + maxAcademique + "], "
            + "\"pfe\": [0-" + maxPfe + "], "
            + "\"langues\": [0-" + maxLangues + "], "
            + "\"softskills\": [0-" + maxSoftskills + "], "
            + "\"certifs\": [0-" + maxCertifs + "]}} "

            + "DERNIER CHECK AVANT ENVOI : "
            + "1. Vérifie que chaque sous-score ≤ son max. "
            + "2. Additionne les 7 sous-scores. "
            + "3. Compare la somme au score global. "
            + "4. Si différence, corrige le score global pour qu'il égale la somme. "
            + "5. Envoie le JSON. ";

        return appelerGroq(prompt);
    }

    // --- Detection automatique du profil debutant / experimente / stagiaire ---
    private String detecterProfil(String texteCV) {
        if (texteCV == null) {
            return "experimente";
        }

        String t = texteCV.toLowerCase();

        Matcher m = Pattern.compile("(\\d+)\\s*(ans|annee|year)").matcher(t);
        int maxAnnees = 0;
        while (m.find()) {
            try {
                maxAnnees = Math.max(maxAnnees, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {}
        }

        boolean signesStagiaire = t.contains("stagiaire")
                || t.contains("recherche de stage")
                || t.contains("stage de fin d'etudes")
                || t.contains("stage de fin d’études")
                || t.contains("internship")
                || t.contains("pfe");

        boolean signesDebutant = t.contains("pfe")
                || t.contains("projet de fin")
                || t.contains("stage")
                || t.contains("recherche un premier emploi");

        if (signesStagiaire) return "stagiaire";
        if (maxAnnees >= 2) return "experimente";
        if (signesDebutant) return "debutant";
        return "experimente";
    }
}