package org.spoofax.jsglr2.reducing;

import org.spoofax.jsglr2.actions.IReduce;
import org.spoofax.jsglr2.parseforest.AbstractParseForest;
import org.spoofax.jsglr2.parseforest.ParseForestManager;
import org.spoofax.jsglr2.parser.Parse;
import org.spoofax.jsglr2.parsetable.IState;
import org.spoofax.jsglr2.stack.AbstractStackNode;
import org.spoofax.jsglr2.stack.StackLink;
import org.spoofax.jsglr2.stack.StackManager;

public class ReducerSkipLayoutAndLexicalAndRejects<StackNode extends AbstractStackNode<ParseForest>, ParseForest extends AbstractParseForest, ParseNode extends ParseForest, Derivation> extends Reducer<StackNode, ParseForest, ParseNode, Derivation> {

    public ReducerSkipLayoutAndLexicalAndRejects(StackManager<StackNode, ParseForest> stackManager, ParseForestManager<ParseForest, ParseNode, Derivation> parseForestManager) {
    		super(stackManager, parseForestManager);
    }
    
    @Override
    public void reducerExistingStackWithDirectLink(Parse<StackNode, ParseForest> parse, IReduce reduce, StackLink<StackNode, ParseForest> existingDirectLinkToActiveStateWithGoto, ParseForest[] parseForests) {
		@SuppressWarnings("unchecked")
        ParseNode parseNode = (ParseNode) existingDirectLinkToActiveStateWithGoto.parseForest;

        if (reduce.isRejectProduction())
            stackManager.rejectStackLink(parse, existingDirectLinkToActiveStateWithGoto);
        else if (!existingDirectLinkToActiveStateWithGoto.isRejected() && parseNode != null) {
    			Derivation derivation = parseForestManager.createDerivation(parse, existingDirectLinkToActiveStateWithGoto.to.position, reduce.production(), reduce.productionType(), parseForests);
        		parseForestManager.addDerivation(parse, parseNode, derivation);
        }
    }
    
    @Override
    public StackLink<StackNode, ParseForest> reducerExistingStackWithoutDirectLink(Parse<StackNode, ParseForest> parse, IReduce reduce, StackNode existingActiveStackWithGotoState, StackNode stack, ParseForest[] parseForests) {
    		StackLink<StackNode, ParseForest> newDirectLinkToActiveStateWithGoto;
    	
    		if (reduce.isRejectProduction()) {
        		newDirectLinkToActiveStateWithGoto = stackManager.createStackLink(parse, existingActiveStackWithGotoState, stack, null);
        	
            stackManager.rejectStackLink(parse, newDirectLinkToActiveStateWithGoto);
        } else {
	        	ParseNode parseNode;
	        	
	        	if (reduce.production().isSkippableInParseForest())
	    			parseNode = null;
	    		else {
	        		Derivation derivation = parseForestManager.createDerivation(parse, stack.position, reduce.production(), reduce.productionType(), parseForests);
	            parseNode = parseForestManager.createParseNode(parse, stack.position, reduce.production(), derivation);		
	    		}
	    		
        		newDirectLinkToActiveStateWithGoto = stackManager.createStackLink(parse, existingActiveStackWithGotoState, stack, parseNode);
        }
        
        return newDirectLinkToActiveStateWithGoto;
    }
    
    @Override
    public StackNode reducerNoExistingStack(Parse<StackNode, ParseForest> parse, IReduce reduce, StackNode stack, IState gotoState, ParseForest[] parseForests) {
	    	StackNode newStackWithGotoState = stackManager.createStackNode(parse, gotoState);
			
		StackLink<StackNode, ParseForest> link;
	    
	    if (reduce.isRejectProduction()) {
	    		link = stackManager.createStackLink(parse, newStackWithGotoState, stack, null);
	    	
	        stackManager.rejectStackLink(parse, link);
	    } else {
	    		ParseNode parseNode;
	    	
	    		if (reduce.production().isSkippableInParseForest())
				parseNode = null;
			else {
		    		Derivation derivation = parseForestManager.createDerivation(parse, stack.position, reduce.production(), reduce.productionType(), parseForests);
		        parseNode = parseForestManager.createParseNode(parse, stack.position, reduce.production(), derivation);		
			}
		    
		    link = stackManager.createStackLink(parse, newStackWithGotoState, stack, parseNode);
	    }
	    
	    return newStackWithGotoState;
    }
    
}
