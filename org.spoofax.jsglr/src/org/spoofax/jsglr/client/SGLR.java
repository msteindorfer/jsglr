/*
 * Created on 03.des.2005
 *
 * Copyright (c) 2005, Karl Trygve Kalleberg <karltk near strategoxt.org>
 *
 * Licensed under the GNU Lesser General Public License, v2.1
 */
package org.spoofax.jsglr.client;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.spoofax.PushbackStringIterator;
import org.spoofax.interpreter.terms.ITermFactory;
import org.spoofax.jsglr.shared.ArrayDeque;
import org.spoofax.jsglr.shared.BadTokenException;
import org.spoofax.jsglr.shared.SGLRException;
import org.spoofax.jsglr.shared.TokenExpectedException;
import org.spoofax.jsglr.shared.Tools;

public class SGLR {

    private RecoveryPerformance performanceMeasuring;

	private final Set<BadTokenException> collectedErrors = new LinkedHashSet<BadTokenException>();

	public static final int EOF = ParseTable.NUM_CHARS;

	static final int TAB_SIZE = 4;//8;

	protected static boolean WORK_AROUND_MULTIPLE_LOOKAHEAD;

	//Performance testing
	private static long parseTime=0;
	private static int parseCount=0;

	protected Frame startFrame;

	private long startTime;

	protected volatile boolean asyncAborted;

	protected Frame acceptingStack;

	protected final ArrayDeque<Frame> activeStacks;

	private ParseTable parseTable;

	protected int currentToken;

	protected int tokensSeen;

	protected int lineNumber;

	protected int columnNumber;

	private final ArrayDeque<ActionState> forShifter;

	private ArrayDeque<Frame> forActor;

	private ArrayDeque<Frame> forActorDelayed;

	private int maxBranches;

	private int maxToken;

	private int maxLine;

	private int maxColumn;

	private int maxTokenNumber;

	private AmbiguityManager ambiguityManager;

	protected Disambiguator disambiguator;

	private int rejectCount;

	private int reductionCount;

	protected PushbackStringIterator currentInputStream;

	private PooledPathList reductionsPathCache = new PooledPathList(512, true);
	private PathListPool pathCache = new PathListPool();
	private ArrayDeque<Frame> activeStacksWorkQueue = new ArrayDeque<Frame>();
	private ArrayDeque<Frame> recoverStacks;

	private ParserHistory history;

	private RecoveryConnector recoverIntegrator;

	protected boolean useIntegratedRecovery;

	public ParserHistory getHistory() {
		return history;
	}

	private boolean fineGrainedOnRegion;

	public void clearRecoverStacks(){
		recoverStacks.clear(false);
	}


	public ArrayDeque<Frame> getRecoverStacks() {
		return recoverStacks;
	}

	public Set<BadTokenException> getCollectedErrors() {
		return collectedErrors;
	}
    
    /**
     * Attempts to set a timeout for parsing.
     * Default implementation throws an
     * {@link UnsupportedOperationException}.
     * 
     * @see org.spoofax.jsglr.io.SGLR#setTimeout(int)
     */
    public void setTimeout(int timeout) {
        throw new UnsupportedOperationException("Use org.spoofax.jsglr.io.SGLR for setting a timeout");
    }
    
    protected void initParseTimer() {
        // Default does nothing (not supported by GWT)
    }

	@Deprecated
	public SGLR(ITermFactory pf, ParseTable parseTable) {
		this(new Asfix2TreeBuilder(pf), parseTable);
	}
	
	@Deprecated
	public SGLR(ParseTable parseTable) {
		this(new Asfix2TreeBuilder(), parseTable);
	}
	
	public SGLR(ITreeBuilder treeBuilder, ParseTable parseTable) {
		assert parseTable != null;
		// Init with a new factory for both serialized or BAF instances.
		this.parseTable = parseTable;

		activeStacks = new ArrayDeque<Frame>();
		forActor = new ArrayDeque<Frame>();
		forActorDelayed = new ArrayDeque<Frame>();
		forShifter = new ArrayDeque<ActionState>();

		disambiguator = new Disambiguator();
		useIntegratedRecovery = false;
		recoverIntegrator = null;
		history = new ParserHistory();
		setTreeBuilder(treeBuilder);
	}

	public void setUseStructureRecovery(boolean useRecovery, IRecoveryParser parser) {
		useIntegratedRecovery = useRecovery;
		recoverIntegrator = new RecoveryConnector(this, parser);
	}

