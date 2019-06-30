package me.mircea.licenta.crawler.impl;

import com.google.common.base.Preconditions;
import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.RobotDefaults;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobStatus;
import me.mircea.licenta.core.crawl.db.model.Page;
import me.mircea.licenta.core.crawl.db.model.PageType;
import me.mircea.licenta.core.parser.utils.CssUtil;
import me.mircea.licenta.core.parser.utils.HtmlUtil;
import me.mircea.licenta.crawler.Crawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author mircea
 * The thinking when designing this class was that a fetcher is a tool
 * that grabs all html pages from a vendor's website. This 1:1
 * relationship is mandated by "crawler politeness", as in a website is
 * designed for humans not "robots" and such I should be respectful and
 * not make parallel requests and make queries with a small time gap.
 */

public class BrowserCrawler extends Crawler {
	private static final Logger LOGGER = LoggerFactory.getLogger(BrowserCrawler.class);
	private static final String NEXT_PAGE_LINK_XPATH_SELECTOR = "//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a";
	private static final int ELEMENT_CLICK_INTERCEPTED_X_OFFSET = 0;
	private static final int ELEMENT_CLICK_INTERCEPTED_Y_OFFSET = -100;
	private static final int MOVE_WAIT_IN_SECONDS = 3;

	private static final String WEBDRIVER_GECKO_DRIVER = "webdriver.gecko.driver";

	private static final String BROWSER_IMAGE_BEHAVIOUR_PREFERENCE = "permissions.default.image";
	private static final String BROWSER_DOM_POPUPS_PREFERENCE = "dom.popup_maximum";
	private static final String BROWSER_POPUPS_MESSAGE_PREFERENCE = "privacy.popups.showBrowserMessage";
	private static final String BROWSER_USER_AGENT_PREFERENCE = "general.useragent.override";

	private static final String CONFIG_FILE_WEBDRIVER_PATH_KEY = "browser_webdriver_path";
	private static final String CONFIG_FILE_BROWSER_LOG_FILE_PATH_KEY = "browser_log_file";
	private static final String CONFIG_FILE_BROWSER_LOAD_IMAGES_KEY = "browser_load_images";
	private static final String CONFIG_FILE_BROWSER_POPUP_MAXIMUM = "browser_popup_maximum";
	private static final String CONFIG_FILE_BROWSER_POPUP_SHOW_BROWSER_MESSAGE = "browser_popup_show_browser_message";

	private static final String CONFIG_FILE_BROWSER_HEADLESS = "browser_headless";

	private static final int WEB_DRIVER_IMPLICIT_WAIT_AMOUNT = 10;

	private final WebDriver driver;
	
