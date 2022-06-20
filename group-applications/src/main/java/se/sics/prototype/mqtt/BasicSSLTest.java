/* Copyright (c) 2009, 2019 IBM Corp.
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

import java.io.File;
import java.net.URI;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttTopic;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import se.sics.prototype.mqtt.MqttV5Receiver.ReceivedMessage;

/**
 * This test aims to run some basic SSL functionality tests of the MQTT client
 */

public class BasicSSLTest {

	static final Class<?> cclass = BasicSSLTest.class;
	private static final String className = cclass.getName();
	private static final Logger log = Logger.getLogger(className);

	private static URI serverURI;
	private static String serverHost;
	private static File keystorePath;
	private static int messageSize = 100000;
	private static String topicPrefix;

	public static void setUpBeforeClass() throws Exception {

		try {
			String methodName = Utility.getMethodName();
			LoggingUtilities.banner(log, cclass, methodName);

			serverURI = TestProperties.getServerURI();
			serverHost = serverURI.getHost();
			topicPrefix = "BasicSSLTest-" + UUID.randomUUID().toString() + "-";

		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			throw exception;
		}
	}

	/**
	 * An ssl connection with server cert authentication, simple pub/sub
	 * 
	 * @throws Exception
	 */
	public static void testSSL() throws Exception {
		URI serverURI = new URI("ssl://" + serverHost + ":" + TestProperties.getServerSSLPort());
		// No-TLS: URI serverURI = new
		// URI(TestProperties.getServerURI().toString());
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, cclass, methodName);
		log.entering(className, methodName);

		IMqttClient mqttClient = null;
		try {
			MqttV5Receiver mqttV5Receiver = new MqttV5Receiver(methodName, LoggingUtilities.getPrintStream());
			mqttClient = new MqttClient(serverURI.toString(), methodName);
			log.info("Assigning callback");
			mqttClient.setCallback(mqttV5Receiver);

			log.info("Setting SSL properties...");
			System.setProperty("javax.net.ssl.keyStore", TestProperties.getClientKeyStore());
			System.setProperty("javax.net.ssl.keyStorePassword", TestProperties.getClientKeyStorePassword());
			System.setProperty("javax.net.ssl.trustStore", TestProperties.getClientTrustStore());
			log.info("Connecting...(serverURI:" + serverURI + ", ClientId:" + methodName);

			// Set username/pw
			/*
			 * MqttConnectionOptions mqttConnectOptions = new
			 * MqttConnectionOptions(); mqttConnectOptions.setUserName("test");
			 * mqttConnectOptions.setPassword("test".getBytes());
			 * mqttClient.connect(mqttConnectOptions);
			 */

			mqttClient.connect();

			// String[] topicNames = new String[] { topicPrefix + methodName +
			// "/Topic" };
			String[] topicNames = new String[] { "sifis-test-123" };
			// String[] topicNames2 = new String[] { "sifis-test-new" };
			int[] topicQos = { 2 };
			log.info("Subscribing to..." + topicNames[0]);
			mqttClient.subscribe(topicNames, topicQos);
			// mqttClient.subscribe(topicNames2, topicQos);


			while (true) {
				ReceivedMessage msg = mqttV5Receiver.receiveNext(0);
				if (msg != null) {
					System.out.println("Received msg: " + msg.topic.toString() + ": " + msg.message.toString());
				}
			}

			// byte[] payload = ("Message payload " + "BasicSSLTest" + "." +
			// methodName).getBytes();
			// MqttTopic mqttTopic = mqttClient.getTopic(topicNames[0]);
			// log.info("Publishing to..." + topicNames[0]);
			// mqttTopic.publish(payload, 2, false);

			// boolean ok = mqttV5Receiver.validateReceipt(topicNames[0], 2,
			// payload);
			// if (!ok) {
			// Assert.fail("Receive failed");
			// }
		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			Assert.fail("Failed to instantiate:" + methodName + " exception=" + exception);
		} finally {
			try {
				if ((mqttClient != null) && mqttClient.isConnected()) {
					log.info("Disconnecting...");
					mqttClient.disconnect();
				}
				if (mqttClient != null) {
					log.info("Close...");
					mqttClient.close();
				}
			} catch (Exception exception) {
				log.log(Level.SEVERE, "caught exception:", exception);
			}
		}

