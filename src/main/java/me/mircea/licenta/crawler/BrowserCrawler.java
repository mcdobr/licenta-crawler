package me.mircea.licenta.crawler;

import com.google.common.base.Preconditions;
import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.Page;
import me.mircea.licenta.core.crawl.db.model.PageType;
import me.mircea.licenta.core.parser.utils.CssUtil;
import me.mircea.licenta.core.parser.utils.HtmlUtil;
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

public class BrowserCrawler implements Crawler {
	private static final Logger LOGGER = LoggerFactory.getLogger(BrowserCrawler.class);

	private final WebDriver driver;
	private final Job job;
	
	public BrowserCrawler(Job job) {
		this.job = job;

		System.setProperty("webdriver.gecko.driver", Job.getDefault("browser_webdriver_path"));
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, Job.getDefault("browser_log_file"));
		
		FirefoxProfile profile = new FirefoxProfile();
		profile.setPreference("permissions.default.image", Integer.valueOf(Job.getDefault("browser_load_images")));
		profile.setPreference("dom.popup_maximum", Integer.valueOf(Job.getDefault("browser_popup_maximum")));
		profile.setPreference("privacy.popups.showBrowserMessage", Boolean.valueOf(Job.getDefault("browser_popup_show_browser_message")));
		profile.setPreference("general.useragent.override", Job.getDefault("user_agent"));
		
		FirefoxOptions opts = new FirefoxOptions();
		opts.setHeadless(Boolean.valueOf(Job.getDefault("browser_headless")));
		opts.setProfile(profile);
		opts.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
		this.driver = new FirefoxDriver(opts);
		this.driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
	}
	
	@Override
	public void run() {
		this.traverseMultiProductPages(this.job.getSeed());
	}
	
	private void traverseMultiProductPages(final String firstMultiProductPage) {
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


		LOGGER.info("Quitting the automated browser...");
		driver.close();
		driver.quit();
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
		Document doc = Jsoup.parse(url, this.job.getSeed());
		return HtmlUtil.sanitizeHtml(doc);
	}
	
	private boolean visitNextPage() {
        final int MAX_WAIT_IN_SECONDS = 5;
	    String nextPageLinkXpath = "//ul[contains(@class,'pagination')]/li[contains(@class, 'active')]/following-sibling::li[not(contains(@class, 'disabled'))][1]/a";

	    if (!driver.findElements(By.xpath(nextPageLinkXpath)).isEmpty()) {
            new WebDriverWait(driver, MAX_WAIT_IN_SECONDS)
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(nextPageLinkXpath)));
            new WebDriverWait(driver, MAX_WAIT_IN_SECONDS)
                    .until(ExpectedConditions.elementToBeClickable(By.xpath(nextPageLinkXpath)));


            WebElement nextPageLink = driver.findElement(By.xpath(nextPageLinkXpath));
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript("arguments[0].click();", nextPageLink);
            return true;
        } else {
	        return false;
        }
	}
}