	/**
	 * Enables errorr recovery based on region recovery and, if available, recovery rules.
	 * Does not enable bridge parsing.
	 *
	 * @see ParseTable#hasRecovers()   Determines if the parse table supports recovery rules
	 */
	public final void setUseStructureRecovery(boolean useRecovery) {
		setUseStructureRecovery(useRecovery, null);
	}
	
    protected void setFineGrainedOnRegion(boolean fineGrainedMode) {
        fineGrainedOnRegion = fineGrainedMode;
        recoverStacks = new ArrayDeque<Frame>();
    }

    @Deprecated
    protected void setUseFineGrained(boolean useFG) {
        recoverIntegrator.setUseFineGrained(useFG);
    }

    // FIXME: we have way to many of these accessors; does this have to be public?
    //        if not for normal use, it should at least be 'internalSet....'
    @Deprecated
    public void setCombinedRecovery(boolean useBP, boolean useFG,
            boolean useOnlyFG) {
        recoverIntegrator.setOnlyFineGrained(useOnlyFG);
        recoverIntegrator.setUseBridgeParser(useBP);
        recoverIntegrator.setUseFineGrained(useFG);
    }

    // LK: this thing gets reset every time; why a setter?
    @Deprecated
    public void setPerformanceMeasuring(RecoveryPerformance performanceMeasuring) {
        this.performanceMeasuring = performanceMeasuring;
    }

    public RecoveryPerformance getPerformanceMeasuring() {
        return performanceMeasuring;
    }

	/**
	 * @deprecated Use {@link #asyncCancel()} instead.
	 */
	@Deprecated
	public void asyncAbort() {
		asyncCancel();
	}

	/**
	 * Aborts an asynchronously running parse job, causing it to throw an exception.
	 *
	 * (Provides no guarantee that the parser is actually cancelled.)
	 */
	public void asyncCancel() {
		asyncAborted = true;
	}

	public void asyncCancelReset() {
		asyncAborted = false;
	}

	public static boolean isDebugging() {
		return Tools.debugging;
	}

	public static boolean isLogging() {
		return Tools.logging;
	}

	/**
	 * Initializes the active stacks. At the start of parsing there is only one
	 * active stack, and this stack contains the start symbol obtained from the
	 * parse table.
	 *
	 * @return top-level frame of the initial stack
	 */
	private Frame initActiveStacks() {
		activeStacks.clear();
		final Frame st0 = newStack(parseTable.getInitialState());
		addStack(st0);
		return st0;
	}
	
	@Deprecated
	public Object parse(String fis) throws BadTokenException,
    TokenExpectedException, ParseException, SGLRException {
	    return parse(fis, null, null);
	}

    @Deprecated
	public final Object parse(String input, String filename) throws BadTokenException,
    TokenExpectedException, ParseException, SGLRException {

        return parse(input, filename, null);
    }

	/**
	 * Parses a string and constructs a new tree using the tree builder.
	 * 
	 * @param input        The input string.
	 * @param filename     The source filename of the string, or null if not available.
	 * @param startSymbol  The start symbol to use, or null if any applicable.
	 */
    public Object parse(String input, String filename, String startSymbol) throws BadTokenException, TokenExpectedException, ParseException,
	SGLRException {
		logBeforeParsing();
		initParseVariables(input, filename);
		startTime = System.currentTimeMillis();
		initParseTimer();
        getPerformanceMeasuring().startParse();
        Object result = sglrParse(startSymbol);
        getPerformanceMeasuring().endParse();
        return result;
	}

	private Object sglrParse(String startSymbol)
	throws BadTokenException, TokenExpectedException,
	ParseException, SGLRException {

		try {
			do {
				readNextToken();
				history.keepTokenAndState(this);
				doParseStep();
			} while (currentToken != SGLR.EOF && activeStacks.size() > 0);

			if (acceptingStack == null) {
				collectedErrors.add(createBadTokenException());
			}

			if(useIntegratedRecovery && acceptingStack==null){
				recoverIntegrator.recover();
				if(acceptingStack==null && activeStacks.size()>0) {
					return sglrParse(startSymbol);
				}
			}
		} catch (final TaskCancellationException e) {
			throw new ParseTimeoutException(this, currentToken, tokensSeen - 1, lineNumber,
					columnNumber, collectedErrors);
		}

		logAfterParsing();

		final Link s = acceptingStack.findDirectLink(startFrame);

		if (s == null) {
			throw new ParseException(this, "Accepting stack has no link");
		}

		logParseResult(s);
		Tools.debug("avoids: ", s.recoverCount);
		//Tools.debug(s.label.toParseTree(parseTable));

		if (parseTable.getTreeBuilder() instanceof NullTreeBuilder) {
			return null;
		} else {
			return disambiguator.applyFilters(this, s.label, startSymbol, tokensSeen);
		}
	}

