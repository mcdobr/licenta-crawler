package me.mircea.licenta.crawler;

import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public abstract class Crawler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Crawler.class);
    protected final Job job;

    protected Crawler(Job job) {
        this.job = job;
    }

    protected void startCrawlJob() {
        LOGGER.info("Started crawling job {}", this.job);
        CrawlDatabaseManager.instance.upsertJob(this.job);
    }

    protected void finishCrawlJob() {
        this.job.setEnd(Instant.now());
        this.job.setStatus(JobStatus.FINISHED);
        CrawlDatabaseManager.instance.upsertJob(this.job);

        LOGGER.info("Finished crawling job {}", this.job);
    }
}
