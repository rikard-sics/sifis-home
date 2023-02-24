/*******************************************************************************
 * Copyright (c) 2022 RISE and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.

 * Contributors: 
 *    Tobias Andersson (RISE SICS)
 *    Marco Tiloca (RISE)
 *    Rikard Höglund (RISE)
 *    
 ******************************************************************************/
package se.sics.edhocapps;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.io.File;
import java.util.Arrays;

/**
 * 
 * HelloWorldClient to display the basic OSCORE mechanics
 *
 */
public class Path {

	
	 
	public static void main(String[] args) {
		   String classpath = System.getProperty("java.class.path");
   String[] classPathValues = classpath.split(File.pathSeparator);
   System.out.println(Arrays.toString(classPathValues));
	}

	
}
