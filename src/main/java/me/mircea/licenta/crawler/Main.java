package me.mircea.licenta.crawler;

import java.io.IOException;

import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
	
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	
	public static void main(String[] args) throws IOException {
		
		if (args.length > 0) {
			for (String seed : args) {
				Job job = new Job(seed, JobType.CRAWL);
				Crawler crawler;

				if (!job.getRobotRules().getSitemaps().isEmpty()) {
					crawler = new SitemapSaxCrawler(job);
				} else {
					crawler = new BrowserCrawler(job);
				}
				
				// TODO: make it parallel
				crawler.run();
			}
		}
	}
}