		log.exiting(className, methodName);
	}

	/**
	 * An ssl connection with server cert authentication, small workload with
	 * multiple clients
	 * 
	 * @throws Exception
	 */
	@Test(timeout = 60000)
	public void testSSLWorkload() throws Exception {
		URI serverURI = new URI("ssl://" + serverHost + ":" + TestProperties.getServerSSLPort());
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, cclass, methodName);
		log.entering(className, methodName);

		IMqttClient[] mqttPublisher = new IMqttClient[4];
		IMqttClient[] mqttSubscriber = new IMqttClient[20];
		try {
			String[] topicNames = new String[] { topicPrefix + methodName + "/Topic" };
			int[] topicQos = { 0 };

			MqttTopic[] mqttTopic = new MqttTopic[mqttPublisher.length];
			for (int i = 0; i < mqttPublisher.length; i++) {
				mqttPublisher[i] = new MqttClient(serverURI.toString(), "MultiPub" + i);
				log.info("Setting SSL properties...ClientId: MultiPub" + i);
				System.setProperty("javax.net.ssl.keyStore", TestProperties.getClientKeyStore());
				System.setProperty("javax.net.ssl.keyStorePassword", TestProperties.getClientKeyStorePassword());
				System.setProperty("javax.net.ssl.trustStore", TestProperties.getClientKeyStore());
				System.setProperty("javax.net.ssl.trustStorePassword", TestProperties.getClientKeyStorePassword());
				log.info("Connecting...(serverURI:" + serverURI + ", ClientId: MultiPub" + i);
				mqttPublisher[i].connect();
				mqttTopic[i] = mqttPublisher[i].getTopic(topicNames[0]);

			} // for...

			MqttV5Receiver[] mqttV5Receiver = new MqttV5Receiver[mqttSubscriber.length];
			for (int i = 0; i < mqttSubscriber.length; i++) {
				mqttSubscriber[i] = new MqttClient(serverURI.toString(), "MultiSubscriber" + i);
				mqttV5Receiver[i] = new MqttV5Receiver(mqttSubscriber[i].getClientId(),
						LoggingUtilities.getPrintStream());
				log.info("Assigning callback...");
				mqttSubscriber[i].setCallback(mqttV5Receiver[i]);

				log.info("Setting SSL properties...ClientId: MultiSubscriber" + i);
				System.setProperty("javax.net.ssl.keyStore", TestProperties.getClientKeyStore());
				System.setProperty("javax.net.ssl.keyStorePassword", TestProperties.getClientKeyStorePassword());
				System.setProperty("javax.net.ssl.trustStore", TestProperties.getClientKeyStore());
				System.setProperty("javax.net.ssl.trustStorePassword", TestProperties.getClientKeyStorePassword());
				log.info("Connecting...(serverURI:" + serverURI + ", ClientId: MultiSubscriber" + i);
				mqttSubscriber[i].connect();
				log.info("Subcribing to..." + topicNames[0]);
				mqttSubscriber[i].subscribe(topicNames, topicQos);
			} // for...

			for (int iMessage = 0; iMessage < 10; iMessage++) {
				byte[] payload = ("Message " + iMessage).getBytes();
				for (int i = 0; i < mqttPublisher.length; i++) {
					log.info("Publishing to..." + topicNames[0]);
					mqttTopic[i].publish(payload, 0, false);
				}

				for (int i = 0; i < mqttSubscriber.length; i++) {
					for (int ii = 0; ii < mqttPublisher.length; ii++) {
						boolean ok = mqttV5Receiver[i].validateReceipt(topicNames[0], 0, payload);
						if (!ok) {
							Assert.fail("Receive failed");
						}
					} // for publishers...
				} // for subscribers...
			} // for messages...
		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			throw exception;
		} finally {
			try {
				for (int i = 0; i < mqttPublisher.length; i++) {
					log.info("Disconnecting...MultiPub" + i);
					mqttPublisher[i].disconnect();
					log.info("Close...");
					mqttPublisher[i].close();
				}
				for (int i = 0; i < mqttSubscriber.length; i++) {
					log.info("Disconnecting...MultiSubscriber" + i);
					mqttSubscriber[i].disconnect();
					log.info("Close...");
					mqttSubscriber[i].close();
				}

			} catch (Exception exception) {
				log.log(Level.SEVERE, "caught exception:", exception);
			}
		}

		log.exiting(className, methodName);
	}

	/**
	 * An ssl connection with server cert authentication, simple pub/sub of a
	 * large message 'messageSize' defined at start of test, change it to meet
	 * your requirements
	 * 
	 * @throws Exception
	 */
	@Test(timeout = 10000)
	public void testSSLLargeMessage() throws Exception {
		URI serverURI = new URI("ssl://" + serverHost + ":" + TestProperties.getServerSSLPort());
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, cclass, methodName);
		log.entering(className, methodName);

		IMqttClient mqttClient = null;
		try {
			mqttClient = new MqttClient(serverURI.toString(), methodName);
			MqttV5Receiver mqttV5Receiver = new MqttV5Receiver(mqttClient.getClientId(),
					LoggingUtilities.getPrintStream());
			log.info("Assigning callback...");
			mqttClient.setCallback(mqttV5Receiver);

			log.info("Setting SSL properties...");
			System.setProperty("javax.net.ssl.keyStore", TestProperties.getClientKeyStore());
			System.setProperty("javax.net.ssl.keyStorePassword", TestProperties.getClientKeyStorePassword());
			System.setProperty("javax.net.ssl.trustStore", TestProperties.getClientKeyStore());
			System.setProperty("javax.net.ssl.trustStorePassword", TestProperties.getClientKeyStorePassword());
			log.info("Connecting...(serverURI:" + serverURI + ", ClientId:" + methodName);
			mqttClient.connect();

			String[] topicNames = new String[] { topicPrefix + methodName + "/Topic" };
			int[] topicQos = { 2 };
			log.info("Subscribing to..." + topicNames[0]);
			mqttClient.subscribe(topicNames, topicQos);

			// Create message of size 'messageSize'
			byte[] message = new byte[messageSize];
			java.util.Arrays.fill(message, (byte) 's');

			MqttTopic mqttTopic = mqttClient.getTopic(topicNames[0]);
			log.info("Publishing to..." + topicNames[0]);
			mqttTopic.publish(message, 2, false);
			boolean ok = mqttV5Receiver.validateReceipt(topicNames[0], 2, message);
			if (!ok) {
				Assert.fail("Receive failed");
			}
		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			Assert.fail("Failed:" + methodName + " exception=" + exception);
		} finally {
			try {
				if ((mqttClient != null) && mqttClient.isConnected()) {
					log.info("Disconnecting...");
					mqttClient.disconnect();
				}
				if (mqttClient != null) {
					log.info("Close...");
					mqttClient.close();
				}
			} catch (Exception exception) {
				log.log(Level.SEVERE, "caught exception:", exception);
				throw exception;
			}
		}
		log.exiting(className, methodName);
	}

	/**
	 * A non ssl connection to an ssl channel
	 * 
	 * @throws Exception
	 */
	@Test(timeout = 10000)
	public void testNonSSLtoSSLChannel() throws Exception {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, cclass, methodName);
		log.entering(className, methodName);

		IMqttClient mqttClient = null;
		try {
			mqttClient = new MqttClient("tcp://" + serverHost + ":" + TestProperties.getServerSSLPort(), methodName);
			MqttV5Receiver mqttV5Receiver = new MqttV5Receiver(mqttClient.getClientId(),
					LoggingUtilities.getPrintStream());
			mqttClient.setCallback(mqttV5Receiver);
			log.info("Assigning callback...");
			try {
				log.info("Connecting...Expect to fail");
				mqttClient.connect();
				Assert.fail("Non SSL Connection was allowed to SSL channel with Client Authentication");
			} catch (Exception e) {
				// Expected exception
			}

		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			Assert.fail("Failed:" + methodName + " exception=" + exception);
		} finally {
			try {
				if ((mqttClient != null) && mqttClient.isConnected()) {
					log.info("Disconnecting...");
					mqttClient.disconnect();
				}
				if (mqttClient != null) {
					log.info("Close...");
					mqttClient.close();
				}

			} catch (Exception exception) {
				log.log(Level.SEVERE, "caught exception:", exception);
				throw exception;
			}
		}
		log.exiting(className, methodName);
	}

	/**
	 * Try ssl connection to channel without ssl
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSSLtoNonSSLChannel() throws Exception {
		String methodName = Utility.getMethodName();
		LoggingUtilities.banner(log, cclass, methodName);
		log.entering(className, methodName);

		IMqttClient mqttClient = null;
		try {
			mqttClient = new MqttClient("ssl://" + serverHost + ":18883", methodName);
			MqttV5Receiver mqttV5Receiver = new MqttV5Receiver(mqttClient.getClientId(),
					LoggingUtilities.getPrintStream());
			log.info("Assigning callback...");
			mqttClient.setCallback(mqttV5Receiver);

			log.info("Setting SSL properties...");
			System.setProperty("javax.net.ssl.keyStore", TestProperties.getClientKeyStore());
			System.setProperty("javax.net.ssl.keyStorePassword", TestProperties.getClientKeyStorePassword());
			System.setProperty("javax.net.ssl.trustStore", TestProperties.getClientKeyStore());
			System.setProperty("javax.net.ssl.trustStorePassword", TestProperties.getClientKeyStorePassword());
			try {
				log.info("Connecting...Expect to fail");
				mqttClient.connect();
				Assert.fail("SSL Connection was allowed to a channel without SSL");
			} catch (Exception e) {
				// Expected exception
			}

		} catch (Exception exception) {
			log.log(Level.SEVERE, "caught exception:", exception);
			Assert.fail("Failed:" + methodName + " exception=" + exception);
		} finally {
			try {
				if ((mqttClient != null) && mqttClient.isConnected()) {
					log.info("Disconnecting...");
					mqttClient.disconnect();
				}
				if (mqttClient != null) {
					log.info("Close...");
					mqttClient.close();
				}

			} catch (Exception exception) {
				log.log(Level.SEVERE, "caught exception:", exception);
				throw exception;
			}
		}
		log.exiting(className, methodName);
	}
}