	public BrowserCrawler(Job job) {
		super(job);

		System.setProperty(WEBDRIVER_GECKO_DRIVER, BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_WEBDRIVER_PATH_KEY));
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_BROWSER_LOG_FILE_PATH_KEY));
		
		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference(BROWSER_USER_AGENT_PREFERENCE, RobotDefaults.getUserAgent());

		profile.setPreference(BROWSER_IMAGE_BEHAVIOUR_PREFERENCE, Integer.valueOf(BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_BROWSER_LOAD_IMAGES_KEY)));
		profile.setPreference(BROWSER_DOM_POPUPS_PREFERENCE, Integer.valueOf(BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_BROWSER_POPUP_MAXIMUM)));
		profile.setPreference(BROWSER_POPUPS_MESSAGE_PREFERENCE, Boolean.valueOf(BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_BROWSER_POPUP_SHOW_BROWSER_MESSAGE)));

		FirefoxOptions opts = new FirefoxOptions();
		opts.setHeadless(Boolean.valueOf(BrowserCrawlerSettingsUtil.getSetting(CONFIG_FILE_BROWSER_HEADLESS)));
		opts.setProfile(profile);
		opts.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
		this.driver = new FirefoxDriver(opts);
		this.driver.manage().timeouts().implicitlyWait(WEB_DRIVER_IMPLICIT_WAIT_AMOUNT, TimeUnit.SECONDS);
	}
	
	@Override
	public void run() {
		startCrawlJob();
		crawl(job.getSeeds());
		finishCrawlJob();
	}

	/**
	 * @param seeds Collection of URLs that provide a starting point.
	 * Crawl starting from a collection of seeds.
	 */
	private void crawl(Collection<String> seeds) {
		for (String seed: seeds) {
			traverseMultiProductPage(seed);
		}

		LOGGER.info("Quitting the automated browser...");
		driver.close();
	}

	/**
	 * @param firstMultiProductPage
	 * Exhaust collection starting from seed.
	 */
	private void traverseMultiProductPage(final String firstMultiProductPage) {
		LOGGER.info("Following pagination links starting from {}", firstMultiProductPage);
		driver.get(firstMultiProductPage);

		boolean havePagesLeft = true;
		String previousShelfUrl = null;
		while (havePagesLeft) {
			Document shelfDoc = getDocumentStripped(driver.getPageSource());
			String shelfUrl = driver.getCurrentUrl();
			Instant retrievedTime = Instant.now();
			
			Page shelfPage = new Page(shelfUrl, previousShelfUrl, PageType.SHELF, retrievedTime);
			List<String> productUrls = getSingleProductPages(shelfDoc);
			LOGGER.info("Got {} product urls", productUrls.size());

			List<Page> batchOfPages = productUrls.stream()
					.map(productUrl -> new Page(productUrl, shelfUrl, PageType.PRODUCT, retrievedTime))
					.collect(Collectors.toList());
			batchOfPages.add(shelfPage);
			CrawlDatabaseManager.instance.upsertManyPages(batchOfPages);
			
			previousShelfUrl = shelfUrl;
			LOGGER.info("Got document {} at {}", shelfUrl, retrievedTime);
			havePagesLeft = visitNextPage();
		}
	}
	
	public List<String> getSingleProductPages(Document multiProductPage) {
		List<String> singleProductPages = new ArrayList<>();
		Elements singleBookElements = multiProductPage.select(CssUtil.makeLeafOfSelector("[class*='produ']:has(img):has(a)"));
		Elements links = new Elements();
		singleBookElements.forEach(bookElement -> links.add(bookElement.selectFirst("a[href]")));
		
		for (Element link : links) {
			String url = link.absUrl("href");
			singleProductPages.add(url);
		}
		return singleProductPages;
	}

	private Document getDocumentStripped(String url) {
		Preconditions.checkNotNull(url);
		Document doc = Jsoup.parse(url, this.job.getDomain());
		return HtmlUtil.sanitizeHtml(doc);
	}
	
	private boolean visitNextPage() {
		final int MAX_EXPLICIT_WAIT_IN_SECONDS = 30;

		if (!driver.findElements(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR)).isEmpty()) {
			try {
				WebElement nextPageLink = driver.findElement(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR));

				new WebDriverWait(driver, MAX_EXPLICIT_WAIT_IN_SECONDS)
						.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR)));
				new WebDriverWait(driver, MAX_EXPLICIT_WAIT_IN_SECONDS)
						.until(ExpectedConditions.elementToBeClickable(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR)));

				tryToClickElement(nextPageLink);
			} catch (RuntimeException e) {
				LOGGER.error("Visiting next link threw {}", e);
			}

			return true;
		} else {
			return false;
		}
	}

	private void tryToClickElement(WebElement nextPageLink) {
		final int MAX_CLICK_TRIES = 10;

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", nextPageLink);
		for (int tryNumber = 0; tryNumber < MAX_CLICK_TRIES; ++tryNumber) {
			try {
				waitForElementToBeVisibleAndClickable();

				nextPageLink = driver.findElement(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR));
				nextPageLink.click();
				return;
			} catch (ElementClickInterceptedException e) {
				LOGGER.warn("Tried to click element but something is probably obscuring it at the edge of the viewport. Will move by implicit offset.");
				String scrollByOffsetsScript = String.format("scrollBy(%d, %d);", ELEMENT_CLICK_INTERCEPTED_X_OFFSET, ELEMENT_CLICK_INTERCEPTED_Y_OFFSET);
				((JavascriptExecutor)driver).executeScript(scrollByOffsetsScript);
			}
		}
	}

	private void waitForElementToBeVisibleAndClickable() {
		new WebDriverWait(driver, MOVE_WAIT_IN_SECONDS)
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR)));
		new WebDriverWait(driver, MOVE_WAIT_IN_SECONDS)
				.until(ExpectedConditions.elementToBeClickable(By.xpath(NEXT_PAGE_LINK_XPATH_SELECTOR)));
	}
}
