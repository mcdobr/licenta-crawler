package me.mircea.licenta.crawler;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrawlerStart {
	private static final Logger logger = LoggerFactory.getLogger(CrawlerStart.class);

	public static void main(String[] args) {
		List<String> seedList = Arrays.asList("https://carturesti.ro/raft/carte-109",
				"http://www.librariilealexandria.ro/carte"/*,
				"https://www.libris.ro/carti",
				"https://www.emag.ro/search/carti"*/);

		ExecutorService exec = Executors.newCachedThreadPool();

		for (String startUrl : seedList) {
			try {
				exec.submit(new Fetcher(startUrl));
			} catch (MalformedURLException e) {
				logger.debug("Problem regarding gathering pages: {}.", e.getMessage());
			}
		}
	}
}
