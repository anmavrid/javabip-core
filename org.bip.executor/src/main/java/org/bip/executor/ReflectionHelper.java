/*
 * Copyright (c) 2012 Crossing-Tech TM Switzerland. All right reserved.
 * Copyright (c) 2012, RiSD Laboratory, EPFL, Switzerland.
 *
 * Author: Simon Bliudze, Alina Zolotukhina, Anastasia Mavridou, and Radoslaw Szymanek
 * Date: 01/27/14
 */

package org.bip.executor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bip.api.Data;
import org.bip.api.DataOut.AccessType;
import org.bip.exceptions.BIPException;

class ReflectionHelper {

	// Separator between method name and parameter number to generate default data in name.
	private static final String SEPARATOR = ".";
		
	/**
	 * It reads data annotations for the method parameters. If one exists then it will use 
	 * it to define the parameter. If no data annotation is provided then a default name 
	 * constructed by name of the method + SEPARATOR + ${parameter.no} where ${parameter.no}
	 * denotes the number of the method parameter.
	 * 
	 * @param method
	 * @return
	 */
	public static List<Data<?>> parseDataAnnotations(Method method) {
		// deal with method parameters: there might be a dataIn
		ArrayList<Data<?>> dataIn = new ArrayList<Data<?>>();
		Class<?> paramTypes[] = method.getParameterTypes();
		if (paramTypes == null || paramTypes.length == 0) {
			return new ArrayList<Data<?>>();
		}
		
		Annotation[][] paramsAnnotations = method.getParameterAnnotations();
		for (int i = 0; i < paramsAnnotations.length; i++) {
			for (Annotation annotation : paramsAnnotations[i]) {
				if (annotation instanceof org.bip.annotations.Data) {
					org.bip.annotations.Data dataAnnotation = (org.bip.annotations.Data) annotation;
					Data<?> data = createData(dataAnnotation.name(), paramTypes[i]);
					dataIn.add(data);
					break;
				}
			}
			// TODO, TEST, write a test that will deal with the case when an annotation is missing. 
			// Maybe create a default name as specified in Data todo.
			if (dataIn.size() < i + 1) {
				// Data annotation is missing for i-th parameter, thus a default name is taken. 
				dataIn.add(createData(method.getName() + SEPARATOR + i, paramTypes[i]));
			}
		}
		return dataIn;
	}

	public static <T> Data<T> createData(String dataName, Class<T> type) {
		Data<T> toReturn = new DataImpl<T>(dataName, type);
		return toReturn;
	}
		
	public static DataOutImpl<?> parseReturnDataAnnotation(Method method) {

		Annotation[] annotations = method.getAnnotations();
		for (Annotation annotation : annotations) {
			if (annotation instanceof org.bip.annotations.Data) { // DATA OUT
				return parseReturnDataAnnotation(method, (org.bip.annotations.Data)annotation);
			}
		}

		throw new BIPException("Method " + method + " does not have BIPData annotation for Data Out");

	}
		
	public static DataOutImpl<?> parseReturnDataAnnotation(Method method, org.bip.annotations.Data dataAnnotation) {

		String name = dataAnnotation.name();
		AccessType type = dataAnnotation.accessTypePort();
		String[] ports = dataAnnotation.ports();
		return createData(name, method.getReturnType(), type, ports);

	}
	
	public static <T> DataOutImpl<T> createData(String dataName, Class<T> type, AccessType accessType, String[] ports) {
		return new DataOutImpl<T>(dataName, type, accessType, ports);
	}	
	
}