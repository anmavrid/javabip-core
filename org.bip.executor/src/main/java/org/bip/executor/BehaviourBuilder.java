/*
 * Copyright (c) 2012 Crossing-Tech TM Switzerland. All right reserved.
 * Copyright (c) 2012, RiSD Laboratory, EPFL, Switzerland.
 *
 * Author: Simon Bliudze, Alina Zolotukhina, Anastasia Mavridou, and Radoslaw Szymanek
 * Date: 10/15/12
 */
package org.bip.executor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.bip.api.ComponentProvider;
import org.bip.api.Data;
import org.bip.api.ExecutableBehaviour;
import org.bip.api.Guard;
import org.bip.api.Port;
import org.bip.api.PortType;
import org.bip.api.ResourceType;
import org.bip.exceptions.BIPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO DESCRIPTION all classes should have a header and description of its purpose for nice looking JavaDoc document.
// For example check BIPSpecification for an example.

// TODO, EXTENSION autoboxing between for example int and Integer may be a good feature to help wire data from multiple components.
/**
 * Gathers all the information to build a behaviour of a component. There are two ways to build it: with data and without data.
 */
public class BehaviourBuilder {

	private Logger logger = LoggerFactory.getLogger(BehaviourBuilder.class);

	public String componentType;
	public String currentState;
	private ArrayList<TransitionImpl> allTransitions;
	private Hashtable<String, Port> allPorts;
	private HashSet<String> states;
	private Hashtable<String, Guard> guards;
	private Object component;

	private Hashtable<String, MethodHandle> dataOutName;

	private ArrayList<DataOutImpl<?>> dataOut;
	private ArrayList<ResourceReqImpl> resources;

	//helper map in needed to construct required resources to transition map
	private Hashtable<Method, ArrayList<ResourceReqImpl>> methodReqResources;
	//helper map in needed to construct releasedresources to transition map
	private Hashtable<Method, ArrayList<String>> methodReleaseResources;
	//helper map in needed to construct transition to utility map
	private Hashtable<Method, String> methodUtility;
	//helper map to construct resource to transition map
	private Hashtable<Method, TransitionImpl> methodToTransition;
    // the name of the managed resource for the Resource Manager, empty for a regular component
    private String resourceName;
	
	public BehaviourBuilder(Object component) {
		this.component = component;
		allTransitions = new ArrayList<TransitionImpl>();
		allPorts = new Hashtable<String, Port>();
		states = new HashSet<String>();
		guards = new Hashtable<String, Guard>();
		dataOutName = new Hashtable<String, MethodHandle>();
		dataOut = new ArrayList<DataOutImpl<?>>();
		resources = new ArrayList<ResourceReqImpl>();
		methodReqResources = new Hashtable<Method, ArrayList<ResourceReqImpl>>();
		methodReleaseResources = new Hashtable<Method, ArrayList<String>>();
		methodUtility = new Hashtable<Method, String>();
		methodToTransition = new Hashtable<Method, TransitionImpl>();
	}

