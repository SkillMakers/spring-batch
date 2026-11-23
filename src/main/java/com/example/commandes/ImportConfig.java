package com.example.commandes;

import java.time.format.DateTimeParseException;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Le job d'import : lire le fichier CSV, controler chaque ligne, ecrire les commandes en base,
 * par paquets (chunks) de {@code import.taille-chunk} lignes, une transaction par paquet.
 */
@Configuration
public class ImportConfig {

	@Bean
	public Job importCommandes(JobRepository jobRepository, Step chargement) {
		return new JobBuilder("importCommandes", jobRepository)
				.start(chargement)
				.build();
	}

	@Bean
	public Step chargement(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<LigneCsv> lecteur, ControleCommande controle, JdbcBatchItemWriter<Commande> ecrivain,
			@Value("${import.taille-chunk:100}") int tailleChunk,
			@Value("${import.tolerant:false}") boolean tolerant) {
		ChunkOrientedStepBuilder<LigneCsv, Commande> step =
				new StepBuilder("chargement", jobRepository)
				.<LigneCsv, Commande>chunk(tailleChunk)
				.transactionManager(transactionManager)
				.reader(lecteur)
				.processor(controle)
				.writer(ecrivain)
				.listener(bilan(controle));
		if (tolerant) {
			step.faultTolerant()
					.skip(FlatFileParseException.class,      // ligne illisible
							NumberFormatException.class,
							DateTimeParseException.class,
							MontantInvalideException.class,  // montant invalide
							DuplicateKeyException.class)     // cle en double
					.skipLimit(10)
					.skipListener(rejets());
		}
		return step.build();
	}

	@Bean
	@StepScope
	public FlatFileItemReader<LigneCsv> lecteur(
			@Value("#{jobParameters['fichier']}") String fichier) {
		return new FlatFileItemReaderBuilder<LigneCsv>()
				.name("lecteurCommandes")
				.resource(new FileSystemResource(fichier))
				.linesToSkip(1)
				.delimited()
				.names("id", "client", "montant", "date")
				.targetType(LigneCsv.class)
				.build();
	}

	@Bean
	public ControleCommande controle(@Value("${import.crash-a-la-ligne:0}") long crashALaLigne) {
		return new ControleCommande(crashALaLigne);
	}

	@Bean
	public JdbcBatchItemWriter<Commande> ecrivain(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<Commande>()
				.dataSource(dataSource)
				.sql("INSERT INTO commande (id, client, montant, date_commande) VALUES (:id, :client, :montant, :date)")
				.itemSqlParameterSourceProvider(c -> new MapSqlParameterSource()
						.addValue("id", c.id())
						.addValue("client", c.client())
						.addValue("montant", c.montant())
						.addValue("date", c.date()))
				.build();
	}

	private SkipListener<LigneCsv, Commande> rejets() {
		return new SkipListener<>() {
			@Override
			public void onSkipInRead(Throwable t) {
				System.out.println("REJET lecture : " + t.getMessage());
			}

			@Override
			public void onSkipInProcess(LigneCsv ligne, Throwable t) {
				System.out.println("REJET controle : " + t.getMessage());
			}

			@Override
			public void onSkipInWrite(Commande commande, Throwable t) {
				System.out.println("REJET ecriture : commande " + commande.id() + " deja en base");
			}
		};
	}

	private StepExecutionListener bilan(ControleCommande controle) {
		return new StepExecutionListener() {
			@Override
			public org.springframework.batch.core.ExitStatus afterStep(StepExecution s) {
				System.out.printf("BILAN lues=%d ecrites=%d rejets=%d commits=%d appels-controle=%d statut=%s%n",
						s.getReadCount(), s.getWriteCount(), s.getSkipCount(), s.getCommitCount(),
						controle.appels(), s.getStatus());
				return s.getExitStatus();
			}
		};
	}
}
