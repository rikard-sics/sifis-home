/*******************************************************************************
 * Copyright (c) 2009, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0
 * and the Eclipse Distribution License is available at 
 *   https://www.eclipse.org/org/documents/edl-v10.php
 *
 *******************************************************************************/

package se.sics.prototype.mqtt;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * Write console output to stdout (rather the default implementation which
 * writes to stderr)
 */
public class ConsoleHandler extends StreamHandler {

	/**
	 * Constructs a <code>ConsoleHandler</code> object.
	 */
	public ConsoleHandler() {
		super();
		setOutputStream(System.out);
	}

	/**
	 * Logs a record if necessary. A flush operation will be done.
	 * 
	 * @param record
	 */
	@Override
	public void publish(LogRecord record) {
		super.publish(record);
		super.flush();
	}

	/**
	 * 
	 */
	@Override
	public void close() {
		// Do nothing (the default implementation would close the stream!)
	}
}
