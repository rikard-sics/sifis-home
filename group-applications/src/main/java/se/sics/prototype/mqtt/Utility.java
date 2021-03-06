/* Copyright (c) 2009, 2014 IBM Corp.
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

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;

/**
 * General purpose test utilities
 */
public class Utility {

	static final String className = Utility.class.getName();
	static final Logger log = Logger.getLogger(className);

	/**
	 * @return the current method name for the caller.
	 */
	public static String getMethodName() {
		StackTraceElement[] stack = (new Throwable()).getStackTrace();
		String methodName = stack[1].getMethodName();

		// Skip over synthetic accessor methods
		if (methodName.equals("access$0")) {
			methodName = stack[2].getMethodName();
		}

		return methodName;
	}

	/**
	 * @return 'true' if running on Windows
	 */
	public static boolean isWindows() {
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) {
			return true;
		}
		return false;
	}

	/**
	 * @param client
	 * @throws MqttException
	 */
	public static void disconnectAndCloseClient(MqttAsyncClient client) throws MqttException {
		if (client != null) {
			if (client.isConnected()) {
				IMqttToken token = client.disconnect(null, null);
				token.waitForCompletion();
			}
			client.close();
		}
	}

	/**
	 * @param client
	 * @throws MqttException
	 */
	public static void disconnectAndCloseClient(IMqttClient client) throws MqttException {
		if (client != null) {
			if (client.isConnected()) {
				client.disconnect(0);
			}
			client.close();
		}
	}

	/**
	 * Used to turn trace on dynamically in a test case, eg.
	 * java.util.logging.Logger logger =
	 * Logger.getLogger("org.eclipse.paho.client.mqttv3");
	 * logger.addHandler(Utility.getHandler()); logger.setLevel(Level.ALL);
	 */
	private static java.util.logging.Handler handler = null;

	public synchronized static java.util.logging.Handler getHandler() {
		try {
			if (handler == null) {
				handler = new FileHandler("framework.log", true);
				handler.setFormatter(new HumanFormatter());
			}
		} catch (IOException exception) {
			exception.printStackTrace();
		}
		return handler;
	}
}
