package me.mircea.licenta.crawler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

import me.mircea.licenta.core.crawl.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.CrawlRequest;
import me.mircea.licenta.core.crawl.db.model.Page;
import me.mircea.licenta.core.crawl.db.model.PageType;
import me.mircea.licenta.core.parser.utils.CssUtil;
import me.mircea.licenta.core.parser.utils.HtmlUtil;

/**
 * @author mircea
 * @brief The thinking when designing this class was that a fetcher is a tool
 *        that grabs all html pages from a vendor's website. This 1:1
 *        relationship is mandated by "crawler politeness", as in a website is
 *        designed for humans not "robots" and such I should be respectful and
 *        not make parallel requests and make queries with a small time gap.
 */

public class BrowserFetcher implements Fetcher {
	private static final Map<String, Cookie> DOMAIN_COOKIES = new HashMap<>();
	private static final Logger logger = LoggerFactory.getLogger(BrowserFetcher.class);
	
	static {
		Cookie librisModalCookie = new Cookie.Builder("GCLIDSEEN", "whatever")
				.domain("libris.ro")
				.build();
		
		DOMAIN_COOKIES.put(librisModalCookie.getDomain(), librisModalCookie);
	}
	
	private final WebDriver driver;
	private final CrawlRequest request;
	
	public BrowserFetcher(CrawlRequest request) {
		this.request = request;
		
		Map<String, String> properties = this.request.getProperties();		
		System.setProperty("webdriver.gecko.driver", properties.get("browser_webdriver_path"));
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, properties.get("browser_log_file"));
		
		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", Integer.valueOf(properties.get("browser_load_images")));
		profile.setPreference("dom.popup_maximum", Integer.valueOf(properties.get("browser_popup_maximum")));
		profile.setPreference("privacy.popups.showBrowserMessage", Boolean.valueOf(properties.get("browser_popup_show_browser_message")));
		profile.setPreference("general.useragent.override", properties.get("user_agent"));
		
		FirefoxOptions opts = new FirefoxOptions();
		// opts.setHeadless(true);
		opts.setProfile(profile);
		opts.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
		this.driver = new FirefoxDriver(opts);
		this.driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);	
	}
	
	@Override
	public void run() {
		this.traverseMultiProductPages(this.request.getStartUrl());
	}
	
	private void traverseMultiProductPages(final String firstMultiProductPage) {
		driver.get(firstMultiProductPage);
		
		Cookie cookie = DOMAIN_COOKIES.get(this.request.getDomain());
		if(cookie != null)
			driver.manage().addCookie(cookie);
		
		boolean havePagesLeft = true;
		String previousShelfUrl = null;
		while (havePagesLeft) {
			Document shelfDoc = getDocumentStripped(driver.getPageSource());
			String shelfUrl = driver.getCurrentUrl();
			Instant retrievedTime = Instant.now();
			
			Page shelfPage = new Page(shelfUrl, retrievedTime, previousShelfUrl, PageType.SHELF);
			List<String> productUrls = getSingleProductPages(shelfDoc);
			List<Page> batchOfPages = productUrls.stream()
					.map(productUrl -> new Page(productUrl, retrievedTime, shelfUrl, PageType.PRODUCT))
					.collect(Collectors.toList());
			batchOfPages.add(shelfPage);
			CrawlDatabaseManager.instance.upsertManyPages(batchOfPages);
			
			previousShelfUrl = shelfUrl;
			logger.info("Got document {} at {}", shelfUrl, retrievedTime);
			havePagesLeft = visitNextPage();
		}
		driver.quit();
	}
	
	public List<String> getSingleProductPages(Document multiProductPage) {
		List<String> singleProductPages = new ArrayList<>();
		Elements singleBookElements = multiProductPage.select(CssUtil.makeLeafOfSelector("[class*='produ']:has(img):has(a)"));
		Elements links = new Elements();
		singleBookElements.stream().forEach(bookElement -> links.add(bookElement.selectFirst("a[href]")));
		
		for (Element link : links) {
			String url = link.absUrl("href");
			singleProductPages.add(url);
		}
		return singleProductPages;
	}
	
	private boolean visitNextPage() {
		List<WebElement> followingPaginationLink = driver.findElements(By.xpath(
				"//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a"));
		
		if (!followingPaginationLink.isEmpty()) {
			
			try {
				//TODO: this is hardcoded
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("Thread interrupted with {}", e);
				Thread.currentThread().interrupt();
			}
			WebElement nextPageLink = followingPaginationLink.get(0);
			
			final int MAX_WAIT_IN_SECONDS = 30;
			
			
			
			WebDriverWait pageLoadWait = new WebDriverWait(driver, MAX_WAIT_IN_SECONDS);
			//nextPageLink = pageLoadWait.until(isTrue)
			
			WebDriverWait clickWait = new WebDriverWait(driver, MAX_WAIT_IN_SECONDS);
			nextPageLink = clickWait.until(ExpectedConditions.elementToBeClickable(nextPageLink));
			WebDriverWait visibleWait = new WebDriverWait(driver, MAX_WAIT_IN_SECONDS);
			nextPageLink = visibleWait.until(ExpectedConditions.visibilityOf(nextPageLink));
			//nextPageLink = clickWait.until(isTrue)
			
			
			nextPageLink.click();
			return true;
		} else
			return false;
	}

	private Document getDocumentStripped(String pageSource) {
		Document doc = Jsoup.parse(pageSource, this.request.getStartUrl());
		return HtmlUtil.sanitizeHtml(doc);
	}
}
