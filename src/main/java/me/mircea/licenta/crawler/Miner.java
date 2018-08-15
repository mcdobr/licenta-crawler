package me.mircea.licenta.crawler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mircea.licenta.core.entities.Product;


public class Miner implements Callable<List<String>> {
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
	 */
	/*private void mineDataRegions(Element node, final int maxInternalTagNodes) {
		if (depths.get(node) >= 3) {
			compareCombinations(node.children(), maxInternalTagNodes);
		}
	}
	 */

	@Override
	public List<String> call() throws Exception {
		doc.select("style").remove();
		doc.select("script").remove();
		doc.getElementsByAttribute("style").removeAttr("style");
		
		List<Product> products = new ArrayList<>();
		
		Elements productElements = doc.select("[class*='produ']:has(img):has(a)");
		for (Element element : productElements) {
			String title = element.select("[class*='titl'],[class*='nume'],[class*='name']").text();
			String price = element.select("[class*='pret'],[class*='price']").text();
			
			logger.error("{} priced at {}", title, price);
			
			Product p = new Product();
			p.setTitle(title);
		}
		
		////TODO: use following xpath to get elements: //*[contains(@class, 'produ') and descendant::img and descendant::a]
		return new ArrayList<>();
	}
}
