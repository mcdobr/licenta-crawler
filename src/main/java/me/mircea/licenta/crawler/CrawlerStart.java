package me.mircea.licenta.crawler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import me.mircea.licenta.core.entities.Book;
import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.WebWrapper;
import me.mircea.licenta.db.products.InstantTranslatorFactory;


public final class CrawlerStart {
	private static final Logger logger = LoggerFactory.getLogger(CrawlerStart.class);
	
	public static final ExecutorService executor = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());
	
	public static void main(String[] args) throws InterruptedException {
		Datastore datastore = DatastoreOptions.newBuilder()
				.setProjectId("bookworm-221210")
				.build()
				.getService();
		ObjectifyService.init(new ObjectifyFactory(datastore));
		
		ObjectifyService.factory().getTranslators().add(new InstantTranslatorFactory());
		//new ReadableInstantTranslatorFactory();
		ObjectifyService.register(Book.class);
		ObjectifyService.register(PricePoint.class);
		ObjectifyService.register(WebWrapper.class);
		
		List<String> seedList = Arrays.asList(
				"https://carturesti.ro/raft/carte-109?per-page=90",
				"http://www.librariilealexandria.ro/carte",
				"https://www.libris.ro/carti"
				);

		for (String startUrl : seedList) {
			try {
				executor.execute(new BrowserFetcher(startUrl));
			} catch (MalformedURLException e) {
				logger.debug("Problem regarding gathering pages: {}.", e.getMessage());
			} catch (FileNotFoundException | NullPointerException e) {
				logger.error("Configuration file not found. Exception details: {}", e);
			} catch (IOException e) {
				logger.warn("Could not read from an input stream. Exception details: {}", e);
			}
		}

		// executor.shutdown();
		executor.awaitTermination(150, TimeUnit.MINUTES);
	}
}
