package se.sics.prototype.mqtt;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;

public class MqttClientTest {

	public static void main(String[] args) throws MqttException {
		MqttAsyncClient testClient = new MqttAsyncClient("tcp://" + "localhost" + ":1883", "test");
		System.out.println("HELLO");
	}

}
