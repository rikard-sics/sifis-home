package se.sics.prototype.ws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;

// https://raw.githubusercontent.com/javiergs/Medium/main/Websockets/ChatClientEndpoint.java
// https://socketsbay.com/test-websockets

@ClientEndpoint
public class ChatClientEndpoint {

	private static CountDownLatch latch;

	@OnOpen
	public void onOpen(Session session) {
		System.out.println("--- Connected " + session.getId());
		try {
			session.getBasicRemote().sendText("start");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@OnMessage
	public String onMessage(String message, Session session) {
		BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
		try {
			System.out.println("--- Received " + message);
			String userInput = bufferRead.readLine();
			return userInput;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		System.out.println("Session " + session.getId() + " closed because " + closeReason);
		latch.countDown();
	}

	public static void main(String[] args) throws DeploymentException, IOException {
		latch = new CountDownLatch(1);
		ClientManager client = ClientManager.createClient();
		try {
			URI uri = new URI("wss://socketsbay.com/wss/v2/2/demo/");
			client.connectToServer(ChatClientEndpoint.class, uri);
			latch.await();
		} catch (DeploymentException | URISyntaxException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
