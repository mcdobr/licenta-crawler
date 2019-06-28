package me.mircea.licenta.crawler.impl;

import me.mircea.licenta.crawler.Crawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

public class StandardCrawler implements Crawler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandardCrawler.class);
    private final Queue<URI> activeQueue;

    public StandardCrawler(String seed) throws URISyntaxException {
        this(new URI(seed));
    }

    public StandardCrawler(URI seed) {
        this.activeQueue = new LinkedList<>();
        this.activeQueue.add(seed);
    }


    @Override
    public void run() {
        while (!activeQueue.isEmpty()) {
            URI uri = activeQueue.poll();

            try {
                Document doc = Jsoup.connect(uri.toString()).get();

                Elements sameDocumentLinks = doc.select("link[rel='next'],link[rel='prev']");
                for (Element elem : sameDocumentLinks)
                    //this.activeQueue



                Thread.sleep(1000);
            } catch (IOException e) {
                LOGGER.warn("An I/O error occured {}", e);
            } catch (InterruptedException e) {
                LOGGER.warn("An interrupt occured while thread was asleep {}", e);
            }
        }
    }
}
