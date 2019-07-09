package me.mircea.licenta.crawler.webservices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import me.mircea.licenta.core.crawl.db.CrawlDatabaseManager;
import me.mircea.licenta.core.crawl.db.model.Job;
import me.mircea.licenta.core.crawl.db.model.JobActiveOnHost;
import me.mircea.licenta.core.crawl.db.model.JobType;
import me.mircea.licenta.core.parser.utils.HtmlUtil;
import me.mircea.licenta.crawler.Crawler;
import me.mircea.licenta.crawler.impl.BrowserCrawler;
import me.mircea.licenta.crawler.impl.SitemapSaxCrawler;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Path("/jobs")
public class CrawlJobResource {
    private static final ExecutorService ASYNC_TASK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlJobResource.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Job> listActiveCrawlerJobs() {
        Iterable<Job> iterable = CrawlDatabaseManager.instance.getActiveJobsByType(JobType.CRAWL);
        return Lists.newArrayList(iterable);
    }

    @GET
    @Path("{jobId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Job getCrawlerJobStatus(@PathParam("jobId") ObjectId jobId) {
        return CrawlDatabaseManager.instance.getJobById(jobId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createCrawlerJob(@Context HttpServletRequest request, ObjectNode crawlRequest) {
        JsonNode homepageNode = crawlRequest.get("homepage");
        JsonNode seedsNode = crawlRequest.get("seeds");
        JsonNode additionalSitemapsNode = crawlRequest.get("additionalSitemaps");
        JsonNode disallowCookiesNode = crawlRequest.get("disallowCookies");

        boolean disallowCookies;
        if (disallowCookiesNode != null) {
            disallowCookies = disallowCookiesNode.booleanValue();
        } else {
            disallowCookies = false;
        }

        LOGGER.error("disallowCookies final = {}", disallowCookies);
        Job job;
        String domain = null;
        try {
            domain = HtmlUtil.getDomainOfUrl(homepageNode.asText());
            if (invalidCrawlStartingPointProvided(seedsNode, homepageNode)) {
                throw new MalformedURLException("Some URLs are malformed");
            }

            job = new Job(homepageNode.asText(), JobType.CRAWL, convertJsonTextArrayToIterable(seedsNode), convertJsonTextArrayToIterable(additionalSitemapsNode), disallowCookies);
            Crawler crawler = chooseBestCrawlingStrategy(job);
            ASYNC_TASK_EXECUTOR.submit(crawler);

            return Response.status(202).entity(job).build();
        } catch (MalformedURLException e) {
            LOGGER.warn("Some provided URLs are malformed {}", homepageNode);
            return Response.status(400).build();
        } catch (JobActiveOnHost e) {
            LOGGER.warn("A job was active on the host before trying to start a new one");
            Job activeJob = CrawlDatabaseManager.instance.getActiveJobOnDomain(domain);
            String redirectUri = request.getRequestURI() + "/" + activeJob.getId().toString();
            return Response.status(409).header("Location", redirectUri).entity(activeJob).build();
        } catch (IOException e) {
            LOGGER.warn("Could not read config file to start job {}", e);
            return Response.status(500).build();
        }
    }

    private boolean invalidCrawlStartingPointProvided(JsonNode seeds, JsonNode homepage) {
        return homepage == null || !homepage.isTextual() || seeds == null || !seeds.isArray()
                || !doSeedsMatchWithHomepage((ArrayNode) seeds, homepage.asText());
    }

    private List<String> convertJsonTextArrayToIterable(JsonNode arrayNode) {
        if (arrayNode == null)
            return Collections.emptyList();

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(arrayNode.elements(), Spliterator.ORDERED), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    private Crawler chooseBestCrawlingStrategy(Job job) {
        Crawler crawler;
        if (!job.getRobotRules().getSitemaps().isEmpty()) {
            crawler = new SitemapSaxCrawler(job);
        } else {
            crawler = new BrowserCrawler(job);
        }
        return crawler;
    }

    private boolean doSeedsMatchWithHomepage(ArrayNode seeds, String homepage) {
        for (JsonNode seedNode : seeds) {
            if (!seedNode.isTextual())
                return false;

            try {
                URI seedUri = new URI(seedNode.asText());
                URI homepageUri = new URI(homepage);

                if (seedUri.getHost() == null || homepageUri.getHost() == null || !seedUri.getHost().equals(homepageUri.getHost())) {
                    return false;
                }
            } catch (URISyntaxException e) {
                LOGGER.trace("Request URIs were malformed");
                return false;
            }
        }
        return true;
    }
}
