package com.tricv.backend.service;

import com.tricv.backend.config.RecrutementConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import java.time.Duration;
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
        String prompt = "Tu es un expert en recrutement multi-secteurs. Pour le domaine ou métier suivant : " + domaine + ", propose une liste RICHE et STRUCTURÉE de compétences à prioriser chez un candidat. "
            + "Fournis entre 5 et 7 grandes CATÉGORIES de compétences couvrant les principales facettes du métier. Pour CHAQUE catégorie, donne entre 4 et 6 SOUS-COMPÉTENCES précises et concrètes (technologies, outils, logiciels, méthodes, savoir-faire ou normes du secteur). "
            + "Couvre différents aspects : les compétences techniques cœur de métier, les outils et logiciels utilisés, les méthodes de travail, et lorsque c'est pertinent les normes/réglementations ou les compétences transversales du domaine. "
            + "Adapte impérativement les catégories et sous-compétences au métier réel demandé : elles doivent être très différentes selon qu'il s'agit d'informatique, de comptabilité, de marketing, de génie civil, de santé, etc. "
            + "Exemples d'esprit attendu : "
            + "- Développeur full stack -> catégories comme Frontend (Angular, React, Vue, HTML/CSS), Backend (Spring Boot, Node.js, Django, .NET), Base de données (MySQL, PostgreSQL, MongoDB), DevOps (Docker, Kubernetes, CI/CD, AWS), Tests (JUnit, Cypress). "
            + "- Comptable -> catégories comme Comptabilité générale, Fiscalité, Audit, Logiciels comptables (Sage, SAP), Reporting et consolidation, Normes (IFRS). "
            + "- Ingénieur génie civil -> catégories comme Calcul de structures, Béton armé, Logiciels (AutoCAD, Robot), Gestion de chantier, Normes (Eurocodes). "
            + "Chaque libellé (catégorie et sous-compétence) doit être court (1 à 4 mots). "
            + "Réponds UNIQUEMENT en JSON, sans texte ni markdown autour, au format exact : "
            + "{\"categories\": [{\"nom\": \"...\", \"sousCompetences\": [\"...\", \"...\"]}]}";

        return appelerGroq(prompt);
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

        // --- Instructions pour compétences prioritaires (si fournies) ---
        String instructionCompsPrio = "";
        if (competencesPrioritaires != null && !competencesPrioritaires.isEmpty()) {
            String listComps = String.join(", ", competencesPrioritaires);
            instructionCompsPrio = "COMPÉTENCES PRIORITAIRES : Le recruteur privilégie particulièrement ces compétences : " + listComps + ". "
                + "Un candidat qui maîtrise fortement ces compétences prioritaires doit recevoir un score technique élevé (18-25). "
                + "Un candidat qui maîtrise le reste du domaine mais PAS les compétences prioritaires doit recevoir un score technique plus faible (10-18). "
                + "Un candidat qui maîtrise TOUT (prioritaire + le reste du domaine) est le meilleur (20-25). "
                + "Explique clairement dans la raison comment le candidat se positionne sur les compétences prioritaires. ";
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
            + "GRILLE FIXE ET UNIQUE (7 CRITÈRES, TOTAL 100) : "
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "

            + "1. COMPÉTENCES TECHNIQUES / MÉTIER (MAX 25 POINTS) : "
            + "   Maîtrise des outils, logiciels, méthodes, savoir-faire spécifiques du domaine/poste. "
            + "   Sois STRICT : ne donne le maximum que si le CV justifie clairement cette maîtrise. "

            + "2. EXPÉRIENCE PROFESSIONNELLE (MAX 20 POINTS) : "
            + "   Années pertinentes + qualité des entreprises/environnements. "
            + "   Grande entreprise, multinationale, institution réputée = plus que structure inconnue. "
            + "   Débutant/stagiaire : évalue stages/projets avec la même logique de pertinence. "
            + "   Sois STRICT : expérience vague = peu de points. "

            + "3. PARCOURS ACADÉMIQUE (MAX 15 POINTS) : "
            + "   HIÉRARCHIE STRICTE : Diplôme INGÉNIEUR > TECHNICIEN. École PUBLIQUE > PRIVÉE. "
            + "   Grandes écoles publiques tunisiennes (" + config.getEcolesPubliquesIngenieur() + ") = maximum. "
            + "   Écoles privées reconnues (" + config.getEcolesPriveesIngenieur() + ") = bien. "
            + "   Master public / licence appliquée = bon. Technicien ISET public = correct. "
            + "   Formation privée non reconnue = minimum. "

            + "4. PROJETS ET PFE (MAX 15 POINTS) : "
            + "   Évalue l'IDÉE, ORIGINALITÉ, CRÉATIVITÉ, ambition, pertinence au poste. "
            + "   ⚠️ SÉVÈRE : Un PFE/projet NON DÉTAILLÉ, peu créatif ou banal ≠ note maximale. "
            + "   - PFE ambitieux, bien documenté, pertinent = 12-15. "
            + "   - Projet solide mais classique = 8-11. "
            + "   - Projet peu documenté ou standard = 4-7. "
            + "   - Aucun projet ou minimal = 0-3. "

            + "5. LANGUES (MAX 8 POINTS) : "
            + "   Nombre et niveau de maîtrise des langues (arabe, français, anglais, autres). "
            + "   Arabe + français + anglais courant = excellent. Seulement français/arabe = bon. "
            + "   Langue supplémentaire (allemand, espagnol, etc.) = bonus. "
            + "   Aucune langue documentée = très faible. "

            + "6. SOFT SKILLS (MAX 8 POINTS) : "
            + "   Travail d'équipe, communication, leadership, autonomie, gestion de projet. "
            + "   Cherche des indices : présidence de clubs, animation de projets, références à la collaboration. "
            + "   Candidat solo, peu de preuves de travail d'équipe = faible. "

            + "7. CERTIFICATIONS ET STABILITÉ (MAX 9 POINTS) : "
            + "   Certifications professionnelles PERTINENTES + stabilité professionnelle. "
            + "   Stabilité = durée dans les postes, peu de changements fréquents. "
            + "   Certifications fortes (AWS, PMO, etc.) = bonus. "
            + "   Changements fréquents d'emploi = points perdus. "

            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "
            + "RÈGLES DE CALCUL (IMPÉRATIF ABSOLU) : "
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "

            + "1. MAXIMUMS STRICTS : Aucun sous-score ne dépasse son max. "
            + "   Technique ≤ 25 | Expérience ≤ 20 | Académique ≤ 15 | PFE ≤ 15 | Langues ≤ 8 | Softskills ≤ 8 | Certifs ≤ 9. "

            + "2. SOMME = SCORE GLOBAL : technique + expérience + académique + pfe + langues + softskills + certifs = score global. "
            + "   Exemple correct : 22 + 17 + 13 + 12 + 7 + 7 + 7 = 85 (score global). "
            + "   Exemple INTERDIT : technique=26 (> 25), ou somme ≠ score. "

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
            + "\"technique\": [0-25], "
            + "\"experience\": [0-20], "
            + "\"academique\": [0-15], "
            + "\"pfe\": [0-15], "
            + "\"langues\": [0-8], "
            + "\"softskills\": [0-8], "
            + "\"certifs\": [0-9]}} "

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