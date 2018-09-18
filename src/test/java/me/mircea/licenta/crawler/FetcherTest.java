package me.mircea.licenta.crawler;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;

import org.jsoup.Jsoup;
import org.junit.Test;

public class FetcherTest {
	@Test
	public void shouldExtractAllSingleProductPages() {
		Fetcher fetcher;
		String url = "http://www.librariilealexandria.ro/carte?limit=24";
		try {
			fetcher = new Fetcher(url);
			
			Set<String> urls = fetcher.getSingleProductPages(Jsoup.connect(url).get()).keySet();
			assertTrue(24 <= urls.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
