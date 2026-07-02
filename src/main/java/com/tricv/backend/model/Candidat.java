package com.tricv.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name="candidats")
public class Candidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;

    @Column(columnDefinition = "LONGTEXT")
    private String texteCV;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getTexteCV() { return texteCV; }
    public void setTexteCV(String texteCV) { this.texteCV = texteCV; }
}