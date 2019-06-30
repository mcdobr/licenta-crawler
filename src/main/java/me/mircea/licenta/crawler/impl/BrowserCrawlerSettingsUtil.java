package me.mircea.licenta.crawler.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author mircea
 * This class was designed with the hope to keep statistics separate from
 * the actual web scraping.
 */
class BrowserCrawlerSettingsUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserCrawlerSettingsUtil.class);
    private static Map<String, String> crawlerSettings;

    static {
        try {
            final String crawlerSettingsFile = "browserDefault.properties";
            final InputStream crawlerSettingsInputStream = BrowserCrawlerSettingsUtil.class.getResourceAsStream("/" + crawlerSettingsFile);

            crawlerSettings = new HashMap<>();
            Properties persistedProps = new Properties();
            persistedProps.load(crawlerSettingsInputStream);
            persistedProps.forEach((key, value) ->
                    crawlerSettings.put(key.toString(), value.toString()));
        } catch (IOException e) {
            LOGGER.error("Fatal error: Could not open crawler settings file {}", e);
            System.exit(-1);
        }
    }

    private BrowserCrawlerSettingsUtil() {
    }

    static String getSetting(String key) {
        return crawlerSettings.get(key);
    }
}
