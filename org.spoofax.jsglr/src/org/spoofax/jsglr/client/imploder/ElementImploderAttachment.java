package org.spoofax.jsglr.client.imploder;

/** 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class ElementImploderAttachment extends ImploderAttachment {
	
	public ElementImploderAttachment(String sort, IToken leftToken, IToken rightToken) {
		super(sort, leftToken, rightToken);
	}

	@Override
	public String getSort() {
		return super.getSort() + "*";
	}
	
	@Override
	public String getElementSort() {
		return super.getSort();
	}
}
