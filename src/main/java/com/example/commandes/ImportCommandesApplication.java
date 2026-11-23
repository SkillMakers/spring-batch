package com.example.commandes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ImportCommandesApplication {

	public static void main(String[] args) {
		// Code de sortie : 0 si le job est COMPLETED, sinon le rang de son statut (5 = FAILED) ;
		// 1 si l'application s'arrete sur une exception (relance refusee, par exemple).
		System.exit(SpringApplication.exit(SpringApplication.run(ImportCommandesApplication.class, args)));
	}
}
