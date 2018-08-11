package me.mircea.licenta.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InternetDomainName;

public class Fetcher {
	private String startUrl;
	private String domain;
	private WebDriver driver;

	private static final Logger logger = LoggerFactory.getLogger(Fetcher.class);
	
	public Fetcher(String startUrl) throws MalformedURLException {
		this.startUrl = startUrl;
		this.domain = getDomainOfUrl(startUrl);
		System.setProperty("webdriver.gecko.driver", "D:\\geckodriver.exe");
		
		FirefoxOptions opts = new FirefoxOptions();
		//opts.setHeadless(true);
		
		this.driver = new FirefoxDriver(opts);
	}
	
	/**
	 * @brief Get all webpages that are relevant to the search (on the same domain).
	 *        TODO: make it so it follows robots.txt.
	 * @return The set of requested documents.
	 */
	public Set<Document> getRelevantWebPages() {
		HashMap<String, Boolean> haveVisitedUrl = new HashMap<>();
		Set<Document> documents = new HashSet<>();

		Queue<Document> docQueue = new LinkedList<>();
		Document startDoc = getDocumentAndStripStyle(startUrl);
		docQueue.add(startDoc);
		try {
			java.nio.file.Files.write(Paths.get("./duke.html"), startDoc.outerHtml().getBytes());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		
		haveVisitedUrl.put(startUrl, true);
		while (!docQueue.isEmpty()) {
			Document doc = docQueue.poll();
			Elements links = doc.select("a:not([href^=#])");/*.select("a[href~=^/?[^/]+]");*/
			//System.out.println(Arrays.toString(links.toArray()));
			
			for (Element link : links) {
				try {
					String absUrl = link.absUrl("href");
					if (!haveVisitedUrl.containsKey(absUrl) && isWorthVisiting(absUrl)) {
						logger.debug("Added url " + absUrl);
						
						Document retrievedDoc = getDocumentAndStripStyle(absUrl);
						haveVisitedUrl.put(absUrl, true);
						Thread.currentThread().sleep(6000);
						docQueue.add(retrievedDoc);
						documents.add(retrievedDoc);
					}
				} catch (InterruptedException e) {

				} catch (MalformedURLException e) {

				}
			}
		}

		driver.quit();
		return documents;
	}

	private Document getDocumentAndStripStyle(String url) {
		driver.get(url);
		String pageContent = driver.getPageSource();
		
		
		Document doc = Jsoup.parse(pageContent, startUrl);
		doc.getElementsByTag("style").remove();
		doc.select("[style]").removeAttr("style");
		return doc;
	}

	private String getDomainOfUrl(String url) throws MalformedURLException {
		return InternetDomainName.from(new URL(url).getHost()).topPrivateDomain().toString();
	}

	private boolean isWorthVisiting(String url) throws MalformedURLException {
		boolean result = getDomainOfUrl(url).equals(domain) && url.startsWith(startUrl);
		return result;
	}

	public static void main(String[] args) {

		String domain = "https://carturesti.ro";
		String startUrl = "https://carturesti.ro/raft/carte-109";

		try {
			Fetcher fetcher = new Fetcher(startUrl);
			fetcher.getRelevantWebPages();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
