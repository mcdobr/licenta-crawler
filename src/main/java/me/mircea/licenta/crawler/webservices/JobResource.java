package me.mircea.licenta.crawler.webservices;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.crawl.CrawlRequest;
import me.mircea.licenta.crawler.BrowserFetcher;
import me.mircea.licenta.crawler.Fetcher;
import me.mircea.licenta.crawler.SitemapSaxFetcher;

@Path("/jobs")
public class JobResource {
	private static final ExecutorService ASYNC_TASK_EXECUTOR = Executors.newCachedThreadPool();
	private static final Logger LOGGER = LoggerFactory.getLogger(JobResource.class);
	
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public List<String> listActiveCrawlerJobs() {
		throw new UnsupportedOperationException();
	}

	@GET
	@Path("{jobId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String getCrawlerJobStatus(@PathParam("jobId") int jobId) {
		throw new UnsupportedOperationException();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createCrawlerJob(String seed) {
		CrawlRequest request;
		try {
			request = new CrawlRequest(seed);
			Fetcher fetcher;
			if (!request.getRobotRules().getSitemaps().isEmpty()) {
				fetcher = new SitemapSaxFetcher(request);
			} else {
				fetcher = new BrowserFetcher(request);
			}
			ASYNC_TASK_EXECUTOR.submit(fetcher);

			return Response.status(202)
					.entity(request)
					.build();
		} catch (IOException e) {
			LOGGER.warn("Could not read a file {}", e);
			return Response.status(500)
					.build();
		}
	}
}
