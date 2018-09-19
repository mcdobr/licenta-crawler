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

public final class CrawlerStart {
	private static final Logger logger = LoggerFactory.getLogger(CrawlerStart.class);

	public static void main(String[] args) throws InterruptedException {
		List<String> seedList = Arrays.asList(/*"https://carturesti.ro/raft/carte-109",*/
				"http://www.librariilealexandria.ro/carte"/*,
				"https://www.libris.ro/carti",*/
				/*"https://www.emag.ro/search/carti"*/);

		ExecutorService exec = Executors.newSingleThreadExecutor();
		for (String startUrl : seedList) {
			try {
				exec.execute(new Fetcher(startUrl));
			} catch (MalformedURLException e) {
				logger.debug("Problem regarding gathering pages: {}.", e.getMessage());
			} catch (FileNotFoundException | NullPointerException e) {
				logger.error("Configuration file not found");
			} catch (IOException e) {
				logger.warn("Could not read from an input stream");
			}
		}

		//exec.shutdown();
		//exec.awaitTermination(2, TimeUnit.DAYS);
	}
}
