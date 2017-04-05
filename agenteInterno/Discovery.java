/**
 * 
 */
package agenteInterno;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This class creates a new {@link Receiver} thread for each device found in the JSON configuration file.
 * 
 * @see {@link Receiver}
 *
 */
public class Discovery {

	private final static String CONFIG = "./configuration.json";
	
	private static final String KEYSTORE_LOCATION = "./ClientKeyStore.jks";
	private static final String KEYSTORE_PASSWORD = "domclient";
	private static final String TRUSTSTORE_LOCATION = "./ClientTrustStore.jks";

	/**
	 * Constructor.
	 */
	public Discovery() {

	}

	/**
	 * Main.
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("Discovery Started");
		// read JSON file to find addresses, uris...
		JSONParser parser = new JSONParser();
		try {
			Object obj = parser.parse(new FileReader(CONFIG));
			JSONObject jsonObject = (JSONObject) obj;
			JSONObject proxy = (JSONObject) jsonObject.get("proxy");
			String address = (String) proxy.get("address");
			String port = (String) proxy.get("port");
			boolean secure = Boolean.parseBoolean((String) proxy.get("secure"));
			JSONArray devices = (JSONArray) jsonObject.get("devices");
			Iterator<JSONObject> iterator = devices.iterator();
			while (iterator.hasNext()) {
				JSONObject device = iterator.next();
				String devAddress = (String) device.get("address");
				String devPort = (String) device.get("port");
				JSONArray resources = (JSONArray) device.get("resources");
				Iterator<String> devIterator = resources.iterator();
				while (devIterator.hasNext()) {
					String res = devIterator.next();
					if (secure) {
						new Thread(new Receiver(devAddress, devPort, address, Integer.parseInt(port), "https://" + address + ":" + port + "/configuration/" + res)).start();
					} else {
						new Thread(new Receiver(devAddress, devPort, address, Integer.parseInt(port), "http://" + address + ":" + port + "/configuration/" + res)).start();
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return the keystore file location.
	 */
	public static String getKeystoreLocation() {
		return KEYSTORE_LOCATION;
	}

	/**
	 * @return the keystore password file location.
	 */
	public static String getKeystorePassword() {
		return KEYSTORE_PASSWORD;
	}

	/**
	 * @return the truststore file location.
	 */
	public static String getTruststoreLocation() {
		return TRUSTSTORE_LOCATION;
	}
}
