package me.mircea.licenta.crawler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InternetDomainName;

public class Fetcher {
	private String startUrl;
	private String domain;
	private WebDriver driver;
	private ExecutorService exec = Executors.newCachedThreadPool();
	
	private static final Logger logger = LoggerFactory.getLogger(Fetcher.class);
	private static final int CRAWLER_DELAY = 3600;

	public Fetcher(String startUrl) throws MalformedURLException {
		this.startUrl = startUrl;
		this.domain = getDomainOfUrl(startUrl);
		System.setProperty("webdriver.gecko.driver", "D:\\geckodriver.exe");

		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", 2); // Don't load images

		profile.setPreference("dom.popup_maximum", 0);
		profile.setPreference("privacy.popups.showBrowserMessage", false);
		/*
		 * profile.setPreference("dom.disable_beforeunload", true); // Disable pop-ups
		 * profile.setPreference("network.cookie.cookieBehavior", 2); // Disable cookies
		 */
		FirefoxOptions opts = new FirefoxOptions();
		// opts.setHeadless(true);
		opts.setProfile(profile);

		this.driver = new FirefoxDriver(opts);
	}

	/**
	 * @brief Get all webpages that are relevant to the search (on the same domain).
	 *        TODO: make it so it follows robots.txt.
	 * @return The set of requested documents.
	 * @throws InterruptedException
	 */
	public Set<Document> getRelevantWebPages() throws InterruptedException {
		Set<String> vistiedUrls = new HashSet<>();
		Set<Document> documents = new HashSet<>();
		Queue<String> urlFrontier = new LinkedList<>();

		urlFrontier.add(startUrl);
		while (!urlFrontier.isEmpty()) {
			logger.info("Analyzing {}", urlFrontier.peek());

			String url = urlFrontier.poll();
			driver.get(url);
			vistiedUrls.add(url);

			Thread.sleep(5000);

			Document startDoc = getDocumentStripped(driver.getPageSource());
			documents.add(startDoc);

			List<WebElement> links = driver.findElements(By.cssSelector("a[href]"));
			for (WebElement link : links) {
				String absUrl = link.getAttribute("href");

				try {
					if (!vistiedUrls.contains(absUrl) && isWorthVisiting(absUrl)) {
						urlFrontier.add(absUrl);
					}
				} catch (MalformedURLException e) {
					logger.info("Requested URL {} is malformed.", absUrl);
				}
			}
		}
		driver.quit();
		return documents;
	}

	public Set<Document> getMultiProductWebPages(final String startUrl) throws InterruptedException {
		Set<Document> documents = new HashSet<>();

		String url = startUrl;
		driver.get(url);
		
		boolean havePagesLeft = true;
		while (havePagesLeft) {
			Document doc = getDocumentStripped(driver.getPageSource());
			documents.add(doc);
			logger.info("Got document {}", driver.getCurrentUrl());
			
			exec.submit(new Miner(doc));
			
			List<WebElement> followingPaginationLink = driver
					.findElements(By.xpath("//ul[@class='pagination']/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a"));
			
			if (!followingPaginationLink.isEmpty()) {
				WebElement nextPageLink = followingPaginationLink.get(0);
				nextPageLink.click();
			} else
				havePagesLeft = false;
			
			Thread.sleep(CRAWLER_DELAY);
		}
		
		driver.quit();
		return documents;
	}

	private Document getDocumentStripped(String pageSource) {
		Document doc = Jsoup.parse(pageSource, startUrl);
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
		String startUrl1 = "https://carturesti.ro/raft/carte-109";
		String startUrl2 = "http://www.librariilealexandria.ro/carte";
		String startUrl3 = "https://www.libris.ro/carti";
		// TODO: use this
		// https://stackoverflow.com/questions/44912203/selenium-web-driver-java-element-is-not-clickable-at-point-36-72-other-el
		// for libris, and fix selection of next page from carturesti
		try {
			Fetcher fetcher = new Fetcher(startUrl1);
			fetcher.getMultiProductWebPages(startUrl1);
		} catch (MalformedURLException | InterruptedException e) {
			logger.debug("Problem regarding gathering pages: {}.", e.getStackTrace());
		}
	}
}
