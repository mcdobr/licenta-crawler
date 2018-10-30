package me.mircea.licenta.miner;

import java.net.MalformedURLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import me.mircea.licenta.core.entities.Book;
import me.mircea.licenta.core.entities.Site;
import me.mircea.licenta.core.entities.WebWrapper;
import me.mircea.licenta.core.infoextraction.HeuristicalStrategy;
import me.mircea.licenta.core.infoextraction.InformationExtractionStrategy;
import me.mircea.licenta.core.infoextraction.WrapperGenerationStrategy;
import me.mircea.licenta.core.infoextraction.WrapperStrategy;
import me.mircea.licenta.core.utils.HtmlUtil;
import me.mircea.licenta.db.products.HibernateUtil;

/**
 * @author mircea
 * @brief This class is used to extract books from a (single) given multi-book
 *        page. The intended strategy is to extract the basic information of the
 *        given entry, then using the specific-book page, to correlate and find
 *        out details about the book.
 */
public class Miner implements Runnable {
	private final Document multiBookPage;
	private final Map<String, Document> singleBookPages;
	private final Instant retrievedTime;
	private Site site;

	private static final Logger logger = LoggerFactory.getLogger(Miner.class);

	public Miner(Document doc, Instant retrievedTime, Map<String, Document> singleBookPages) {
		this.multiBookPage = doc;
		this.retrievedTime = retrievedTime;
		this.singleBookPages = singleBookPages;
	}

	public Elements getBookElements() {
		String bookSelector = "[class*='produ']:has(img):has(a)";
		return multiBookPage.select(String.format("%s:not(:has(%s))", bookSelector, bookSelector));
	}

	@Override
	public void run() {
		HtmlUtil.sanitizeHtml(multiBookPage);

		Optional<Site> validSite = getPersistedOrNewSite();
		if (!validSite.isPresent())
			return;
		site = validSite.get();

		InformationExtractionStrategy extractionStrategy = chooseStrategy();
		persistBooks(extractionStrategy);
		if (site.getWrapper() == null)
			persistNewWrapper((WrapperGenerationStrategy) extractionStrategy);

	}

	private InformationExtractionStrategy chooseStrategy() {
		if (site.getWrapper() == null) {
			return new HeuristicalStrategy();
		} else
			return new WrapperStrategy(site.getWrapper());
	}

	private void persistBooks(InformationExtractionStrategy strategy) {
		int inserted = 0;
		int updated = 0;

		final Elements bookElements = getBookElements();
		for (Element bookElement : bookElements) {
			final String bookUrl = HtmlUtil.extractFirstLinkOfElement(bookElement);
			final Document singleBookPage = singleBookPages.get(bookUrl);

			final Book book = strategy.extractBook(bookElement, singleBookPage);
			final PricePoint pricePoint = strategy.extractPricePoint(bookElement, Locale.forLanguageTag("ro-ro"),
					retrievedTime.atZone(ZoneId.systemDefault()).toLocalDate(), site);

			Session session = HibernateUtil.getSessionFactory().openSession();
			session.beginTransaction();

			List<Book> books = findBookByProperties(book, session);
			book.getPricepoints().add(pricePoint);

			if (books.isEmpty()) {
				++inserted;
				session.save(book);
				logger.info("Saved new {} to db.", book);
			} else {
				++updated;

				Book persistedBook = books.get(0);
				Optional<Book> mergedBook = Book.merge(persistedBook, book);

				if (mergedBook.isPresent()) {
					session.merge(mergedBook.get());
					logger.info("Updated {} in db.", mergedBook.get());
				}
			}

			session.getTransaction().commit();
			session.close();
		}
		// TODO: setup loggers right
		logger.error("Found {}/{} books on {} : {} inserted, {} updated.", bookElements.size(), singleBookPages.size(),
				multiBookPage.absUrl("href"), inserted, updated);
	}

	private void persistNewWrapper(WrapperGenerationStrategy wrapperGenerator) {
		Session wrapperGenerationSession = HibernateUtil.getSessionFactory().openSession();
		wrapperGenerationSession.beginTransaction();

		Element anySingleBookPage = singleBookPages.values().iterator().next();
		WebWrapper wrapper = wrapperGenerator.generateWrapper(anySingleBookPage, new Elements(multiBookPage));
		site.setWrapper(wrapper);

		wrapperGenerationSession.save(wrapper);
		wrapperGenerationSession.saveOrUpdate(site);

		logger.error("Added wrapper {} to site {}", wrapper, site);

		wrapperGenerationSession.getTransaction().commit();
		wrapperGenerationSession.close();
	}

	/**
	 * Works only in a session.
	 * 
	 * @param candidate
	 * @param session
	 * @return A list of books containing either one with the same isbn, or other
	 *         books that have the same name.
	 */
	private List<Book> findBookByProperties(Book candidate, Session session) {
		CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
		CriteriaQuery<Book> bookCriteriaQuery = criteriaBuilder.createQuery(Book.class);
		Root<Book> bookRoot = bookCriteriaQuery.from(Book.class);

		if (candidate.getIsbn() != null)
			bookCriteriaQuery.where(criteriaBuilder.equal(bookRoot.get("isbn"), candidate.getIsbn()));
		else
			bookCriteriaQuery.where(criteriaBuilder.equal(bookRoot.get("title"), candidate.getTitle()));

		bookCriteriaQuery.select(bookRoot);
		return session.createQuery(bookCriteriaQuery).getResultList();
	}

	private Optional<Site> getPersistedOrNewSite() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();

		Site newSite = null;
		try {
			newSite = new Site(multiBookPage.baseUri());

			CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
			CriteriaQuery<Site> siteCriteriaQuery = criteriaBuilder.createQuery(Site.class);
			Root<Site> siteRoot = siteCriteriaQuery.from(Site.class);
			siteCriteriaQuery.where(criteriaBuilder.equal(siteRoot.get("name"), newSite.getName()));
			siteCriteriaQuery.select(siteRoot);

			List<Site> sites = session.createQuery(siteCriteriaQuery).getResultList();
			if (sites.isEmpty())
				session.save(newSite);
			else
				newSite = sites.get(0);

		} catch (MalformedURLException e) {
			logger.trace("The site was ill-formed {}", e);
		}

		session.getTransaction().commit();
		session.close();
		return Optional.ofNullable(newSite);
	}
}
