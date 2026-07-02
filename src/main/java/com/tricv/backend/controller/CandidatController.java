package com.tricv.backend.controller;

import com.tricv.backend.model.Candidat;
import com.tricv.backend.model.Evaluation;
import com.tricv.backend.service.CandidatService;
import com.tricv.backend.service.EvaluationService;
import com.tricv.backend.service.IAService;
import com.tricv.backend.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CandidatController {

    @Autowired
    private CandidatService candidatService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private IAService iaService;

    @Autowired
    private PdfService pdfService;

    @GetMapping("/candidats")
    public List<Candidat> getTousLesCandidats() {
        return candidatService.getTous();
    }

    @GetMapping("/candidats/recherche")
    public List<Candidat> rechercherParNom(@RequestParam String nom) {
        return candidatService.rechercherParNom(nom);
    }

    @DeleteMapping("/candidats/{id}")
    public void supprimerCandidat(@PathVariable Long id) {
        candidatService.supprimerCandidat(id);
    }

    @PostMapping("/candidats/ajouter-pdf")
    public List<Candidat> ajouterCandidatsPdf(
            @RequestParam("fichiers") List<MultipartFile> fichiers) {

        List<Candidat> resultats = new ArrayList<>();

        for (MultipartFile fichier : fichiers) {
            try {
                String texteCV = pdfService.extraireTexte(fichier);

                String nomFichier = fichier.getOriginalFilename();
                String nom = nomFichier != null
                    ? nomFichier.replace(".pdf", "")
                                .replace("_", " ")
                                .replace("-", " ")
                    : "Candidat inconnu";

                Candidat candidat = new Candidat();
                candidat.setNom(nom);
                candidat.setTexteCV(texteCV);

                resultats.add(candidatService.addCandidat(candidat));
                System.out.println("Ajoute : " + nom);

            } catch (Exception e) {
                System.err.println("Erreur pour " + fichier.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        return resultats;
    }

    @GetMapping(value = "/competences/suggerer", produces = MediaType.APPLICATION_JSON_VALUE)
    public String suggererCompetences(@RequestParam String domaine) {
        return iaService.suggererCompetences(domaine);
    }

    @GetMapping("/evaluations/recherche")
    public List<Evaluation> rechercherParDomaine(
            @RequestParam String domaine,
            @RequestParam(defaultValue = "auto") String modeProfil,
            @RequestParam(required = false) String competencesPrioritaires) {
        return evaluationService.rechercherParDomaine(domaine, modeProfil, competencesPrioritaires);
    }
    @DeleteMapping("/evaluations/vider")
    public String viderEvaluations() {
        evaluationService.viderToutesLesEvaluations();
        return "Toutes les evaluations ont ete supprimees";
    }
}