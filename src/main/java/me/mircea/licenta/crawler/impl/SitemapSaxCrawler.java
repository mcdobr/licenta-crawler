package me.mircea.licenta.crawler.impl;

import crawlercommons.sitemaps.*;
import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.RobotDefaults;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.Page;
import me.mircea.licenta.crawler.Crawler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * This class is designed to handle a host that links to sitemaps from the robots.txt.
 * @author mircea
 */
public class SitemapSaxCrawler extends Crawler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SitemapSaxCrawler.class);
    private static final int HTTP_CONNECT_TIMEOUT_IN_MILLISECONDS = 50_000;
    private static final int HTTP_READ_TIMEOUT_IN_MILLISECONDS = 50_000;

    public SitemapSaxCrawler(Job job) {
        super(job);
    }

    @Override
    public void run() {
        startCrawlJob();
        try {
            parseSitemaps();
        } catch (IOException e) {
            LOGGER.info("Could not handle a connection: {}", e.getMessage());
        } catch (UnknownFormatException e) {
            LOGGER.info("Could not parse a sitemap: {}", e.getMessage());
        }
        finishCrawlJob();
    }

    private void parseSitemaps() throws IOException, UnknownFormatException {
        Queue<String> siteMapQueue = new LinkedList<>(job.getRobotRules().getSitemaps());

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
                    List<String> indexedSiteMaps = ((SiteMapIndex) sitemap).getSitemaps()
                            .stream()
                            .map(sitemapUrl -> sitemapUrl.getUrl().toString())
                            .collect(Collectors.toList());
                    siteMapQueue.addAll(indexedSiteMaps);
                } else {
                    SiteMap concreteSiteMap = ((SiteMap) sitemap);

                    List<Page> pagesToBeUpserted = concreteSiteMap.getSiteMapUrls().stream()
                            .map(link -> new Page(link.getUrl().toString(), "sitemap", Instant.now()))
                            .collect(Collectors.toList());

                    LOGGER.info("Discovered {} urls about to be upserted", concreteSiteMap.getSiteMapUrls().size());
                    CrawlDatabaseManager.instance.upsertManyPages(pagesToBeUpserted);
                }
            }
        }
        LOGGER.info("Finished parsing sitemaps on {}", job.getDomain());
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
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_IN_MILLISECONDS);
            connection.setReadTimeout(HTTP_READ_TIMEOUT_IN_MILLISECONDS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", RobotDefaults.getDefault("user_agent"));
            connection.connect();

            int httpStatus = connection.getResponseCode();
            redirect = shouldRedirect(httpStatus);

            if (redirect) {
                String location = URLDecoder.decode(connection.getHeaderField("Location"), "UTF-8");
                url = new URL(url, location);
            }

            ++redirectCounter;
        } while (redirect && redirectCounter < MAX_REDIRECTS);

        if (redirectCounter >= MAX_REDIRECTS) {
            LOGGER.warn("Redirects number exceeded maximum redirects on url {}", originalUrl);
            return Optional.empty();
        }

        return Optional.of(connection);
    }

    /**
     * @param httpStatus
     * @return Decision if request should be retried.
     */
    private boolean shouldRedirect(int httpStatus) {
        return (httpStatus == HttpURLConnection.HTTP_MOVED_PERM)
                || (httpStatus == HttpURLConnection.HTTP_MOVED_TEMP)
                || (httpStatus == HttpURLConnection.HTTP_SEE_OTHER);
    }
}
