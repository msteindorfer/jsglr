package org.spoofax.jsglr2.parsetable;

public class Production implements IProduction {

	private final int productionNumber;
	private final String sort;
	private final String startSymbolSort;
	private final String descriptor;
	private final boolean isContextFree;
	private final boolean isLayout;
	private final boolean isLiteral;
	private final boolean isLexical;
	private final boolean isLexicalRhs;
	private final boolean isSkippableInParseForest;
	private final boolean isList;
	private final boolean isOptional;
	private final boolean isStringLiteral;
	private final boolean isNumberLiteral;
	private final boolean isOperator;
	private final ProductionAttributes attributes;

	public Production(int productionNumber, String sort, String startSymbolSort, String descriptor, Boolean isContextFree, Boolean isLayout, Boolean isLiteral, Boolean isLexical, Boolean isLexicalRhs, Boolean isSkippableInParseForest, Boolean isList, Boolean isOptional, Boolean isStringLiteral, Boolean isNumberLiteral, Boolean isOperator, ProductionAttributes attributes) {
		this.productionNumber = productionNumber;
		this.sort = sort;
		this.startSymbolSort = startSymbolSort;
		this.descriptor = descriptor;
		this.isContextFree = isContextFree;
		this.isLayout = isLayout;
		this.isLiteral = isLiteral;
		this.isLexical = isLexical;
		this.isLexicalRhs = isLexicalRhs;
		this.isSkippableInParseForest = isSkippableInParseForest;
		this.isList = isList;
		this.isOptional = isOptional;
		this.isStringLiteral = isStringLiteral;
		this.isNumberLiteral = isNumberLiteral;
		this.isOperator = isOperator;
		this.attributes = attributes;
	}

	public int productionNumber() {
	    return productionNumber;
	}

	public static ProductionType typeFromInt(int productionType) {
		switch (productionType) {
			case 1:		return ProductionType.REJECT;
			case 2:		return ProductionType.PREFER;
			case 3:		return ProductionType.BRACKET;
			case 4:		return ProductionType.AVOID;
			case 5:		return ProductionType.LEFT_ASSOCIATIVE;
			case 6:		return ProductionType.RIGHT_ASSOCIATIVE;
			default:		return ProductionType.NO_TYPE;
		}
	}

	public ProductionType productionType() { return attributes.type; }

    public String sort() {
        return sort;
    }

    public String startSymbolSort() {
        return startSymbolSort;
    }

    public String constructor() {
        return attributes.constructor;
    }

    public String descriptor() {
        return descriptor;
    }

    public boolean isContextFree() {
        return isContextFree;
    }

    public boolean isLayout() {
        return isLayout;
    }

    public boolean isLiteral() {
        return isLiteral;
    }

    public boolean isLexical() {
        return isLexical;
    }

    public boolean isLexicalRhs() {
        return isLexicalRhs;
    }

	public boolean isSkippableInParseForest() {
		return isSkippableInParseForest;
	}

    public boolean isList() {
        return isList;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public boolean isStringLiteral() {
		return isStringLiteral;
	}

    public boolean isNumberLiteral() {
		return isNumberLiteral;
	}

    public boolean isOperator() {
		return isOperator;
	}

    public boolean isCompletionOrRecovery() {
        return attributes.isCompletionOrRecovery();
    }

    public String toString() {
		return descriptor;
	}

}