	void readNextToken() {
		logCurrentToken();
		currentToken = getNextToken();
	}

	public void doParseStep() {
		parseCharacter(); //applies reductions on active stack structure and fills forshifter
		shifter(); //renewes active stacks with states in forshifter
	}

	private void initParseVariables(String input, String filename) {
		forActor.clear();
		forActorDelayed.clear();
		forShifter.clear();
		history.clear();
		startFrame = initActiveStacks();
		tokensSeen = 0;
		columnNumber = 0;
		lineNumber = 1;
		currentInputStream = new PushbackStringIterator(input);
		acceptingStack = null;
		collectedErrors.clear();
		history=new ParserHistory();
		performanceMeasuring=new RecoveryPerformance();
		parseTable.getTreeBuilder().initializeInput(input, filename);
		PooledPathList.resetPerformanceCounters();
		PathListPool.resetPerformanceCounters();
		ambiguityManager = new AmbiguityManager(input.length());
		
	}

	private BadTokenException createBadTokenException() {

		final Frame singlePreviousStack = activeStacks.size() == 1 ? activeStacks.get(0) : null;

		if (singlePreviousStack != null) {
			Action action = singlePreviousStack.peek().getSingularAction();

			if (action != null && action.getActionItems().length == 1) {
				final StringBuilder expected = new StringBuilder();

				do {
					final int token = action.getSingularRange();
					if (token == -1) {
						break;
					}
					expected.append((char) token);

					final ActionItem[] items = action.getActionItems();

					if (!(items.length == 1 && items[0].type == ActionItem.SHIFT)) {
						break;
					}

					final Shift shift = (Shift) items[0];
					action = parseTable.getState(shift.nextState).getSingularAction();

				} while (action != null);

				if (expected.length() > 0) {
					return new TokenExpectedException(this, expected.toString(), currentToken,
							tokensSeen - 1, lineNumber, columnNumber);
				}
			}
		}

		return new BadTokenException(this, currentToken, tokensSeen - 1, lineNumber,
				columnNumber);
	}

	private void shifter() {
		logBeforeShifter();
		clearActiveStacks();

		final AbstractParseNode prod = parseTable.lookupProduction(currentToken);

		while (forShifter.size() > 0) {

			final ActionState as = forShifter.remove();

			if (!parseTable.hasRejects() || !as.st.allLinksRejected()) {
				Frame st1 = findStack(activeStacks, as.s);
				if (st1 == null) {
					st1 = newStack(as.s);
					addStack(st1);
				}
				st1.addLink(as.st, prod, 1);
			} else {
				if (Tools.logging) {
					Tools.logger("Shifter: skipping rejected stack with state ",
							as.st.state.stateNumber);
				}
			}
		}
		logAfterShifter();
	}

	public void addStack(Frame st1) {
		if(Tools.tracing) {
			TRACE("SG_AddStack() - " + st1.state.stateNumber);
		}
		activeStacks.addFirst(st1);
	}

	private void parseCharacter() {
		logBeforeParseCharacter();

		activeStacksWorkQueue.clear();
		for(int i = 0; i < activeStacks.size(); i++) {
			activeStacksWorkQueue.add(activeStacks.get(i));
		}

		clearForActorDelayed();
		clearForShifter();

		while (activeStacksWorkQueue.size() > 0 || forActor.size() > 0) {
			final Frame st = pickStackNodeFromActivesOrForActor(activeStacksWorkQueue);
			if (!st.allLinksRejected()) {
				actor(st);
			}

			if(activeStacksWorkQueue.size() == 0 && forActor.size() == 0) {
				fillForActorWithDelayedFrames(); //Fills foractor, clears foractor delayed
			}
		}
	}

	private void fillForActorWithDelayedFrames() {
		if(Tools.tracing) {
			TRACE("SG_ - both empty");
		}
		final ArrayDeque<Frame> tmp = forActor;
		forActor = forActorDelayed;
		tmp.clear();
		forActorDelayed = tmp;
	}

	private Frame pickStackNodeFromActivesOrForActor(ArrayDeque<Frame> actives) {
		Frame st;
		if(actives.size() > 0) {
			if(Tools.tracing) {
				TRACE("SG_ - took active");
			}
			st = actives.remove();
		} else {
			if(Tools.tracing) {
				TRACE("SG_ - took foractor");
			}
			st = forActor.remove();
		}
		return st;
	}

