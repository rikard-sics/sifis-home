package se.sics.ace.as.logging;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

import org.glassfish.tyrus.client.ClientManager;

import com.google.gson.Gson;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * Handles connection establishment and sending of log messages to the DHT.
 *
 */
@ClientEndpoint
public class DhtLogger {

	private static CountDownLatch latch;
	private static ClientManager dhtClient = null;
	private static Session session = null;

	/**
	 * Sends a logging message to the DHT
	 * 
	 * @param message the message
	 * @param priority the priority
	 * @param severity the severity
	 * @param category the category
	 */
	static public void sendLog(String message, int priority, int severity, String category) {

		// Build the outgoing JSON payload for the DHT
		JsonOut outgoing = new JsonOut();

		Command commandVal = new Command();
		OutValue valueVal = new OutValue();
		Logs logsVal = new Logs();

		logsVal.setMessage(message);
		logsVal.setPriority(priority);
		logsVal.setSeverity(severity);
		logsVal.setCategory(category);

		valueVal.setLogs(logsVal);
		commandVal.setValue(valueVal);
		outgoing.setCommand(commandVal);

		Gson gsonOut = new Gson();
		String jsonOut = gsonOut.toJson(outgoing);

		// Now send the payload to the DHT
		try {
			session.getBasicRemote().sendText(jsonOut);
		} catch (IOException e) {
			System.err.println("Error: Sending logging payload to DHT failed");
			e.printStackTrace();
		}
	}

	// {"Command":{"value":{"logs":{"message":"error","priority":1,"severity":5,"category":"AS"}}}}
	static public void main(String[] args) {

		// Build outgoing JSON to DHT
		JsonOut outgoing = new JsonOut();

		Command commandVal = new Command();
		OutValue valueVal = new OutValue();
		Logs logsVal = new Logs();

		logsVal.setMessage("Error");
		logsVal.setPriority(10);
		logsVal.setSeverity(4);
		logsVal.setCategory("AS");

		valueVal.setLogs(logsVal);
		commandVal.setValue(valueVal);
		outgoing.setCommand(commandVal);

		Gson gsonOut = new Gson();
		String jsonOut = gsonOut.toJson(outgoing);
		System.out.println("AAAAAAAAAAA " + jsonOut);
	}

	/**
	 * Retrieve the client instance connected to the DHT.
	 * 
	 * @return the client
	 */
	public static ClientManager getClientInstance() {

		if (dhtClient == null || session == null) {
			establishConnection();
		}

		return dhtClient;
	}

	/**
	 * Retrieve the session instance associated with the connection to the DHT.
	 * 
	 * @return the session
	 */
	public static Session getSessionInstance() {

		if (dhtClient == null || session == null) {
			establishConnection();
		}

		return session;
	}

	/**
	 * Establish the connection to the DHT.
	 * 
	 * @return if the connection was successfully established
	 */
	public static boolean establishConnection() {

		System.out.println("Connecting to DHT for logging");

		latch = new CountDownLatch(1000);
		dhtClient = ClientManager.createClient();
		try {
			// wss://socketsbay.com/wss/v2/2/demo/
			URI uri = new URI("ws://localhost:3000/ws");
			session = dhtClient.connectToServer(DhtLogger.class, uri);
			latch.await();
		} catch (DeploymentException | URISyntaxException | InterruptedException | IOException e) {
			System.err.println("Error: Failed to connect to DHT for logging");
			e.printStackTrace();
			return false;
		}

		return true;
	}

}
