package com.example.commandes;

/** Une ligne dont le montant est negatif ou nul. */
public class MontantInvalideException extends RuntimeException {

	public MontantInvalideException(String message) {
		super(message);
	}
}