	private void actor(Frame st) {

		final State s = st.peek();
		logBeforeActor(st, s);

		for (final Action action : s.getActions()) {
			if (action.accepts(currentToken)) {
				for (final ActionItem ai : action.getActionItems()) {
					switch (ai.type) {
					case ActionItem.SHIFT: {
						final Shift sh = (Shift) ai;
						final ActionState actState = new ActionState(st, parseTable.getState(sh.nextState));
						actState.currentToken = currentToken;
						addShiftPair(actState); //Adds StackNode to forshifter
						statsRecordParsers(); //sets some values un current parse state
						break;
					}
					case ActionItem.REDUCE: {
						final Reduce red = (Reduce) ai;
						doReductions(st, red.production);
						break;
					}
					case ActionItem.REDUCE_LOOKAHEAD: {
						final ReduceLookahead red = (ReduceLookahead) ai;
						if(checkLookahead(red)) {
							if(Tools.tracing) {
								TRACE("SG_ - ok");
							}
							doReductions(st, red.production);
						}
						break;
					}
					case ActionItem.ACCEPT: {
						if (!st.allLinksRejected()) {
							acceptingStack = st;
							if (Tools.logging) {
								Tools.logger("Reached the accept state");
							}
						}
						break;
					}
					default:
						throw new NotImplementedException();
					}
				}
			}
		}

		if(Tools.tracing) {
			TRACE("SG_ - actor done");
		}
	}

	private boolean checkLookahead(ReduceLookahead red) {
		return doCheckLookahead(red, red.getCharRanges(), 0);
	}

	private boolean doCheckLookahead(ReduceLookahead red, RangeList[] charClass, int pos) {
		if(Tools.tracing) {
			TRACE("SG_CheckLookAhead() - ");
		}

		final int c = currentInputStream.read();

		// EOF
		if(c == -1) {
			return true;
		}

		boolean permit = true;

		if(pos < charClass.length) {
			permit = charClass[pos].within(c) ? false : doCheckLookahead(red, charClass, pos + 1);
		}

		currentInputStream.unread(c);

		return permit;
	}

	private void addShiftPair(ActionState state) {
		if(Tools.tracing) {
			TRACE("SG_AddShiftPair() - " + state.s.stateNumber);
		}
		forShifter.add(state);
	}

	private void statsRecordParsers() {
		if (forShifter.size() > maxBranches) {
			maxBranches = forShifter.size();
			maxToken = currentToken;
			maxColumn = columnNumber;
			maxLine = lineNumber;
			maxTokenNumber = tokensSeen;
		}
	}


	private void doReductions(Frame st, Production prod) {

		if(!recoverModeOk(st, prod)) {
			return;
		}

		final PooledPathList paths = reductionsPathCache.start();
		//System.out.println(paths.size());
		st.findAllPaths(paths, prod.arity);
		//System.out.println(paths.size());
		logBeforeDoReductions(st, prod, paths.size());
		reduceAllPaths(prod, paths);
		logAfterDoReductions();
		paths.end();
	}

	private boolean recoverModeOk(Frame st, Production prod) {
		return !prod.isRecoverProduction() || fineGrainedOnRegion;
	}

	private void doLimitedReductions(Frame st, Production prod, Link l) { //Todo: Look add sharing code with doReductions
		if(!recoverModeOk(st, prod)) {
			return;
		}

		final PooledPathList limitedPool = pathCache.create();
		st.findLimitedPaths(limitedPool, prod.arity, l); //find paths containing the link
		logBeforeLimitedReductions(st, prod, l, limitedPool);
		reduceAllPaths(prod, limitedPool);
		limitedPool.end();
	}

	private void reduceAllPaths(Production prod, PooledPathList paths) {

		for(int i = 0; i < paths.size(); i++) {
			final Path path = paths.get(i);
			final AbstractParseNode[] kids = path.getParseNodes();
			final Frame st0 = path.getEnd();
			final State next = parseTable.go(st0.peek(), prod.label);
			logReductionPath(prod, path, st0, next);
			reducer(st0, next, prod, kids, path);
		}

		if (asyncAborted) {
			// Rethrown as ParseTimeoutException in SGLR.sglrParse()
			throw new TaskCancellationException("Long-running parse job aborted");
		}
	}


