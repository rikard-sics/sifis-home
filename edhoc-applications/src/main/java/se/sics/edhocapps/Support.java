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
 *
 * Contributors: 
 *    Marco Tiloca (RISE)
 *    Rikard Höglund (RISE)
 *    
 ******************************************************************************/
package se.sics.edhocapps;

import java.io.IOException;

/**
 * Supporting methods for the EDHOC applications.
 *
 */
public class Support {

	/**
	 * Simple method for "press enter to continue" functionality
	 */
	static void printPause(String message) {

		System.out.println("===");
		System.out.println(message);
		System.out.println("Press ENTER to continue");
		System.out.println("===");
		try {
			@SuppressWarnings("unused")
			int read = System.in.read(new byte[2]);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void printIfNotNull(String str) {
		if (str != null) {
			System.out.println(str);
		}
	}

	/**
	 * Print help for EDHOC clients
	 */
	public static void printHelp() {
		System.out.println("Usage: [ -server URI ] [ -dht ]");

		System.out.println("Options:");

		System.out.print("-server");
		System.out.println("\t EDHOC Server base URI");

		System.out.print("-dht");
		System.out.println("\t Use DHT");

		System.out.print("-help");
		System.out.println("\t Print help");
	}

}
