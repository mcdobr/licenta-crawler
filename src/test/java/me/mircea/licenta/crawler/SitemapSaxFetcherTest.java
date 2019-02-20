package me.mircea.licenta.crawler;

import static org.junit.Assert.fail;

import java.io.IOException;
import org.junit.Test;

import me.mircea.licenta.core.crawl.CrawlRequest;

public class SitemapSaxFetcherTest {
	
	@Test
	public void shouldHaveSitemaps() throws IOException {
		CrawlRequest mockRequest = new CrawlRequest("https://www.bookdepository.com/");
		SitemapSaxFetcher fetcher = new SitemapSaxFetcher(mockRequest);
		
		fetcher.run();
		fail();
	}
}