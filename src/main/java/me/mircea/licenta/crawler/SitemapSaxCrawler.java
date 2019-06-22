package me.mircea.licenta.crawler;

import crawlercommons.sitemaps.*;
import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.Page;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class SitemapSaxCrawler implements Crawler {
	private static final Logger logger = LoggerFactory.getLogger(SitemapSaxCrawler.class);

	private final Job job;

	public SitemapSaxCrawler(Job job) {
		this.job = job;
	}

	@Override
	public void run() {
		try {
			parseSitemaps();
		} catch (IOException e) {
			logger.info("Could not handle a connection: {}", e.getMessage());
		} catch (UnknownFormatException e) {
			logger.info("Could not parse a sitemap: {}", e.getMessage());
		}
	}

	public void parseSitemaps() throws IOException, UnknownFormatException {
		Queue<String> siteMapQueue = new LinkedList<>();
		siteMapQueue.addAll(job.getRobotRules().getSitemaps());

		while (!siteMapQueue.isEmpty()) {
			URL queueFrontUrl = new URL(siteMapQueue.poll());

			Optional<HttpURLConnection> possibleConnection = followPossibleRedirects(queueFrontUrl);
			if (possibleConnection.isPresent()) {
				HttpURLConnection connection = possibleConnection.get();

				InputStream inputStream = connection.getInputStream();
				if ("gzip".equals(connection.getContentEncoding()) || "x-gzip".equals(connection.getContentEncoding())
				|| "application/gzip".equals(connection.getContentType()) || "application/x-gzip".equals(connection.getContentType())) {
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

					List<Page> pagesToBeUpserted = concreteSiteMap.getSiteMapUrls().stream()
							.map(link -> new Page(link.getUrl().toString(), "sitemap", Instant.now()))
							.collect(Collectors.toList());

					logger.info("Discovered {} urls about to be upserted", concreteSiteMap.getSiteMapUrls().size());
					CrawlDatabaseManager.instance.upsertManyPages(pagesToBeUpserted);
				}
			}
		}
		logger.info("Finished parsing sitemaps on {}", job.getDomain());
	}

	/**
	 * Follow redirects if required.
	 */
	private Optional<HttpURLConnection> followPossibleRedirects(final URL originalUrl) throws IOException {
		URL url = originalUrl;

		HttpURLConnection connection;
		boolean redirect;
		int redirectCounter = 0;
		final int MAX_REDIRECTS = 5;
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
				url = new URL(url, location);
			}

			++redirectCounter;
		} while (redirect && redirectCounter < MAX_REDIRECTS);

		if (redirectCounter >= MAX_REDIRECTS) {
			logger.warn("Redirects number exceeded maximum redirects on url {}", originalUrl);
			return Optional.empty();
		}

		return Optional.of(connection);
	}
}
