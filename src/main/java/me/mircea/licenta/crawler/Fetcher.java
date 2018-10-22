package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.utils.CssUtil;
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
	private static final String CONFIG_FILENAME = "fetcher.properties";
	private static final Logger logger = LoggerFactory.getLogger(Fetcher.class);
	private static final Map<String, Cookie> domainCookies = new HashMap<>();
	
	static {
		Cookie librisModalCookie = new Cookie.Builder("GCLIDSEEN", "whatever")
				.domain("libris.ro")
				.build();
		
		domainCookies.put(librisModalCookie.getDomain(), librisModalCookie);
	}
	
	private final String startUrl;
	private final String domain;
	private final WebDriver driver;
	private final int crawlDelay;
	
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
		opts.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
		this.driver = new FirefoxDriver(opts);
		
	}

	public Map<String, Document> getSingleProductPages(Document multiProductPage) {
		Map<String, Document> singleProductPages = new HashMap<>();
		Elements singleBookElements = multiProductPage.select(CssUtil.makeLeafOfSelector("[class*='produ']:has(img):has(a)"));
		Elements links = new Elements();
		singleBookElements.stream().forEach(bookElement -> links.add(bookElement.selectFirst("a[href]")));
		
		for (Element link : links) {
			String url = link.absUrl("href");
			
			final int MAX_TRIES = 1;
			Document bookPage = null;
			for (int i = 0; i < MAX_TRIES; ++i) {
				try {
					bookPage = HtmlUtil.sanitizeHtml(Jsoup.connect(url).get());
					break;
				} catch (SocketTimeoutException e) {
					logger.warn("Socket timed out on {}", url);
				} catch (IOException e) {
					logger.warn("Could not get page {}", url);
				}
			}
			
			if (bookPage != null) {
				singleProductPages.put(url, bookPage);
			} else {
				logger.warn("Could not get page {} after {} tries", url, MAX_TRIES);
			}
			
		}

		return singleProductPages;
	}

	/**
	 * @param startMultiProductPage
	 *            First page that contains multiple products.
	 * @throws InterruptedException
	 * @throws MalformedURLException 
	 */
	public void traverseMultiProductPages(final String startMultiProductPage) throws InterruptedException {
		String url = startMultiProductPage;
		driver.get(url);
		
		Cookie cookie = domainCookies.get(this.domain);
		if(cookie != null)
			driver.manage().addCookie(cookie);
		
		boolean havePagesLeft = true;
		while (havePagesLeft) {
			Document multiProductPage = getDocumentStripped(driver.getPageSource());
			Instant retrievedTime = Instant.now();
			logger.info("Got document {}", driver.getCurrentUrl());
			
			CrawlerStart.executor.submit(new Miner(multiProductPage, retrievedTime, getSingleProductPages(multiProductPage)));
			havePagesLeft = visitNextPage();
		}
		//
		driver.quit();
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
			
			waitForElementToAppear(nextPageLink, 10, "Pagination link was not visible in 10 seconds");
			nextPageLink.click();
			//TODO: maybe implicit wait
			Thread.sleep(crawlDelay);
			return true;
		} else
			return false;
	}
	
	private void waitForElementToAppear(WebElement element, long timeOutInSeconds, String timeoutMessage) {
		WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);
		wait.until(ExpectedConditions.visibilityOf(element));
	}
}
