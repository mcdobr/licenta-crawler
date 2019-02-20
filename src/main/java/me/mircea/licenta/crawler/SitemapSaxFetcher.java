package me.mircea.licenta.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

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
import me.mircea.licenta.core.crawl.CrawlRequest;
import me.mircea.licenta.core.crawl.db.model.Page;

public class SitemapSaxFetcher implements Fetcher {
	private static final Logger logger = LoggerFactory.getLogger(SitemapSaxFetcher.class);

	private final CrawlRequest request;
	
	public SitemapSaxFetcher(CrawlRequest request) {
		this.request = request;
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
		siteMapQueue.addAll(request.getRobotRules().getSitemaps());
		
		while (!siteMapQueue.isEmpty()) {
			URL url = new URL(siteMapQueue.poll());

			// Get the file manually
			URLConnection connection = url.openConnection();
			connection.setRequestProperty("User-Agent", request.getProperties().get("user_agent"));
			connection.connect();
			InputStream inputStream = connection.getInputStream();
			if ("gzip".equals(connection.getContentEncoding()) || "x-gzip".equals(connection.getContentEncoding())) {
				inputStream = new GZIPInputStream(inputStream);
			}
			byte[] content = IOUtils.toByteArray(inputStream);

			SiteMapParser siteMapParser = new SiteMapParser(false);
			AbstractSiteMap sitemap = siteMapParser.parseSiteMap(content, url);
			
			if (sitemap.isIndex()) {
				List<String> indexedSiteMaps = ((SiteMapIndex)sitemap).getSitemaps()
						.stream()
						.map(sitemapUrl -> sitemapUrl.getUrl().toString())
						.collect(Collectors.toList());
				siteMapQueue.addAll(indexedSiteMaps);
			} else {
				linksToBeCrawled.addAll(((SiteMap)sitemap).getSiteMapUrls());
			}
		}
		
		return linksToBeCrawled;
	}
	
}
