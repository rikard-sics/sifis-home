package se.sics.prototype.json;

import com.google.gson.Gson;
import se.sics.prototype.json.incoming.JsonIn;
import se.sics.prototype.json.outgoing.JsonOut;
import se.sics.prototype.json.outgoing.OutValue;
import se.sics.prototype.json.outgoing.RequestPubMessage;

public class JsonParseTest {

	/*
	 * {"Volatile":{"value":{"message":"hi","topic":"command_dev1"}}}
	 */
	// https://github.com/google/gson/blob/master/UserGuide.md#maps-examples
	// https://stackoverflow.com/a/19177892
	// https://github.com/google/gson/blob/master/UserGuide.md#object-examples

	public static void main(String[] args) throws Exception {
		// Deserialize incoming

		String test1 = "{\"Volatile\":{\"value\":{\"message\":\"hi\",\"topic\":\"command_dev1\"}}}";
		// String test2 =
		// "{\"value\":{\"message\":\"hi\",\"topic\":\"command_dev1\"}}";
		// String test3 = "{\"key\": \"value\"}";

		Gson gson = new Gson();
		JsonIn parsed = gson.fromJson(test1, JsonIn.class);

		System.out.println(parsed.getVolatile().getValue().getTopic());
		System.out.println(parsed.getVolatile().getValue().getMessage());

		// Serialize outgoing

		// {"RequestPubMessage":{"value":{"message":"hi","topic":"output_dev1"}}}

		JsonOut outgoing = new JsonOut();
		RequestPubMessage pubMsg = new RequestPubMessage();
		OutValue outVal = new OutValue();
		outVal.setTopic("output_dev1");
		outVal.setMessage("test123");
		pubMsg.setValue(outVal);
		outgoing.setRequestPubMessage(pubMsg);

		Gson gsonOut = new Gson();
		String jsonOut = gsonOut.toJson(outgoing);

		System.out.println("Outgoing JSON: " + jsonOut);
	}
}
