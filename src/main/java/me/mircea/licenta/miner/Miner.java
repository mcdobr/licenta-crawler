package me.mircea.licenta.miner;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Session;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.infoextraction.HeuristicalStrategy;
import me.mircea.licenta.core.infoextraction.InformationExtractionStrategy;
import me.mircea.licenta.core.utils.HibernateUtil;
import me.mircea.licenta.core.utils.HtmlUtil;
import me.mircea.licenta.core.utils.ImmutablePair;

/**
 * @author mircea
 * @brief This class is used to extract products from a (single) given
 *        multi-product page. The intended strategy is to extract the basic
 *        information of the given entry, then using the specific-product page,
 *        to correlate and find out details about the product.
 */
public class Miner implements Runnable {
	private final Document doc;
	private final Map<String, Document> singleProductPages;
	private final Instant retrievedTime;

	private static final Logger logger = LoggerFactory.getLogger(Miner.class);

	public Miner(Document doc, Instant retrievedTime, Map<String, Document> singleProductPages) {
		this.doc = doc;
		this.retrievedTime = retrievedTime;
		this.singleProductPages = singleProductPages;
	}

	private Elements getProductElements() {
		String productSelector = "[class*='produ']:has(img):has(a)";
		return doc.select(String.format("%s:not(:has(%s))", productSelector, productSelector));
	}

	@Override
	public void run() {
		InformationExtractionStrategy extractionStrategy = new HeuristicalStrategy();

		HtmlUtil.sanitizeHtml(doc);
		logger.info("Started mining for products on url {} ...", doc.location());
		for (Element productElement : getProductElements()) {
			ImmutablePair<Product, PricePoint> productPricePair = extractionStrategy.extractProductAndPricePoint(
					productElement, Locale.forLanguageTag("ro-ro"),
					retrievedTime.atZone(ZoneId.systemDefault()).toLocalDate());
			Product product = productPricePair.getFirst();
			PricePoint pricePoint = productPricePair.getSecond();
			String productUrl = HtmlUtil.extractFirstLinkOfElement(productElement);

			try {
				Document singleProductPage = Jsoup.connect(productUrl).get();
				Map<String, String> productAttributes = extractionStrategy.extractProductAttributes(singleProductPage);

				if (productAttributes.isEmpty())
					logger.error("AttributesMap is empty on {}", productUrl);
				for (String author : productAttributes.get("Autor").split(".,")) {
					product.getAuthors().add(author);
				}

				productAttributes.keySet().stream().filter(key -> key.contains("ISBN")).findFirst()
						.ifPresent(key -> product.setIsbn(productAttributes.get(key)));

			} catch (IOException e) {
				logger.warn("Could not connect to url: {}", productUrl);
			}

			Session session = HibernateUtil.getSessionFactory().openSession();
			session.beginTransaction();

			// Query product
			CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
			CriteriaQuery<Product> productCriteriaQuery = criteriaBuilder.createQuery(Product.class);
			Root<Product> productRoot = productCriteriaQuery.from(Product.class);
			productCriteriaQuery.where(criteriaBuilder.equal(productRoot.get("title"), product.getTitle()));
			productCriteriaQuery.select(productRoot);

			// insert product
			List<Product> products = session.createQuery(productCriteriaQuery).getResultList();
			if (products.isEmpty()) {
				product.getPricepoints().add(pricePoint);
				session.save(product);
				logger.info("Saved new product {} to db.", product);
			} else {
				Product persistedProduct = products.get(0);
				persistedProduct.getPricepoints().add(pricePoint);
				session.saveOrUpdate(persistedProduct);
				logger.info("Updated product {} in db.", persistedProduct);
			}

			session.getTransaction().commit();
			session.close();
		}
		logger.info("Ended mining for products on url {}...", doc.location());
	}
}
