package se.sics.prototype.support;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.californium.elements.util.Bytes;

/**
 * Class to hold asymmetric keys for the group members
 * to use in the OSCORE group. 
 *
 */
public class KeyStorage {

	//Map holding private and public keys indexed by the member name
	public static Map<String, String> publicPrivateKeys;
	static {
		publicPrivateKeys = new HashMap<>();
		publicPrivateKeys.put("Client1", "pQMnAQEgBiFYIFylzMSj2HrwPgoIdw8GM7fs50NlUyhfLkY9e+vznQrtI1ggjN6Odv9SuxW0R0cOP6sC7jto7zaRtaZ/bw6nQ4iAPpk=");
		publicPrivateKeys.put("Client2", "pQMnAQEgBiFYIEtmju4zHfVSf9u/369Xoyoin37x4qj5NIWwkf7j/P6dI1ggHoEddHIcdDhlSRuEh5hyO9Z/16Al9VWp44RBej6g+WE=");
		publicPrivateKeys.put("Server1", "pQMnAQEgBiFYIBxUZrhV2dW6+wzI5/T1sA4zCbQ8Fa3KHe2qWFJlOa5zI1ggE/m/9QJKcoe0C+TTRJmXTy0RmJPK9INnNtrC0tNpl8I=");
		publicPrivateKeys.put("Server2", "pQMnAQEgBiFYINIKTSYnHmt+1OrcTYx3eTXIpLFvmxxP0GK+NGKt+y0cI1ggp9URBpjMBdSxn9eOWEsm/AZMLIALOTTKrK84O9OPoGs=");
		publicPrivateKeys.put("Server3", "pQMnAQEgBiFYIMX9pQTWGg8SYI2X6pkdP+b7FEie0xbisFjqRrndU3ZQI1ggUsdP3Hk0lBkQQeU552ErmOVnAaeTappwRc/kE4j2ThQ=");
		publicPrivateKeys.put("Server4", "pQMnAQEgBiFYIJxqx9fM0HTCPcg6LlNp1IdshIARMoYDSnJLeCAH1R2lI1gg6MhAGhahHXRZgdS+ZownFTNUhaT3nicve8A49V1wJbk=");
		publicPrivateKeys.put("Server5", "pQMnAQEgBiFYINvuYCYrTiGdSQ5gvC9bYL1ZAbIeEhksNmjvS0nMi8bJI1gg18vE0tfrOqA7j0ePgleFJLLxbi9Itv1JECY0dBS5qg8=");
		publicPrivateKeys.put("Server6", "pQMnAQEgBiFYIIfqvIO3xV/kS7qxVo7qsRtwYuL92ydmQDLut5DVwa4gI1ggzZVPliiBLlr/QL7bQZixMu1kPWpUA75P18WzR5l5+Gs=");
	}

	//Map holding public keys indexed by the member name
	public static Map<String, String> publicKeys;
	static {
		publicKeys = new HashMap<>();
		publicKeys.put("Client1", "pAMnAQEgBiFYIFylzMSj2HrwPgoIdw8GM7fs50NlUyhfLkY9e+vznQrt");
		publicKeys.put("Client2", "pAMnAQEgBiFYIEtmju4zHfVSf9u/369Xoyoin37x4qj5NIWwkf7j/P6d");
		publicKeys.put("Server1", "pAMnAQEgBiFYIBxUZrhV2dW6+wzI5/T1sA4zCbQ8Fa3KHe2qWFJlOa5z");
		publicKeys.put("Server2", "pAMnAQEgBiFYINIKTSYnHmt+1OrcTYx3eTXIpLFvmxxP0GK+NGKt+y0c");
		publicKeys.put("Server3", "pAMnAQEgBiFYIMX9pQTWGg8SYI2X6pkdP+b7FEie0xbisFjqRrndU3ZQ");
		publicKeys.put("Server4", "pAMnAQEgBiFYIJxqx9fM0HTCPcg6LlNp1IdshIARMoYDSnJLeCAH1R2l");
		publicKeys.put("Server5", "pAMnAQEgBiFYINvuYCYrTiGdSQ5gvC9bYL1ZAbIeEhksNmjvS0nMi8bJ");
		publicKeys.put("Server6", "pAMnAQEgBiFYIIfqvIO3xV/kS7qxVo7qsRtwYuL92ydmQDLut5DVwa4g");
	}

	//Map holding public keys for clients, indexed by Sender ID
	//Client 1 is 0x11 and Client 2 0x22
	//(Cannot use a byte array as HashMap key directly)
	public static Map<Bytes, String> clientKeys;
	static {
		clientKeys = new HashMap<>();
		clientKeys.put(new Bytes(new byte[] { 0x11 }), "pAMnAQEgBiFYIFylzMSj2HrwPgoIdw8GM7fs50NlUyhfLkY9e+vznQrt");
		clientKeys.put(new Bytes(new byte[] { 0x22 }), "pAMnAQEgBiFYIEtmju4zHfVSf9u/369Xoyoin37x4qj5NIWwkf7j/P6d");
	}
	
	// Hold specific Sender IDs for the 2 clients
	public static Map<String, Bytes> clientIds;
	static {
		clientIds = new HashMap<>();
		clientIds.put("Client1", new Bytes(new byte[] { 0x11 }));
		clientIds.put("Client2", new Bytes(new byte[] { 0x22 }));
	}
	
