package org.spoofax.jsglr2.stack.basic;

import org.spoofax.jsglr2.parseforest.AbstractParseForest;
import org.spoofax.jsglr2.parser.Position;
import org.spoofax.jsglr2.parsetable.IState;

public class BasicStackManager<ParseForest extends AbstractParseForest> extends AbstractBasicStackManager<BasicStackNode<ParseForest>, ParseForest> {
    
	protected BasicStackNode<ParseForest> createStackNode(int stackNumber, IState state, Position position) {
		return new BasicStackNode<ParseForest>(stackNumber, state, position);
	}
    
}
