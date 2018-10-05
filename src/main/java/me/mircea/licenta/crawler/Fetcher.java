package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.utils.HtmlUtil;
import me.mircea.licenta.miner.Miner;

/**
 * @author mircea
 * @brief The thinking when designing this class was that a fetcher is a tool
 *        that grabs all html pages from a vendor's website. This 1:1
 *        relationship is mandated by "crawler politeness", as in a website is
 *        designed for humans not "robots" and such I should be respectful and
 *        not make parallel requests and make queries with a small time gap.
 */

// TODO: maybe inherit autoclosable
public class Fetcher implements Runnable {
	private final String startUrl;
	private final String domain;
	private final WebDriver driver;

	//private final ExecutorService exec = Executors.newSingleThreadExecutor();
	//private final ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final int crawlDelay;

	private static final String CONFIG_FILENAME = "fetcher.properties";
	private static final Logger logger = LoggerFactory.getLogger(Fetcher.class);

	public Fetcher(String startUrl) throws IOException {
		this.startUrl = startUrl;
		this.domain = HtmlUtil.getDomainOfUrl(startUrl);

		InputStream configInputStream = getClass().getResourceAsStream(CONFIG_FILENAME);
		Properties properties = new Properties();
		properties.load(configInputStream);

		System.setProperty("webdriver.gecko.driver", properties.getProperty("webdriver_path"));
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE,
				properties.getProperty("browser_log_file", "browser.log"));

		this.crawlDelay = Integer.parseInt(properties.getProperty("crawlDelay"));

		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", 2); // Don't load images
		profile.setPreference("dom.popup_maximum", 0);
		profile.setPreference("privacy.popups.showBrowserMessage", false);
		FirefoxOptions opts = new FirefoxOptions();
		// opts.setHeadless(true);
		opts.setProfile(profile);

		this.driver = new FirefoxDriver(opts);
	}

	public Map<String, Document> getSingleProductPages(Document multiProductPage) {
		Map<String, Document> singleProductPages = new HashMap<>();

		Elements singlePageLinks = multiProductPage
				.select("[class*='produ']:has(img):has(a):not(:has([class*='produ']:has(img):has(a))) a[href]");
		for (Element link : singlePageLinks) {
			String url = link.absUrl("href");
			try {
				Document doc = HtmlUtil.sanitizeHtml(Jsoup.connect(url).get());
				singleProductPages.put(url, doc);
			} catch (IOException e) {
				logger.warn("Could not get page {}", url);
			}
		}

		return singleProductPages;
	}

	/**
	 * @param startMultiProductPage
	 *            First page that contains multiple products.
	 * @throws InterruptedException
	 */
	public void traverseMultiProductPages(final String startMultiProductPage) throws InterruptedException {
		String url = startMultiProductPage;
		driver.get(url);

		List<Future<?>> futures = new ArrayList<>();
		boolean havePagesLeft = true;
		while (havePagesLeft) {
			Document multiProductPage = getDocumentStripped(driver.getPageSource());
			Instant retrievedTime = Instant.now();
			logger.info("Got document {}", driver.getCurrentUrl());

			//exec.submit(new Miner(multiProductPage, retrievedTime, getSingleProductPages(multiProductPage)));
			CrawlerStart.executor.submit(new Miner(multiProductPage, retrievedTime, getSingleProductPages(multiProductPage)));
			
			closePopups();
			
			havePagesLeft = visitNextPage();
		}
		//
		driver.quit();
		//exec.shutdown();
		//exec.awaitTermination(1, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		try {
			this.traverseMultiProductPages(startUrl);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Thread was interrupted {}", e.getMessage());
		} finally {
			driver.quit();
		}
	}

	private Document getDocumentStripped(String pageSource) {
		Document doc = Jsoup.parse(pageSource, startUrl);
		return HtmlUtil.sanitizeHtml(doc);
	}

	private boolean isWorthVisiting(String url) throws MalformedURLException {
		return HtmlUtil.getDomainOfUrl(url).equals(domain) && url.startsWith(startUrl);
	}

	private boolean visitNextPage() throws InterruptedException {
		List<WebElement> followingPaginationLink = driver.findElements(By.xpath(
				"//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a"));
		
		if (!followingPaginationLink.isEmpty()) {
			WebElement nextPageLink = followingPaginationLink.get(0);
			
			waitForElementToAppear(nextPageLink, 2, "Pagination link was not visible in 2 seconds");
			nextPageLink.click();
			Thread.sleep(crawlDelay);
			return true;
		} else
			return false;
	}
	
	/**
	 * Close popups if they exist
	 */
	private void closePopups() {
		List<WebElement> popups = driver.findElements(By.cssSelector(".NewsClose[onclick]"));
		if (!popups.isEmpty()) {
			for (WebElement popup : popups) {
				try {
					popup.click();
				} catch (StaleElementReferenceException e) {
					logger.debug("Clicked popup reference went stale.");
				}
			}
		}
	}
	
	private void waitForElementToAppear(WebElement element, long timeOutInSeconds, String timeoutMessage) {
		WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);
		wait.until(ExpectedConditions.visibilityOf(element));
	}
}
