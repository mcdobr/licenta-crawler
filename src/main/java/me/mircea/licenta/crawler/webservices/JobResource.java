package me.mircea.licenta.crawler.webservices;

import java.util.List;

import javax.websocket.server.PathParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/jobs")
public class JobResource {
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public List<Job> listActiveJobs() {
		throw new UnsupportedOperationException();
	}

	@GET
	@Path("{jobId}")
	@Produces(MediaType.TEXT_PLAIN)
	public Job getJobStatus(@PathParam("jobId") int jobId) {
		throw new UnsupportedOperationException();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Job createJob() {
		throw new UnsupportedOperationException();
		
		
	}
}