	//Map holding Sender IDs for clients, indexed by public keys
	//Client 1 is 0x11 and Client 2 0x22
	//(Cannot use a byte array as HashMap key directly)
	public static Map<String, Bytes> clientSenderIDs;
	static {
		clientSenderIDs = new HashMap<>();
		clientSenderIDs.put("pAMnAQEgBiFYIFylzMSj2HrwPgoIdw8GM7fs50NlUyhfLkY9e+vznQrt", new Bytes(new byte[] { 0x11 }));
		clientSenderIDs.put("pAMnAQEgBiFYIEtmju4zHfVSf9u/369Xoyoin37x4qj5NIWwkf7j/P6d", new Bytes(new byte[] { 0x22 }));
	}

	//Map holding OSCORE keys (master secret) to use by the group members towards the AS
	public static Map<String, byte[]> memberAsKeys;
	static {
		memberAsKeys = new HashMap<>();
		memberAsKeys.put("Client1", new byte[] { (byte) 0xCC, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11 });
		memberAsKeys.put("Client2", new byte[] { (byte) 0xCC, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22 });
		memberAsKeys.put("Server1", new byte[] { 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11 });
		memberAsKeys.put("Server2", new byte[] { 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22 });
		memberAsKeys.put("Server3", new byte[] { 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33 });
		memberAsKeys.put("Server4", new byte[] { 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44 });
		memberAsKeys.put("Server5", new byte[] { 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55 });
		memberAsKeys.put("Server6", new byte[] { 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66 });
	}

    // Map holding ACE Sender ID indexed by the member name
    public static Map<String, byte[]> aceSenderIds;
    static {
        aceSenderIds = new HashMap<>();
        aceSenderIds.put("AS", new byte[] { (byte) 0xA0 });
        aceSenderIds.put("Client1", new byte[] { (byte) 0xA3 });
        aceSenderIds.put("Client2", new byte[] { (byte) 0xA4 });
        aceSenderIds.put("Server1", new byte[] { (byte) 0xA5 });
        aceSenderIds.put("Server2", new byte[] { (byte) 0xA6 });
        aceSenderIds.put("Server3", new byte[] { (byte) 0xA7 });
        aceSenderIds.put("Server4", new byte[] { (byte) 0xA8 });
        aceSenderIds.put("Server5", new byte[] { (byte) 0xA9 });
        aceSenderIds.put("Server6", new byte[] { (byte) 0xAA });
    }
    
//	//Map holding CCS to use by the group members
//	public static Map<String, byte[]> memberCcs;
//	static {
//		memberCcs = new HashMap<>();
//		memberCcs.put("Client1", hexStringToByteArray("A202616108A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Client2", hexStringToByteArray("A202616208A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server1", hexStringToByteArray("A202616308A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server2", hexStringToByteArray("A202616408A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server3", hexStringToByteArray("A202616508A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server4", hexStringToByteArray("A202616608A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server5", hexStringToByteArray("A202616708A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//		memberCcs.put("Server6", hexStringToByteArray("A202616808A101A4010103272006215820069E912B83963ACC5941B63546867DEC106E5B9051F2EE14F3BC5CC961ACD43A"));
//	}
//	
//	//Map holding Private Keys to use by the group members
//	public static Map<String, byte[]> memberPrivateKeys;
//	static {
//		memberPrivateKeys = new HashMap<>();
//		memberPrivateKeys.put("Client1", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Client2", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server1", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server2", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server3", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server4", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server5", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//		memberPrivateKeys.put("Server6", hexStringToByteArray("64714D41A240B61D8D823502717AB088C9F4AF6FC9844553E4AD4C42CC735239"));
//	}
    
	//Map holding CCS to use by the group members
	public static Map<String, byte[]> memberCcs;
	static {
		memberCcs = new HashMap<>();
		memberCcs.put("Client1", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026661656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Client2", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026662656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server1", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026663656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server2", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026664656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server3", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026665656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server4", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026666656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server5", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026667656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
		memberCcs.put("Server6", hexStringToByteArray("A501781A636F6170733A2F2F7365727665722E6578616D706C652E636F6D026668656E64657203781A636F6170733A2F2F636C69656E742E6578616D706C652E6F7267041A70004B4F08A101A401010327200621582077EC358C1D344E41EE0E87B8383D23A2099ACD39BDF989CE45B52E887463389B"));
	}
	
	//Map holding Private Keys to use by the group members
	public static Map<String, byte[]> memberPrivateKeys;
	static {
		memberPrivateKeys = new HashMap<>();
		memberPrivateKeys.put("Client1", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Client2", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server1", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server2", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server3", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server4", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server5", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
		memberPrivateKeys.put("Server6", hexStringToByteArray("857EB61D3F6D70A278A36740D132C099F62880ED497E27BDFD4685FA1A304F26"));
	}
	
 	/**
 	 * @param str the hex string
 	 * @return the byte array
 	 * @str the hexadecimal string to be converted into a byte array
 	 * 
 	 *      Return the byte array representation of the original string
 	 */
 	public static byte[] hexStringToByteArray(final String str) {
 		int len = str.length();
 		byte[] data = new byte[len / 2];

 		// Big-endian
 		for (int i = 0; i < len; i += 2) {
 			data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
 			data[i / 2] = (byte) (data[i / 2] & 0xFF);
 		}

 		// Little-endian
 		/*
 		 * for (int i = 0; i < len; i += 2) { data[i / 2] = (byte)
 		 * ((Character.digit(str.charAt(len - 2 - i), 16) << 4) +
 		 * Character.digit(str.charAt(len - 1 - i), 16)); data[i / 2] = (byte)
 		 * (data[i / 2] & 0xFF); }
 		 */

 		return data;

 	}
}
