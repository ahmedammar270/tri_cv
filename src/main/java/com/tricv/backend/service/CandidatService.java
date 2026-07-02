package com.tricv.backend.service;

import com.tricv.backend.model.Candidat;
import com.tricv.backend.repository.CandidatRepository;
import com.tricv.backend.repository.EvaluationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CandidatService {

    @Autowired
    private CandidatRepository candidatRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    public List<Candidat> rechercherParNom(String nom) {
        return candidatRepository.findByNomContainingIgnoreCase(nom);
    }

    public List<Candidat> getTous() {
        return candidatRepository.findAll();
    }

    public Candidat addCandidat(Candidat candidat) {
        return candidatRepository.save(candidat);
    }

    @Transactional
    public void supprimerCandidat(Long id) {
        // Supprimer les évaluations liées d'abord
        evaluationRepository.deleteByCandidatId(id);
        // Puis supprimer le candidat
        candidatRepository.deleteById(id);
    }
}