	public ExecutableBehaviour build(ComponentProvider provider) throws BIPException {
		
		if (componentType == null || componentType.isEmpty()) {
			throw new NullPointerException("Component type for object " + component + " cannot be null or empty.");
		}	
		if (currentState == null || currentState.isEmpty()) {
			throw new NullPointerException("The initial state of the component of type " + componentType + " cannot be null or empty.");
		}
		if (allTransitions == null || allTransitions.isEmpty()) {
			throw new BIPException("List of transitions in component of type " + componentType + " cannot be null or empty.");
		}
		if (states == null || states.isEmpty()) {
			throw new BIPException("List of states in component of type " + componentType + " cannot be null or empty.");
		}
		if (allPorts == null || allPorts.isEmpty()) {
			throw new BIPException("List of states in component of type " + componentType + " cannot be null or empty.");
		}
		if (component == null) {
			throw new NullPointerException("The component object of type " + componentType + " cannot be null.");
		}
		
		 Hashtable<TransitionImpl,  ArrayList<ResourceReqImpl>> transitionResources = new Hashtable<TransitionImpl,  ArrayList<ResourceReqImpl>>();
		 Hashtable<TransitionImpl, String> transitionRequest = new Hashtable<TransitionImpl, String>();
		 Hashtable<TransitionImpl,  ArrayList<String>> transitionRelease = new Hashtable<TransitionImpl,  ArrayList<String>>();
		 

		ArrayList<Port> componentPorts = new ArrayList<Port>();
		// We need to create new ports here as there was no provider information available when the specification was parsed.
		for (Port port : this.allPorts.values()) {
			componentPorts.add(new PortImpl(port.getId(), port.getType(), port.getSpecType(), provider));
		}

		Map<String, Port> allEnforceablePorts = new HashMap<String, Port>();
		for (Port port : componentPorts) {
			if (port.getType().equals(PortType.enforceable))
				allEnforceablePorts.put(port.getId(), port);
		}
		
		for (DataOutImpl<?> data : dataOut) {
			data.computeAllowedPort(allEnforceablePorts);
		}

		ArrayList<ExecutableTransition> transformedTransitions = transformIntoExecutableTransition();
		
		for (Method method : methodReqResources.keySet()) {
			transitionResources.put(methodToTransition.get(method), methodReqResources.get(method));
			transitionRequest.put(methodToTransition.get(method), methodUtility.get(method));
		}
		for (Method method : methodReleaseResources.keySet()) {
			transitionRelease.put(methodToTransition.get(method), methodReleaseResources.get(method));
		}
		if (methodReqResources.size() != methodUtility.size()) {
			throw new BIPException("There is a transition where either the required resources or the utility function is not specified");
		}
		
		if (!resources.isEmpty())
		{
			return new BehaviourImpl(componentType, currentState, transformedTransitions, 
					 componentPorts, states, guards.values(), dataOut, dataOutName, component,
					 transitionResources, transitionRelease, transitionRequest);
		}
				
		return new BehaviourImpl(componentType, currentState, resourceName, transformedTransitions, 
								 componentPorts, states, guards.values(), dataOut, dataOutName, component);
	}
	

	
	private ArrayList<ExecutableTransition> transformIntoExecutableTransition() {

		HashMap<String, Port> mapIdToPort = new HashMap<String, Port>( );
		for (Port port : allPorts.values())
			mapIdToPort.put(port.getId(), port);
		
		// Transform transitions into ExecutableTransitions.
		ArrayList<ExecutableTransition> transformedAllTransitions = new ArrayList<ExecutableTransition>();
		for (TransitionImpl transition : allTransitions) {
			
			// TODO DESIGN, what are exactly different ways of specifying that the port is internal. We need to be specific about it in spec.
			if (transition.name().equals("") ) {
				transformedAllTransitions.add( new ExecutableTransitionImpl(transition, PortType.internal, guards) );
				continue;
			}
			
			PortType transitionPortType = mapIdToPort.get(transition.name()).getType();
			
			ExecutableTransitionImpl t = new ExecutableTransitionImpl(transition, transitionPortType, guards) ;
			transformedAllTransitions.add( t);
			methodToTransition.put(t.method, t);
		}
		
		return transformedAllTransitions;
		
	}
	
	public void setComponentType(String type) {
		this.componentType = type;
	}

	public void setInitialState(String state) {
		this.currentState = state;
		states.add(state);
	}

