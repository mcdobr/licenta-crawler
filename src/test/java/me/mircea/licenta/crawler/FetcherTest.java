package me.mircea.licenta.crawler;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class FetcherTest {
	@Test
	public void getProductPages() {
		Fetcher fetcher;
		try {
			fetcher = new Fetcher("http://www.librariilealexandria.ro/carte?limit=24");
			assertTrue(false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
