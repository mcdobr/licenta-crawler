package me.mircea.licenta.crawler;

import java.util.HashMap;
import java.util.Map;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Miner {
	private Map<Element, Integer> depths;
	
	public Map<Element, Integer> getDepthOfTree(Element root) {
		Map<Element, Integer> depths = new HashMap<>();
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

	/**
	 * @brief This function implements the MDR algorithm explained in a paper by
	 *        Zhai and Liu.
	 * @param root
	 *            The starting tag node.
	 * @param maxInternalTagNodes
	 *            The maximum number of tag nodes that a generalized tree node can
	 *            have.
	 */
	public void mineDataRegions(Element node, final int maxInternalTagNodes) {
		if (depths.get(node) >= 3) {
			compareCombinations(node.children(), maxInternalTagNodes);
		}
	}
}
