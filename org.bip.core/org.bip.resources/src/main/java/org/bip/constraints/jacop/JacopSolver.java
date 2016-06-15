package org.bip.constraints.jacop;

import java.util.ArrayList;

import org.bip.constraint.ConstraintSolver;
import org.bip.constraint.DnetConstraint;
import org.bip.constraint.ExpressionCreator;
import org.bip.constraint.ResourceAllocation;
import org.jacop.constraints.Constraint;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.search.DepthFirstSearch;
import org.jacop.search.IndomainMin;
import org.jacop.search.Search;
import org.jacop.search.SelectChoicePoint;
import org.jacop.search.SimpleSelect;
import org.jacop.search.SolutionListener;


public class JacopSolver implements ConstraintSolver {

	private JacopFactory factory;
	private ResourceAllocation allocation;
	
	/**
	 * It contains all variables used within a specific example.
	 */
	private ArrayList<IntVar> vars;
	/**
	 * It specifies the constraint store responsible for holding information 
	 * about constraints and variables.
	 */
	private Store store;
	
	/**
	 * It specifies the search procedure used by a given example.
	 */
	private Search<IntVar> search;
	
	public JacopSolver() {
		store = new Store();
		vars = new ArrayList<IntVar>();
		factory = new JacopFactory(store, vars);
		//newCycle();
	}
	
	@Override
	public boolean isSolvable() {
		
		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[1]), null,
				new IndomainMin<IntVar>());

		search = new DepthFirstSearch<IntVar>();

		boolean result = search.labeling(store, select);
		return result;
	}

	@Override
	public ResourceAllocation getAllocation() {
		SolutionListener<IntVar> sl = search.getSolutionListener();
		return new JacopResourceAllocation(sl.getVariables(), search.getSolution());
	}

	@Override
	public void addConstraint(DnetConstraint constraint) {
		if (constraint!=null)
		store.impose(constraint.cstr());
		
	}
	
	public void addVariable(IntVar e) {
		vars.add(e);
	}

	@Override
	public ExpressionCreator expressionCreator() {
		return factory;
	}

	@Override
	public void newCycle() {
		store = new Store();
		vars.clear();
		factory.reinit(store, vars);// = new JacopFactory(store, vars);
	}

}
