package com.example.commandes;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Apres un arret brutal, l'execution du job reste « STARTED » en base, et Spring Batch refuse
 * de la relancer. Ce runner la declare echouee ({@link JobOperator#recover}, Spring Batch 6)
 * avant que Spring Boot ne lance le job, qui reprend alors la ou il s'etait arrete.
 * <p>
 * A n'activer que si l'on est sur qu'aucune autre instance du job ne tourne : sinon, on
 * declarerait echoue un job bien vivant.
 */
@Component
@Order(-1) // avant le lanceur de jobs de Spring Boot
@ConditionalOnProperty("import.recuperer-apres-crash")
public class RecuperationApresCrash implements ApplicationRunner {

	private final JobRepository jobRepository;

	private final JobOperator jobOperator;

	public RecuperationApresCrash(JobRepository jobRepository, JobOperator jobOperator) {
		this.jobRepository = jobRepository;
		this.jobOperator = jobOperator;
	}

	@Override
	public void run(ApplicationArguments args) {
		var bloquees = jobRepository.findRunningJobExecutions("importCommandes");
		for (JobExecution bloquee : bloquees) {
			JobExecution recuperee = jobOperator.recover(bloquee);
			System.out.println("RECUPERATION execution " + recuperee.getId()
					+ " : " + recuperee.getStatus());
		}
	}
}
