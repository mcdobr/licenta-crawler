package me.mircea.licenta.miner;

import java.net.MalformedURLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.LoadType;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Book;
import me.mircea.licenta.core.entities.WebWrapper;
import me.mircea.licenta.core.infoextraction.HeuristicalStrategy;
import me.mircea.licenta.core.infoextraction.InformationExtractionStrategy;
import me.mircea.licenta.core.infoextraction.WrapperGenerationStrategy;
import me.mircea.licenta.core.utils.HtmlUtil;

/**
 * @author mircea
 * @brief This class is used to extract books from a (single) given multi-book
 *        page. The intended strategy is to extract the basic information of the
 *        given entry, then using the specific-book page, to correlate and find
 *        out details about the book.
 */
public class Miner implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Miner.class);
	
	private final Document multiBookPage;
	private final Map<String, Document> singleBookPages;
	private final Instant retrievedTime;
	private final String site;
	private WebWrapper wrapper;
	
	public Miner(Document doc, Instant retrievedTime, Map<String, Document> singleBookPages) throws MalformedURLException {
		this.multiBookPage = doc;
		this.retrievedTime = retrievedTime;
		this.singleBookPages = singleBookPages;
		this.site = HtmlUtil.getDomainOfUrl(multiBookPage.baseUri());
		this.wrapper = ObjectifyService.run(new Work<WebWrapper>() {
			@Override
			public WebWrapper run() {
				return getWrapperForSite(site);
			}
		});
	}

	public Elements getBookElements() {
		String bookSelector = "[class*='produ']:has(img):has(a)";
		return multiBookPage.select(String.format("%s:not(:has(%s))", bookSelector, bookSelector));
	}

	@Override
	public void run() {
		HtmlUtil.sanitizeHtml(multiBookPage);
		
		ObjectifyService.run(new Work<Void>() {
			@Override
			public Void run() {

				InformationExtractionStrategy extractionStrategy = chooseStrategy();
				persistBooks(extractionStrategy);
				return null;
			}
		});
	}

	private InformationExtractionStrategy chooseStrategy() {
		WebWrapper wrapper = getWrapperForSite(this.site);
		return new HeuristicalStrategy();
		
		/*
		if (wrapper == null) {
			return new HeuristicalStrategy();
		} else {
			return new WrapperStrategy(wrapper);
		}*/
	}

	private void persistBooks(InformationExtractionStrategy strategy) {
		int inserted = 0;
		int updated = 0;

		final Elements bookElements = getBookElements();
		for (Element bookElement : bookElements) {
			final String bookUrl = HtmlUtil.extractFirstLinkOfElement(bookElement);
			final Document singleBookPage = singleBookPages.get(bookUrl);

			final Book book = strategy.extractBook(bookElement, singleBookPage);
			final PricePoint pricePoint = strategy.extractPricePoint(bookElement, Locale.forLanguageTag("ro-ro"), retrievedTime);

			List<Book> books = findBookByProperties(book);
			book.getPricepoints().add(pricePoint);
			
			if (books.isEmpty()) {
				++inserted;
				ObjectifyService.ofy().save().entity(book);
				//session.save(book);
				logger.info("Saving new {} to db.", book);
			} else {
				++updated;

				Book persistedBook = books.get(0);
				Optional<Book> mergedBook = Book.merge(persistedBook, book);

				if (mergedBook.isPresent()) {
					Book bookToPersist = mergedBook.get();
					ObjectifyService.ofy().delete().entities(persistedBook);
					ObjectifyService.ofy().save().entities(bookToPersist);
					//session.merge(mergedBook.get());
					logger.info("Updating book to {} in db.", mergedBook.get());
				}
			}
		}
		// TODO: setup loggers right
		logger.error("Found {}/{} books on {} : {} inserted, {} updated.", bookElements.size(), singleBookPages.size(),
				multiBookPage.absUrl("href"), inserted, updated);
	}

	private Key<WebWrapper> persistNewWrapper(WrapperGenerationStrategy wrapperGenerator) {
		Element anySingleBookPage = singleBookPages.values().iterator().next();
		WebWrapper wrapper = wrapperGenerator.generateWrapper(anySingleBookPage, new Elements(multiBookPage));
		
		logger.error("Adding wrapper {}", wrapper);
		return ObjectifyService.ofy().save().entity(wrapper).now();
	}

	/**
	 * @param candidate
	 * @return A list of books containing either one with the same isbn, or other
	 *         books that have the same name.
	 */
	private List<Book> findBookByProperties(Book candidate) {
		final int MAX_BOOKS_RETURNED = 10;
		
		LoadType<Book> bookLoader = ObjectifyService.ofy().load().type(Book.class);
		if (candidate.getIsbn() != null)
			return bookLoader.filter("isbn", candidate.getIsbn()).list();
		else
			return bookLoader.filter("title", candidate.getTitle()).limit(MAX_BOOKS_RETURNED).list();
	}

	private WebWrapper getWrapperForSite(String site) {
		Preconditions.checkNotNull(site);
		
		return ObjectifyService.ofy().load().type(WebWrapper.class).filter("site", site).first().now();
	}
}
