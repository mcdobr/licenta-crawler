package me.mircea.licenta.crawler;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.crawl.CrawlRequest;

public class Main {
	
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	
	public static void main(String[] args) throws IOException {
		
		if (args.length > 0) {
			for (String startUrl : args) {
				CrawlRequest request = new CrawlRequest(startUrl);
				Fetcher fetcher;
				
				
				if (!request.getRobotRules().getSitemaps().isEmpty()) {
					fetcher = new SitemapSaxFetcher(request);
				} else {
					fetcher = new BrowserFetcher(request);
				}
				
				// TODO: make it parallel
				fetcher.run();
			}
		}
	}
}
