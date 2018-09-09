package me.mircea.licenta.crawler;

import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Product;

public class DataRecordNormalizer {
	private static final Logger logger = LoggerFactory.getLogger(DataRecordNormalizer.class);
	
	
	private DataRecordNormalizer() {
	}

	/**
	 * @brief Extracts a product from the given html element.
	 * @param htmlElement
	 * @return
	 */
	public static Product extractProduct(Element htmlElement) {
		String title = htmlElement.select("[class*='titl'],[class*='nume'],[class*='name']").text();
		String price = htmlElement.select("[class*='pret'],[class*='price']").text();
		logger.debug("{} priced at {}", title, price);

		Locale locale = Locale.forLanguageTag("ro-ro");
		Product p = new Product();
		p.setTitle(title);

		try {
			PricePoint point = PricePoint.valueOf(price, locale);
			p.getPricepoints().add(point);
		} catch (ParseException e) {
			logger.warn("Price tag was ill-formated");
		}

		return p;
	}

	public static String extractProductLink(Element htmlElement) {
		return htmlElement.select("a[href]").first().absUrl("href");
	}
}
