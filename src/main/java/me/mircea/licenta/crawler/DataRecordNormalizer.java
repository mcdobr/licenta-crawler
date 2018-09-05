package me.mircea.licenta.crawler;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Currency;
import java.util.Locale;

import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.PricePoint;
import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.utils.HibernateUtil;

public class DataRecordNormalizer {
	private static final Logger logger = LoggerFactory.getLogger(DataRecordNormalizer.class);

	public static Product extractProduct(Element element) {
		String title = element.select("[class*='titl'],[class*='nume'],[class*='name']").text();
		String price = element.select("[class*='pret'],[class*='price']").text();

		// TODO : handle price right
		logger.info("{} priced at {}", title, price);

		Locale locale = Locale.forLanguageTag("ro-ro");

		// logger.info("A pricepoint of {} {}",
		// pricePoint.getNominalValue().toPlainString(), pricePoint.getCurrency());

		
		Product p = new Product();
		p.setTitle(title);
		
		try {
			PricePoint point = parsePriceTag(price, locale);
			p.getPricepoints().add(point);
		} catch (ParseException e) {
			logger.error("Price tag was ill-formated");
		}

		return p;
	}

	/**
	 * @brief Transforms a price tag string into a PricePoint.
	 * @param price
	 * @param locale
	 * @return
	 * @throws ParseException
	 */
	// TODO: Should return a nominalValue, currency pair.
	private static PricePoint parsePriceTag(final String price, final Locale locale) throws ParseException {
		final NumberFormat noFormat = NumberFormat.getNumberInstance(locale);
		if (noFormat instanceof DecimalFormat) {
			((DecimalFormat) noFormat).setParseBigDecimal(true);
		}

		BigDecimal nominalValue = (BigDecimal) noFormat.parse(price);
		if (nominalValue.stripTrailingZeros().scale() <= 0 && nominalValue.compareTo(BigDecimal.valueOf(100)) >= 1)
			nominalValue = nominalValue.divide(BigDecimal.valueOf(100));

		return new PricePoint(null, nominalValue, Currency.getInstance(locale), null, null);
	}
}
