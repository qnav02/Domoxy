package web;

import java.net.Socket;
import java.util.HashMap;

/**
 *	This class collects all the opened connections between the internal agent and the external proxy.
 *	Each connection is stored in a {@link HashMap} and identified by the {@link URI} provided by the internal agent.
 */
public class ConnectionTable {

	private HashMap<String, Socket> hash;

	public ConnectionTable() {
		hash = new HashMap<String, Socket>();
	}

	/**
	 * It inserts a new key-value pair in the HashMap.
	 * 
	 * @param uri	the {@link HashMap} key
	 * @param sock	a {@link Socket} object
	 */
	public synchronized void insert(String uri, Socket sock) {
		hash.put(uri, sock);
		System.out.println("inserted " + uri);
	}

	/**
	 * It looks up for a matching {@link URI} in the table. 
	 * 
	 * @param uri a String representing an {@link URI}
	 * @return null if no matching element is found, the {@link Socket} value otherwise
	 */
	public synchronized Socket getSock(String uri) {
		Socket s = null;
		String keyTmp = "";
		System.out.println("getSock");
		for (String key : hash.keySet()) {
			System.out.println("k " + key);
			if (uri.startsWith(key) && key.length()>keyTmp.length()) {
				keyTmp = key;
				System.out.println("uri "+uri);
				System.out.println("keyTmp "+keyTmp);
				s = hash.get(keyTmp);
			}
		}
		return s;
	}

	/**
	 * It purges the {@link HashMap}. The map will be empty after the execution of this method.
	 */
	public synchronized void cleanUp() {
		hash.clear();
	}

	/**
	 * It removes the table entry which corresponds to the given uri.
	 * 
	 * @param uri a String representing an {@link URI}
	 */
	public synchronized void remove(String uri) {
		hash.remove(uri);
		System.out.println("removed " + uri);
	}
	
	public synchronized String toString(){
		return "HASH: " + hash.toString() + "\n";		
	}

}
