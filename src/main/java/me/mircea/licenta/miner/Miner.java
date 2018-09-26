package me.mircea.licenta.miner;

import java.net.MalformedURLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Session;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.entities.Site;
import me.mircea.licenta.core.infoextraction.HeuristicalStrategy;
import me.mircea.licenta.core.infoextraction.InformationExtractionStrategy;
import me.mircea.licenta.core.utils.HtmlUtil;
import me.mircea.licenta.db.products.HibernateUtil;

/**
 * @author mircea
 * @brief This class is used to extract products from a (single) given
 *        multi-product page. The intended strategy is to extract the basic
 *        information of the given entry, then using the specific-product page,
 *        to correlate and find out details about the product.
 */
public class Miner implements Runnable {
	private final Document multiProductPage;
	private final Map<String, Document> singleProductPages;
	private final Instant retrievedTime;

	private static final Logger logger = LoggerFactory.getLogger(Miner.class);

	public Miner(Document doc, Instant retrievedTime, Map<String, Document> singleProductPages) {
		this.multiProductPage = doc;
		this.retrievedTime = retrievedTime;
		this.singleProductPages = singleProductPages;
	}

	public Elements getProductElements() {
		String productSelector = "[class*='produ']:has(img):has(a)";
		return multiProductPage.select(String.format("%s:not(:has(%s))", productSelector, productSelector));
	}

	// TODO: This method needs refactoring... badly
	@Override
	public void run() {
		InformationExtractionStrategy extractionStrategy = new HeuristicalStrategy();
		HtmlUtil.sanitizeHtml(multiProductPage);
		
		Site site = getPersistedOrCreatedSite();
		
		int inserted = 0;
		int updated = 0;

		final Elements productElements = getProductElements();
		for (Element productElement : productElements) {
			final String productUrl = HtmlUtil.extractFirstLinkOfElement(productElement);
			final Document singleProductPage = singleProductPages.get(productUrl);

			final Product product = extractionStrategy.extractProduct(productElement, singleProductPage);
			final PricePoint pricePoint = extractionStrategy.extractPricePoint(productElement,
					Locale.forLanguageTag("ro-ro"), retrievedTime.atZone(ZoneId.systemDefault()).toLocalDate(), site);

			List<Product> products = findBookByProperties(product);

			Session session = HibernateUtil.getSessionFactory().openSession();
			session.beginTransaction();

			if (products.isEmpty()) {
				++inserted;

				product.getPricepoints().add(pricePoint);
				session.save(product);
				logger.info("Saved new product {} to db.", product);
			} else {
				++updated;

				Product persistedProduct = products.get(0);
				persistedProduct.getPricepoints().add(pricePoint);
				session.saveOrUpdate(persistedProduct);
				logger.info("Updated product {} in db.", persistedProduct);
			}

			session.getTransaction().commit();
			session.close();
		}

		// TODO: setup loggers right
		logger.error("Found {}/{} products on that page: {} inserted, {} updated.", productElements.size(),
				singleProductPages.size(), inserted, updated);
	}

	private List<Product> findBookByProperties(Product candidate) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();

		CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
		CriteriaQuery<Product> productCriteriaQuery = criteriaBuilder.createQuery(Product.class);
		Root<Product> productRoot = productCriteriaQuery.from(Product.class);

		if (candidate.getIsbn() != null)
			productCriteriaQuery.where(criteriaBuilder.equal(productRoot.get("isbn"), candidate.getIsbn()));
		else
			productCriteriaQuery.where(criteriaBuilder.equal(productRoot.get("title"), candidate.getTitle()));

		productCriteriaQuery.select(productRoot);
		List<Product> products = session.createQuery(productCriteriaQuery).getResultList();

		session.getTransaction().commit();
		session.close();

		return products;
	}

	private Site getPersistedOrCreatedSite() {
		
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		Site site = null;
		try {
			
			site = new Site(multiProductPage.baseUri());

			CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
			CriteriaQuery<Site> siteCriteriaQuery = criteriaBuilder.createQuery(Site.class);
			Root<Site> siteRoot = siteCriteriaQuery.from(Site.class);
			siteCriteriaQuery.where(criteriaBuilder.equal(siteRoot.get("name"), site.getName()));
			siteCriteriaQuery.select(siteRoot);

			List<Site> sites = session.createQuery(siteCriteriaQuery).getResultList();
			if (sites.isEmpty())
				session.save(site);
			else 
				site = sites.get(0);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		

		session.getTransaction().commit();
		session.close();
		return site;
	}
}