	private void reducer(Frame st0, State s, Production prod, AbstractParseNode[] kids, Path path) {

		logBeforeReducer(s, prod, path.getLength());
		increaseReductionCount();

		final int length = path.getLength();
		final int numberOfRecoveries = calcRecoverCount(prod, path);
		final AbstractParseNode t = prod.apply(kids);
		final Frame st1 = findStack(activeStacks, s);

		if (st1 == null) {
			if(prod.isRecoverProduction()){
				addNewRecoverStack(st0, s, prod, length, numberOfRecoveries, t);
				return;
			}
			addNewStack(st0, s, prod, length, numberOfRecoveries, t);
		} else {
			/* A stack with state s exists; check for ambiguities */
			Link nl = st1.findDirectLink(st0);

			if (nl != null) {
				if(prod.isRecoverProduction()){
					return;
				}
				logAmbiguity(st0, prod, st1, nl);
				if (prod.isRejectProduction()) {
					nl.reject();
				}
				if(numberOfRecoveries == 0 && nl.recoverCount == 0 || nl.isRejected()) {
					createAmbNode(t, nl);
				} else if (numberOfRecoveries < nl.recoverCount) {
					nl.label = t;
					nl.recoverCount = numberOfRecoveries;
					actorOnActiveStacksOverNewLink(nl);
				} else if (numberOfRecoveries == nl.recoverCount) {
					nl.label = t;
				}
			} else {
				if(prod.isRecoverProduction()) {
					addNewRecoverStack(st0, s, prod, length, numberOfRecoveries, t);
					return;
				}
				nl = st1.addLink(st0, t, length);
				nl.recoverCount = numberOfRecoveries;
				if (prod.isRejectProduction()) {
					nl.reject();
					increaseRejectCount();
				}
				logAddedLink(st0, st1, nl);
				actorOnActiveStacksOverNewLink(nl);
			}
		}
		if(Tools.tracing) {
			TRACE_ActiveStacks();
			TRACE("SG_ - reducer done");
		}
	}

	private void createAmbNode(AbstractParseNode t, Link nl) {
		nl.addAmbiguity(t, tokensSeen);
		ambiguityManager.increaseAmbiguityCalls();
	}

	/**
	 * Found no existing stack with for state s; make new stack
	 */
	private void addNewStack(Frame st0, State s, Production prod, int length,
			int numberOfRecoveries, AbstractParseNode t) {

		final Frame st1 = newStack(s);
		final Link nl = st1.addLink(st0, t, length);

		nl.recoverCount = numberOfRecoveries;
		addStack(st1);
		forActorDelayed.addFirst(st1);

		if(Tools.tracing) {
			TRACE("SG_AddStack() - " + st1.state.stateNumber);
		}

		if (prod.isRejectProduction()) {
			if (Tools.logging) {
				Tools.logger("Reject [new]");
			}
			nl.reject();
			increaseRejectCount();
		}
	}

	/**
	 *  Found no existing stack with for state s; make new stack
	 */
	private void addNewRecoverStack(Frame st0, State s, Production prod, int length,
			int numberOfRecoveries, AbstractParseNode t) {
		if (!(fineGrainedOnRegion && !prod.isRejectProduction())) {
			return;
		}
		final Frame st1 = newStack(s);
		final Link nl = st1.addLink(st0, t, length);
		nl.recoverCount = numberOfRecoveries;
		recoverStacks.addFirst(st1);
	}

	private void actorOnActiveStacksOverNewLink(Link nl) {
		// Note: ActiveStacks can be modified inside doLimitedReductions
		// new elements may be inserted at the beginning
		final int sz = activeStacks.size();
		for (int i = 0; i < sz; i++) {
			//                for(Frame st2 : activeStacks) {
			if(Tools.tracing) {
				TRACE("SG_ activeStack - ");
			}
			final int pos = activeStacks.size() - sz + i;
			final Frame st2 = activeStacks.get(pos);
			if (st2.allLinksRejected() || inReduceStacks(forActor, st2) || inReduceStacks(forActorDelayed, st2))
			{
				continue; //stacknode will find reduction in regular process
			}

			for (final Action action : st2.peek().getActions()) {
				if (action.accepts(currentToken)) {
					for (final ActionItem ai : action.getActionItems()) {
						switch(ai.type) {
						case ActionItem.REDUCE:
							final Reduce red = (Reduce) ai;
							doLimitedReductions(st2, red.production, nl);
							break;
						case ActionItem.REDUCE_LOOKAHEAD:
							final ReduceLookahead red2 = (ReduceLookahead) ai;
							if(checkLookahead(red2)) {
								doLimitedReductions(st2, red2.production, nl);
							}
							break;
						}
					}
				}
			}
		}
	}

	private int calcRecoverCount(Production prod, Path path) {
		return path.getRecoverCount() + (prod.isRecoverProduction() ? 1 : 0);
	}

	private boolean inReduceStacks(Queue<Frame> q, Frame frame) {
		if(Tools.tracing) {
			TRACE("SG_InReduceStacks() - " + frame.state.stateNumber);
		}
		return q.contains(frame);
	}

