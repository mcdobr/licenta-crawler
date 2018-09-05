package me.mircea.licenta.crawler;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Session;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.utils.HibernateUtil;

public final class CrawlerStart {
	private static final Logger logger = LoggerFactory.getLogger(CrawlerStart.class);

	public static void main(String[] args) {
		
		Session ses = HibernateUtil.getSessionFactory().openSession();
		ses.beginTransaction();
		
		ses.save(new Product());
		
		ses.getTransaction().commit();
		ses.close();
		
		
		List<String> seedList = Arrays.asList(/*"https://carturesti.ro/raft/carte-109",*/
				"http://www.librariilealexandria.ro/carte"/*,
				"https://www.libris.ro/carti",
				"https://www.emag.ro/search/carti"*/);

		ExecutorService exec = Executors.newCachedThreadPool();

		for (String startUrl : seedList) {
			try {
				exec.submit(new Fetcher(startUrl));
			} catch (MalformedURLException e) {
				logger.debug("Problem regarding gathering pages: {}.", e.getMessage());
			}
		}
	}
}
