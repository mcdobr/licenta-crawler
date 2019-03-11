package me.mircea.licenta.crawler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class BrowserCrawlerTest {
	@Test
	public void shouldExtractAllSingleProductPages() {
		BrowserCrawler browserFetcher;
		String url = "http://www.librariilealexandria.ro/carte?limit=24";
		
		try {
			browserFetcher = new BrowserCrawler(new Job(url, JobType.CRAWL));
			
			List<String> urls = browserFetcher.getSingleProductPages(Jsoup.connect(url).get());
			assertTrue(24 <= urls.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void shouldContainPopUp() throws IOException {
		Document doc = Jsoup.connect("https://www.libris.ro/carti").get();
		
		assertNotNull(doc.selectFirst(".NewsClose"));
		
	}
	
}