	protected Frame newStack(State s) {
		if(Tools.tracing) {
			TRACE("SG_NewStack() - " + s.stateNumber);
		}
		return new Frame(s);
	}

	private void increaseReductionCount() {
		reductionCount++;
	}

	protected void increaseRejectCount() {
		rejectCount++;
	}

	protected int getRejectCount() {
		return rejectCount;
	}

	private Frame findStack(ArrayDeque<Frame> stacks, State s) {
		if(Tools.tracing) {
			TRACE("SG_FindStack() - " + s.stateNumber);
		}

		// We need only check the top frames of the active stacks.
		if (Tools.debugging) {
			Tools.debug("findStack() - ", dumpActiveStacks());
			Tools.debug(" looking for ", s.stateNumber);
		}

		final int size = stacks.size();
		for (int i = 0; i < size; i++) {
			if (stacks.get(i).state.stateNumber == s.stateNumber) {
				if(Tools.tracing) {
					TRACE("SG_ - found stack");
				}
				return stacks.get(i);
			}
		}
		if(Tools.tracing) {
			TRACE("SG_ - stack not found");
		}
		return null;
	}


	private int getNextToken() {
		if(Tools.tracing) {
			TRACE("SG_NextToken() - ");
		}

		final int ch = currentInputStream.read();
		updateLineAndColumnInfo(ch);
		if(ch == -1) {
			return SGLR.EOF;
		}
		return ch;
	}

	protected void updateLineAndColumnInfo(int ch) {
		tokensSeen++;

		if (Tools.debugging) {
			Tools.debug("getNextToken() - ", ch, "(", (char) ch, ")");
		}

		switch (ch) {
		case '\n':
			lineNumber++;
			columnNumber = 0;
			break;
		case '\t':
			columnNumber = (columnNumber / TAB_SIZE + 1) * TAB_SIZE;
			break;
		case -1:
			break;
		default:
			columnNumber++;
		}
	}

	@Deprecated
	public void setFilter(boolean filter) {
		getDisambiguator().setFilterAny(filter);
	}

	public void clear() {
		if (this.acceptingStack != null) {
			this.acceptingStack.clear();
		}

		clearActiveStacksDeep();
		clearForActorDelayedDeep();
		clearForActorDeep();
		clearForShifterDeep();

		this.parseTable = null;
		this.ambiguityManager = null;
	}

	private void clearForShifterDeep() {
		for (final ActionState as : forShifter) {
			as.clear(true);
		}
		clearForShifter();
	}

	private void clearForShifter() {
		forShifter.clear();
	}

	private void clearForActor() {
		forActor.clear();
	}

	private void clearForActorDeep() {
		for (final Frame frame : forActor) {
			frame.clear();
		}
		clearForActor();
	}

	private void clearForActorDelayedDeep() {
		for (final Frame frame : forActorDelayed) {
			frame.clear();

		}
		clearForActorDelayed();
	}

	private void clearForActorDelayed() {
		forActorDelayed.clear(true);
	}

	private void clearActiveStacksDeep() {
		for (final Frame frame : activeStacks) {
			frame.clear();
		}
		clearActiveStacks();
	}

	private void clearActiveStacks() {
		activeStacks.clear(true);
	}

	public ParseTable getParseTable() {
		return parseTable;
	}

	public void setTreeBuilder(ITreeBuilder treeBuilder) {
		parseTable.setTreeBuilder(treeBuilder);
	}

	public ITreeBuilder getTreeBuilder() {
		return parseTable.getTreeBuilder();
	}

	AmbiguityManager getAmbiguityManager() {
		return ambiguityManager;
	}

	public Disambiguator getDisambiguator() {
		return disambiguator;
	}

	public void setDisambiguator(Disambiguator disambiguator) {
		this.disambiguator = disambiguator;
	}

	@Deprecated
	public ITermFactory getFactory() {
		return parseTable.getFactory();
	}

	public int getReductionCount() {
		return reductionCount;
	}

	public int getRejectionCount() {
		return rejectCount;
	}

	@Deprecated
	public static void setWorkAroundMultipleLookahead(boolean value) {
		WORK_AROUND_MULTIPLE_LOOKAHEAD = value;
	}





	////////////////////////////////////////////////////// Log functions ///////////////////////////////////////////////////////////////////////////////

	private static int traceCallCount = 0;

	static void TRACE(String string) {
		System.err.println("[" + traceCallCount + "] " + string);
		traceCallCount++;
	}