    public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
    }

	public void addPort(String id, PortType type, Class<?> specificationType) {
		Port port = new PortImpl(id, type, specificationType);
		// PortImpl constructor already protects against null id.
		if (allPorts.containsKey(id))
			throw new BIPException("Port with id " + id + " has been already defined.");
		allPorts.put(id, port);
	}
	

	public void addPort(String id, PortType type, String specType) {
		Port port = new PortImpl(id, type, specType);
		// PortImpl constructor already protects against null id.
		if (allPorts.containsKey(id))
			throw new BIPException("Port with id " + id + " has been already defined.");
		allPorts.put(id, port);
	}
	
	public void addState(String state) {		
		states.add(state);		
	}
		
	public void addTransitionAndStates(String name, String source, 
			  				  		   String target, String guard, 
			  				  		   Method method) {			
	
			addTransitionAndStates(name, source, target, guard, method, ReflectionHelper.parseDataAnnotations(method));
					
	}
	
	public void addTransitionAndStates(String name, String source, 
									   String target, String guard, 
									   Method method, List<Data<?>> data) {			

		if (!allPorts.containsKey(name)) {
			if (name == null)
				throw new BIPException("Transition name can not be null, use empty empty string for internal transitions");
			if (!name.isEmpty())
				throw new BIPException("In component " + this.componentType + " transition " + name + " does not correspond to any port. Specify ports first and/or make sure the names match. ");
		}

		addState(source);
		addState(target);

		allTransitions.add( new TransitionImpl(name, source, target, guard, method, data) );
		
	}
	
	public void addTransition(String name, String source, 
	  		   				  String target, String guard, 
	  		   				  Method method) {			

		addTransition(name, source, target, guard, method, ReflectionHelper.parseDataAnnotations(method), ReflectionHelper.parseResourceAnnotations(method));
	}

	public void addTransition(String name, String source, 
			   				  String target, String guard, 
			   				  Method method, List<Data<?>> data, List<ResourceReqImpl> resourceReq) {			

		if (!allPorts.containsKey(name)) {
			if (name == null)
				throw new BIPException("Transition name can not be null, use empty empty string for internal transitions");
			if (!name.isEmpty())
				throw new BIPException("Transition " + name + " does not correspond to any port. Specify ports first and/or make sure the names match. ");
		}
		
		if (!states.contains(source))
			throw new BIPException("Transition " + name + " is specifying source state " + source + " that has not been explicitly stated before.");

		if (!states.contains(target))
			throw new BIPException("Transition " + name + " is specifying target state " + target + " that has not been explicitly stated before.");

		allTransitions.add( new TransitionImpl(name, source, target, guard, method, data));
	}	
	


	/**
	 * It add a guard based on the provided method with the guard name equal to method name.
	 * @param method
	 */
	public void addGuard(Method method) {
		addGuard(method.getName(), method);
	}

	
	/**
	 * It adds the guard by providing directly the method parameter. The method 
	 * parameter needs to be annotated to convey information about the data required by the method.
	 * 
	 * This function is left for the user convenience so the annotations can still be used to specify data.
	 *  
	 * @param name name of the guard
	 * @param method the method that is invoked to compute given guard.
	 */
	public void addGuard(String name, Method method) {		
		addGuard(name, method, ReflectionHelper.parseDataAnnotations(method));		
	}
	
	public void addGuard(String name, Method method, List<Data<?>> data) {		
		guards.put(name, new GuardImpl(name, method, data));		
	}

	public void addDataOut(Method method) {		
		
		DataOutImpl<?> data = ReflectionHelper.parseReturnDataAnnotation(method); 
		dataOut.add( data );
		dataOutName.put(data.name(), getMethodHandleFromMethod(method));
	}

	public void addDataOut(Method method, org.bip.annotations.Data annotation) {		
		
		DataOutImpl<?> data = ReflectionHelper.parseReturnDataAnnotation(method, annotation); 
		dataOut.add( data );
		dataOutName.put(data.name(), getMethodHandleFromMethod(method));
								
	}

	private MethodHandle getMethodHandleFromMethod(Method method) {
		MethodType methodType;
		MethodHandle methodHandle = null;
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
		try {
			methodHandle = lookup.findVirtual(component.getClass(), method.getName(), methodType);
		} catch (NoSuchMethodException e) {
			ExceptionHelper.printExceptionTrace(logger, e, "When building behaviour for component " + componentType);
		} catch (IllegalAccessException e) {
			ExceptionHelper.printExceptionTrace(logger, e, "When building behaviour for component " + componentType);
		}
		return methodHandle;
	}

	public void addResource(Method method, String label, ResourceType type) {
		// TODO R remove utility from here?
		ResourceReqImpl r = new ResourceReqImpl(label, type);
		resources.add(r);
		ArrayList<ResourceReqImpl> methodResources = new ArrayList<ResourceReqImpl>();
		if (methodReqResources.containsKey(method)) {
			methodResources = methodReqResources.get(method);
		}
		methodResources.add(r);
		methodReqResources.put(method, methodResources);
	}

	public void addResourceUtility(Method method, String utility) {
		methodUtility.put(method, utility);
	}

	public void addResourceRelease(Method method, String[] resourcesToRelease) {
		ArrayList<String> methodResources = new ArrayList<String>(Arrays.asList(resourcesToRelease));
		methodReleaseResources.put(method, methodResources);
	}
	
}
