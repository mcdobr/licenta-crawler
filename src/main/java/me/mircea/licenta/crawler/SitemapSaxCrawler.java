package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import me.mircea.licenta.core.crawl.db.model.Job;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import crawlercommons.sitemaps.AbstractSiteMap;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapIndex;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import crawlercommons.sitemaps.UnknownFormatException;
import me.mircea.licenta.core.crawl.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.model.Page;

public class SitemapSaxCrawler implements Crawler {
	private static final Logger logger = LoggerFactory.getLogger(SitemapSaxCrawler.class);

	private final Job job;
	
	public SitemapSaxCrawler(Job job) {
		this.job = job;
	}
	
	@Override
	public void run() {
		try {
			List<SiteMapURL> sitemapLinks = getLinksToBeCrawled();
			Instant retrievedTime = Instant.now();
			
			List<Page> pages = sitemapLinks.stream()
					.map(link -> new Page(link.getUrl().toString(), retrievedTime, "sitemap"))
					.collect(Collectors.toList());
			
			CrawlDatabaseManager.instance.upsertManyPages(pages);
		} catch (IOException e) {
			logger.info("Could not handle a connection: {}", e.getMessage());
		} catch (UnknownFormatException e) {
			logger.info("Could not parse a sitemap: {}", e.getMessage());
		}
	}
	
	private List<SiteMapURL> getLinksToBeCrawled() throws IOException, UnknownFormatException {
		List<SiteMapURL> linksToBeCrawled = new ArrayList<>();
		
		Queue<String> siteMapQueue = new LinkedList<>();
		siteMapQueue.addAll(job.getRobotRules().getSitemaps());
		
		while (!siteMapQueue.isEmpty()) {
			URL queueFrontUrl = new URL(siteMapQueue.poll());
			
			Optional<HttpURLConnection> optionalConnection = followPossibleRedirects(queueFrontUrl);
			if (!optionalConnection.isPresent())
				continue;
			else {
				HttpURLConnection connection = optionalConnection.get();
				
				InputStream inputStream = connection.getInputStream();
				if ("gzip".equals(connection.getContentEncoding()) || "x-gzip".equals(connection.getContentEncoding())) {
					inputStream = new GZIPInputStream(inputStream);
				}
				byte[] content = IOUtils.toByteArray(inputStream);
	
				SiteMapParser siteMapParser = new SiteMapParser(false);
				AbstractSiteMap sitemap = siteMapParser.parseSiteMap(content, connection.getURL());
				
				if (sitemap.isIndex()) {
					List<String> indexedSiteMaps = ((SiteMapIndex)sitemap).getSitemaps()
							.stream()
							.map(sitemapUrl -> sitemapUrl.getUrl().toString())
							.collect(Collectors.toList());
					siteMapQueue.addAll(indexedSiteMaps);
				} else {
					SiteMap concreteSiteMap = ((SiteMap)sitemap);
					linksToBeCrawled.addAll(concreteSiteMap.getSiteMapUrls());
					logger.info("Discovered {} urls", concreteSiteMap.getSiteMapUrls().size());
				}
			}
		}
		
		return linksToBeCrawled;
	}
	
	/**
	 * Java URLConnection API does not follow redirects from HTTP to HTTPS or vice-versa
	 * even if follow redirects is set to true. So this function manually redirects
	 * a number of times less than a hardcoded MAX_REDIRECTS.
	 */
	private Optional<HttpURLConnection> followPossibleRedirects(final URL originalUrl) throws IOException {
		URL url = originalUrl;
		
		HttpURLConnection connection;
		boolean redirect = false;
		int redirectCounter = 0;
		final int MAX_REDIRECTS = 10;
		do {
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(50 * 1000);
			connection.setReadTimeout(50 * 1000);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestProperty("User-Agent", Job.getDefault("user_agent"));
			connection.connect();
			
			int httpStatus = connection.getResponseCode();
			redirect = (httpStatus == HttpURLConnection.HTTP_MOVED_PERM) || (httpStatus == HttpURLConnection.HTTP_MOVED_TEMP) || (httpStatus == HttpURLConnection.HTTP_SEE_OTHER);
			
			if (redirect) {
				String location = URLDecoder.decode(connection.getHeaderField("Location"), "UTF-8");
				URL next = new URL(url, location);
				url = next;
			}
		} while (redirect && redirectCounter < MAX_REDIRECTS);
		
		if (redirectCounter >= MAX_REDIRECTS) {
			logger.warn("Redirects number exceeded maximum redirects on url {}", originalUrl);
			return Optional.empty();
		}
		
		return Optional.of(connection);
	}
}