	private String dumpActiveStacks() {
		final StringBuffer sb = new StringBuffer();
		boolean first = true;
		if (activeStacks == null) {
			sb.append(" GSS unitialized");
		} else {
			sb.append("{").append(activeStacks.size()).append("} ");
			for (final Frame f : activeStacks) {
				if (!first) {
					sb.append(", ");
				}
				sb.append(f.dumpStack());
				first = false;
			}
		}
		return sb.toString();
	}


	private void logParseResult(Link s) {
		if (isDebugging()) {
			Tools.debug("internal parse tree:\n", s.label);
		}

		if(Tools.tracing) {
			TRACE("SG_ - internal tree: " + s.label);
		}

		if (Tools.measuring) {
			final Measures m = new Measures();
			//Tools.debug("Time (ms): " + (System.currentTimeMillis()-startTime));
			m.setTime(System.currentTimeMillis() - startTime);
			//Tools.debug("Red.: " + reductionCount);
			m.setReductionCount(reductionCount);
			//Tools.debug("Nodes: " + Frame.framesCreated);
			m.setFramesCreated(Frame.framesCreated);
			//Tools.debug("Links: " + Link.linksCreated);
			m.setLinkedCreated(Link.linksCreated);
			//Tools.debug("avoids: " + s.avoidCount);
			m.setAvoidCount(s.recoverCount);
			//Tools.debug("Total Time: " + parseTime);
			m.setParseTime(parseTime);
			//Tools.debug("Total Count: " + parseCount);
			Measures.setParseCount(++parseCount);
			//Tools.debug("Average Time: " + (int)parseTime / parseCount);
			m.setAverageParseTime((int)parseTime / parseCount);
			m.setRecoverTime(-1);
			Tools.setMeasures(m);
		}
	}


	private void logBeforeParsing() {
		if(Tools.tracing) {
			TRACE("SG_Parse() - ");
		}

		if (Tools.debugging) {
			Tools.debug("parse() - ", dumpActiveStacks());
		}
	}

	private void logAfterParsing()
	throws BadTokenException, TokenExpectedException {
		if (isLogging()) {
			Tools.logger("Number of lines: ", lineNumber);
			Tools.logger("Maximum ", maxBranches, " parse branches reached at token ",
					logCharify(maxToken), ", line ", maxLine, ", column ", maxColumn,
					" (token #", maxTokenNumber, ")");

			final long elapsed = System.currentTimeMillis() - startTime;
			Tools.logger("Parse time: " + elapsed / 1000.0f + "s");
		}

		if (isDebugging()) {
			Tools.debug("Parsing complete: all tokens read");
		}

		if (acceptingStack == null) {
			final BadTokenException bad = createBadTokenException();
			if (collectedErrors.isEmpty()) {
				throw bad;
			} else {
				collectedErrors.add(bad);
				throw new MultiBadTokenException(this, collectedErrors);
			}
		}


		if (isDebugging()) {
			Tools.debug("Accepting stack exists");
		}
	}

	private void logCurrentToken() {
		if (isLogging()) {
			Tools.logger("Current token (#", tokensSeen, "): ", logCharify(currentToken));
		}
	}

	private void logAfterShifter() {
		if(Tools.tracing) {
			TRACE("SG_DiscardShiftPairs() - ");
			TRACE_ActiveStacks();
		}
	}

	private void logBeforeShifter() {
		if(Tools.tracing) {
			TRACE("SG_Shifter() - ");
			TRACE_ActiveStacks();
		}

		if (Tools.logging) {
			Tools.logger("#", tokensSeen, ": shifting ", forShifter.size(), " parser(s) -- token ",
					logCharify(currentToken), ", line ", lineNumber, ", column ", columnNumber);
		}

		if (Tools.debugging) {
			Tools.debug("shifter() - " + dumpActiveStacks());

			Tools.debug(" token   : " + currentToken);
			Tools.debug(" parsers : " + forShifter.size());
		}
	}

	private void logBeforeParseCharacter() {
		if(Tools.tracing) {
			TRACE("SG_ParseToken() - ");
		}

		if (Tools.debugging) {
			Tools.debug("parseCharacter() - " + dumpActiveStacks());
			Tools.debug(" # active stacks : " + activeStacks.size());
		}

		/* forActor = *///computeStackOfStacks(activeStacks);

		if (Tools.debugging) {
			Tools.debug(" # for actor     : " + forActor.size());
		}
	}

	private String logCharify(int currentToken) {
		switch (currentToken) {
		case 32:
			return "\\32";
		case SGLR.EOF:
			return "EOF";
		case '\n':
			return "\\n";
		case 0:
			return "\\0";
		default:
			return "" + (char) currentToken;
		}
	}

