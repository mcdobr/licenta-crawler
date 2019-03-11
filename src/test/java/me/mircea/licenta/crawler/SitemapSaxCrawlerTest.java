package me.mircea.licenta.crawler;

import static org.junit.Assert.fail;

import java.io.IOException;

import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobType;
import org.junit.Test;

public class SitemapSaxCrawlerTest {
	
	@Test
	public void shouldHaveSitemaps() throws IOException {
		Job mockRequest = new Job("https://www.bookdepository.com/", JobType.CRAWL);
		SitemapSaxCrawler fetcher = new SitemapSaxCrawler(mockRequest);
		
		fetcher.run();
		fail();
	}
}