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

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A log formatter which formats a reduced selection of the LogRecord fields.
 */
public class HumanFormatter extends DetailFormatter {

	/**
	 * @param record
	 * @param sb
	 */
	@Override
	public void addClassName(LogRecord record, StringBuffer sb) {
		// do nothing
	}

	/**
	 * @param sb
	 * @param type
	 */
	@Override
	public void addTypeName(LogRecord record, StringBuffer sb, String type) {

		int intLevel = record.getLevel().intValue();
		int intFINER = Level.FINER.intValue();

		if (intLevel <= intFINER) {
			sb.append(type);
			sb.append(" ");
		}
	}

	/**
	 * @param record
	 * @param sb
	 */
	@Override
	public void addMethodName(LogRecord record, StringBuffer sb) {

		int intLevel = record.getLevel().intValue();
		int intFINER = Level.FINER.intValue();

		if (intLevel <= intFINER) {
			sb.append(formatJavaName(record.getSourceMethodName(), 30));
			sb.append(" ");
		}
	}
}
