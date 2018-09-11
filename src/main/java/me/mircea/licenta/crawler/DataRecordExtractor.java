package me.mircea.licenta.crawler;

import java.text.ParseException;
import java.time.Instant;
import java.util.Locale;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.utils.ImmutablePair;

public class DataRecordExtractor {
	private static final Logger logger = LoggerFactory.getLogger(DataRecordExtractor.class);

	private DataRecordExtractor() {
	}

	/**
	 * @brief Extracts a product from the given html element.
	 * @param htmlElement
	 * @param retrievedTime 
	 * @return
	 */
	// TODO: refactor this into two functions?
	public static ImmutablePair<Product, PricePoint> extractProductAndPricePoint(Element htmlElement, Locale locale, Instant retrievedTime) {
		String title = htmlElement.select("[class*='titl'],[class*='nume'],[class*='name']").text();
		String price = htmlElement.select("[class*='pret'],[class*='price']").text();
		logger.debug("{} priced at {}", title, price);

		Product product = new Product();
		product.setTitle(title);

		PricePoint pricePoint = null;
		try {
			pricePoint = PricePoint.valueOf(price, locale, retrievedTime);
		} catch (ParseException e) {
			logger.warn("Price tag was ill-formated");
		}

		return new ImmutablePair<>(product, pricePoint);
	}

	public static String extractProductLink(Element htmlElement) {
		return htmlElement.select("a[href]").first().absUrl("href");
	}
}
