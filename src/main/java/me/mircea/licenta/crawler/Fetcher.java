package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InternetDomainName;

import me.mircea.licenta.core.utils.HtmlHelper;
import me.mircea.licenta.miner.Miner;

/**
 * @author mircea
 * @brief The thinking when designing this class was that a fetcher is a tool
 *        that grabs all html pages from a vendor's website. This 1:1
 *        relationship is mandated by "crawler politeness", as in a website is
 *        designed for humans not "robots" and such I should be respectful and
 *        not make parallel requests and make queries with a small time gap.
 */
public class Fetcher implements Runnable {
	private String startUrl;
	private String domain;
	private final WebDriver driver;
	private final ExecutorService exec = Executors.newCachedThreadPool();
	private final int crawlDelay;
	
	private static final String CONFIG_FILENAME = "fetcher.properties";
	private static final Logger logger = LoggerFactory.getLogger(Fetcher.class);

	public Fetcher(String startUrl) throws IOException {
		this.startUrl = startUrl;
		this.domain = getDomainOfUrl(startUrl);
		
		InputStream configInputStream = getClass().getResourceAsStream(CONFIG_FILENAME);
		Properties properties = new Properties();
		properties.load(configInputStream);
		
		System.setProperty("webdriver.gecko.driver", properties.getProperty("webdriver_path"));
		this.crawlDelay = Integer.parseInt(properties.getProperty("crawlDelay"));
		
		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", 2); // Don't load images
		profile.setPreference("dom.popup_maximum", 0);
		profile.setPreference("privacy.popups.showBrowserMessage", false);
		FirefoxOptions opts = new FirefoxOptions();
		//opts.setHeadless(true);
		opts.setProfile(profile);

		this.driver = new FirefoxDriver(opts);
	}

	/**
	 * @param startMultiProductPage
	 *            First page that contains multiple products.
	 * @return A set of the documents obtained by following pagination links.
	 * @throws InterruptedException
	 */
	public Set<Document> traverseMultiProductPages(final String startMultiProductPage) throws InterruptedException {
		Set<Document> documents = new HashSet<>();

		String url = startMultiProductPage;
		driver.get(url);

		boolean havePagesLeft = true;
		while (havePagesLeft) {
			Document doc = getDocumentStripped(driver.getPageSource());
			Instant retrievedTime = Instant.now();
			documents.add(doc);
			logger.info("Got document {}", driver.getCurrentUrl());

			exec.submit(new Miner(doc, retrievedTime));

			// Go to next pagination page
			List<WebElement> followingPaginationLink = driver.findElements(By.xpath(
					"//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a"));

			if (!followingPaginationLink.isEmpty()) {
				WebElement nextPageLink = followingPaginationLink.get(0);
				nextPageLink.click();
			} else
				havePagesLeft = false;

			Thread.sleep(crawlDelay);
		}

		driver.quit();
		return documents;
	}
	
	private Document getDocumentStripped(String pageSource) {
		Document doc = Jsoup.parse(pageSource, startUrl);
		return HtmlHelper.sanitizeHtml(doc);
	}

	private String getDomainOfUrl(String url) throws MalformedURLException {
		return InternetDomainName.from(new URL(url).getHost()).topPrivateDomain().toString();
	}

	private boolean isWorthVisiting(String url) throws MalformedURLException {
		return getDomainOfUrl(url).equals(domain) && url.startsWith(startUrl);
	}

	@Override
	public void run() {
		try {
			this.traverseMultiProductPages(startUrl);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Thread was interrupted {}", e.getMessage());
		}
		driver.quit();
	}
}
