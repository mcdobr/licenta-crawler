package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

import crawlercommons.robots.BaseRobotRules;
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

public class BrowserFetcher implements Fetcher {
	private static final String PROPERTIES_FILENAME = "fetcher_default.properties";
	private static final Map<String, Cookie> DOMAIN_COOKIES = new HashMap<>();
	private static final Logger logger = LoggerFactory.getLogger(BrowserFetcher.class);
	
	static {
		Cookie librisModalCookie = new Cookie.Builder("GCLIDSEEN", "whatever")
				.domain("libris.ro")
				.build();
		
		DOMAIN_COOKIES.put(librisModalCookie.getDomain(), librisModalCookie);
	}
	
	private final String startUrl;
	private final String domain;
	private final WebDriver driver;
	private Map<String, String> properties;
	private BaseRobotRules crawlRules;
	
	public BrowserFetcher(String startUrl) throws IOException {
		this.startUrl = startUrl;
		this.domain = HtmlUtil.getDomainOfUrl(startUrl);

		InputStream propertiesInputStream = getClass().getResourceAsStream("/" + PROPERTIES_FILENAME);
		this.properties = readPropertiesFile(propertiesInputStream);
		this.crawlRules = readRobotsFile(this.startUrl, this.properties);
		
		System.setProperty("webdriver.gecko.driver", this.properties.get("browser_webdriver_path"));
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, this.properties.get("browser_log_file"));
		
		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", Integer.valueOf(this.properties.get("browser_load_images")));
		profile.setPreference("dom.popup_maximum", Integer.valueOf(this.properties.get("browser_popup_maximum")));
		profile.setPreference("privacy.popups.showBrowserMessage", Boolean.valueOf(this.properties.get("browser_popup_show_browser_message")));
		
		FirefoxOptions opts = new FirefoxOptions();
		// opts.setHeadless(true);
		opts.setProfile(profile);
		opts.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
		this.driver = new FirefoxDriver(opts);
		this.driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);	
	}
	
	@Override
	public void run() {
		this.traverseMultiProductPages(startUrl);
	}
	
	/**
	 * @param startMultiProductPage
	 *            First page that contains multiple products.
	 * @throws InterruptedException
	 * @throws MalformedURLException 
	 */
	private void traverseMultiProductPages(final String startMultiProductPage) {
		String url = startMultiProductPage;
		driver.get(url);
		
		Cookie cookie = DOMAIN_COOKIES.get(this.domain);
		if(cookie != null)
			driver.manage().addCookie(cookie);
		
		boolean havePagesLeft = true;
		while (havePagesLeft) {
			Document multiProductPage = getDocumentStripped(driver.getPageSource());
			Instant retrievedTime = Instant.now();
			logger.info("Got document {}", driver.getCurrentUrl());
			
			try {
				Miner miner = new Miner(multiProductPage, retrievedTime, getSingleProductPages(multiProductPage));
				CrawlerStart.executor.submit(miner);
			} catch (MalformedURLException e) {
				logger.debug("Could not start extracting because of corrupt urls {}", e);
			}
			
			havePagesLeft = visitNextPage();
		}
		driver.quit();
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
	
	private boolean visitNextPage() {
		List<WebElement> followingPaginationLink = driver.findElements(By.xpath(
				"//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a"));
		
		if (!followingPaginationLink.isEmpty()) {
			WebElement nextPageLink = followingPaginationLink.get(0);
			
			WebDriverWait clickWait = new WebDriverWait(driver, 20);
			clickWait.until(ExpectedConditions.elementToBeClickable(nextPageLink));

			nextPageLink.click();
			return true;
		} else
			return false;
	}

	private Document getDocumentStripped(String pageSource) {
		Document doc = Jsoup.parse(pageSource, startUrl);
		return HtmlUtil.sanitizeHtml(doc);
	}
}
