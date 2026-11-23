package com.example.commandes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Transforme une ligne du fichier en commande, et refuse les montants negatifs ou nuls.
 * <p>
 * Il compte ses appels : quand l'ecriture d'un paquet echoue, Spring Batch le rappelle
 * sur des lignes deja traitees.
 */
public class ControleCommande implements ItemProcessor<LigneCsv, Commande> {

	private final AtomicLong appels = new AtomicLong();

	private final long crashALaLigne;

	public ControleCommande(long crashALaLigne) {
		this.crashALaLigne = crashALaLigne;
	}

	@Override
	public Commande process(LigneCsv ligne) {
		appels.incrementAndGet();
		long id = Long.parseLong(ligne.id());
		// On compare l'identifiant de la commande : dans le fichier du 24/11, il vaut le numero de ligne.
		if (id == crashALaLigne) {
			// Arret brutal, comme un kill -9 : aucun code de fin ne s'execute.
			System.out.println(">>> ARRET BRUTAL a la ligne " + id);
			Runtime.getRuntime().halt(137);
		}
		BigDecimal montant = new BigDecimal(ligne.montant());
		if (montant.signum() <= 0) {
			throw new MontantInvalideException("ligne " + id + " : montant " + montant);
		}
		return new Commande(id, ligne.client(), montant, LocalDate.parse(ligne.date()));
	}

	public long appels() {
		return appels.get();
	}
}
