package me.mircea.licenta.crawler;

import java.util.HashMap;
import java.util.List;
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

import me.mircea.licenta.core.entities.Product;
import me.mircea.licenta.core.utils.HibernateUtil;


public class Miner implements Runnable {
	private Document doc;
	private Map<Element, Integer> depths;
	
	private static final Logger logger = LoggerFactory.getLogger(Miner.class);
	
	public Miner(Document doc) {
		this.doc = doc;
		this.depths = new HashMap<>();
	}
	
	/*
	private Map<Element, Integer> getDepthOfTree(Element root) {
		getDepthOfSubtree(root, depths);
		return depths;
	}
	
	private void getDepthOfSubtree(Element root, Map<Element, Integer> depths) {
		Elements children = root.children();
		if (children.isEmpty()) {
			depths.put(root, 1);
		} else {
			for (Element child : children) {
				if (!depths.containsKey(child)) {
					getDepthOfSubtree(child, depths);

					Integer currentDepth = depths.getOrDefault(root, 1);
					depths.put(root, Math.max(currentDepth, 1 + depths.get(child)));
				}
			}
		}
	}

	private void compareCombinations(Elements children, final int maxInternalTagNodes) {
		for (int i = 0; i < maxInternalTagNodes; ++i) {
			for (int j = i + 1; j <= maxInternalTagNodes; ++j) {
				if (i + 2 * j - 1 < children.size()) {
					int start = i;
				}
			}
		}
	}
	*/
	/**
	 * @brief This function implements the MDR algorithm explained in a paper by
	 *        Zhai and Liu.
	 * @param root
	 *            The starting tag node.
	 * @param maxInternalTagNodes
	 *            The maximum number of tag nodes that a generalized tree node can
	 *            have.
	 *
	 private void mineDataRegions(Element node, final int maxInternalTagNodes) {
		if (depths.get(node) >= 3) {
			compareCombinations(node.children(), maxInternalTagNodes);
		}
	}
	*/

	private void cleanHtml() {
		doc.select("style").remove();
		doc.select("script").remove();
		doc.getElementsByAttribute("style").removeAttr("style");
	}
	
	@Override
	public void run() {
		cleanHtml();

		// Select all leaf nodes that look like a product
		String productSelector = "[class*='produ']:has(img):has(a)";		
		Elements productElements = doc.select(String.format("%s:not(:has(%s))", productSelector, productSelector));
		logger.info("Started mining for products...");
		
		for (Element element : productElements) {
			Session session = HibernateUtil.getSessionFactory().openSession();
			session.beginTransaction();
			
			Product p = DataRecordNormalizer.extractProduct(element);
			
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<Product> cq = cb.createQuery(Product.class);
			Root<Product> product = cq.from(Product.class);
			
			cq.where(cb.equal(product.get("title"), p.getTitle()));
			cq.select(product);
			
			List<Product> products = session.createQuery(cq).getResultList();
			if (products.isEmpty()) {
				session.save(p);
				logger.error("Saved product to db.");
			}

			session.getTransaction().commit();
			session.close();
		}
		logger.info("Ended mining for products...");
	}
}