	private void logBeforeActor(Frame st, State s) {
		List<ActionItem> actionItems = null;

		if (Tools.debugging || Tools.tracing) {
			actionItems = s.getActionItems(currentToken);
		}

		if(Tools.tracing) {
			TRACE("SG_Actor() - " + st.state.stateNumber);
			TRACE_ActiveStacks();
		}

		if (Tools.debugging) {
			Tools.debug("actor() - ", dumpActiveStacks());
		}

		if (Tools.debugging) {
			Tools.debug(" state   : ", s.stateNumber);
			Tools.debug(" token   : ", currentToken);
		}

		if (Tools.debugging) {
			Tools.debug(" actions : ", actionItems);
		}

		if(Tools.tracing) {
			TRACE("SG_ - actions: " + actionItems.size());
		}
	}

	private void logAfterDoReductions() {
		if (Tools.debugging) {
			Tools.debug("<doReductions() - " + dumpActiveStacks());
		}

		if(Tools.tracing) {
			TRACE("SG_ - doreductions done");
		}
	}

	private void logReductionPath(Production prod, Path path, Frame st0, State next) {
		if (Tools.debugging) {
			Tools.debug(" path: ", path);
			Tools.debug(st0.state);
		}

		if (Tools.logging) {
			Tools.logger("Goto(", st0.peek().stateNumber, ",", prod.label + ") == ",
					next.stateNumber);
		}
	}


	private void logBeforeDoReductions(Frame st, Production prod,
			final int pathsCount) {
		if(Tools.tracing) {
			TRACE("SG_DoReductions() - " + st.state.stateNumber);
		}

		if (Tools.debugging) {
			Tools.debug("doReductions() - " + dumpActiveStacks());
			logReductionInfo(st, prod);
			Tools.debug(" paths : " + pathsCount);
		}
	}

	private void logBeforeLimitedReductions(Frame st, Production prod, Link l,
			PooledPathList paths) {
		if(Tools.tracing) {
			TRACE("SG_ - back in reducer ");
			TRACE_ActiveStacks();
			TRACE("SG_DoLimitedReductions() - " + st.state.stateNumber + ", " + l.parent.state.stateNumber);
		}

		if (Tools.debugging) {
			Tools.debug("doLimitedReductions() - ", dumpActiveStacks());
			logReductionInfo(st, prod);
			Tools.debug(Arrays.asList(paths));
		}
	}

	private void logReductionInfo(Frame st, Production prod) {
		Tools.debug(" state : ", st.peek().stateNumber);
		Tools.debug(" token : ", currentToken);
		Tools.debug(" label : ", prod.label);
		Tools.debug(" arity : ", prod.arity);
		Tools.debug(" stack : ", st.dumpStack());
	}

	private void logAddedLink(Frame st0, Frame st1, Link nl) {
		if (Tools.debugging) {
			Tools.debug(" added link ", nl, " from ", st1.state.stateNumber, " to ",
					st0.state.stateNumber);
		}

		if(Tools.tracing) {
			TRACE_ActiveStacks();
		}
	}

	private void logBeforeReducer(State s, Production prod, int length) {
		if(Tools.tracing) {
			TRACE("SG_Reducer() - " + s.stateNumber + ", " + length + ", " + prod.label);
			TRACE_ActiveStacks();
		}

		if (Tools.logging) {
			Tools.logger("Reducing; state ", s.stateNumber, ", token: ", logCharify(currentToken),
					", production: ", prod.label);
		}

		if (Tools.debugging) {
			Tools.debug("reducer() - ", dumpActiveStacks());

			Tools.debug(" state      : ", s.stateNumber);
			Tools.debug(" token      : ", logCharify(currentToken) + " (" + currentToken + ")");
			Tools.debug(" production : ", prod.label);
		}
	}

	private void TRACE_ActiveStacks() {
		TRACE("SG_ - active stacks: " + activeStacks.size());
		TRACE("SG_ - for_actor stacks: " + forActor.size());
		TRACE("SG_ - for_actor_delayed stacks: " + forActorDelayed.size());
	}


	private void logAmbiguity(Frame st0, Production prod, Frame st1, Link nl) {
		if (Tools.logging) {
			Tools.logger("Ambiguity: direct link ", st0.state.stateNumber, " -> ",
					st1.state.stateNumber, " ", (prod.isRejectProduction() ? "{reject}" : ""));
			if (nl.label instanceof ParseNode) {
				Tools.logger("nl is ", nl.isRejected() ? "{reject}" : "", " for ",
						((ParseNode) nl.label).label);
			}
		}

		if (Tools.debugging) {
			Tools.debug("createAmbiguityCluster - ", tokensSeen - nl.getLength() - 1, "/",
					nl.getLength());
		}
	}
}
