package web;

import java.util.HashMap;

/**
 * This class is an implementation of the structure which contains all the waiting threads.
 * All the threads are identified by a uniqueID {@link String}.
 */
public class WaitTable {

	private HashMap<String, byte[]> hash;
	
	/**
	 * Constructor.
	 */
	public WaitTable() {
		this.hash = new HashMap<String, byte[]>();
	}

	/**
	 * @param key
	 * @return the answer
	 */
	public byte[] getAnsw(String key) {
		byte[] s = null;
		for (String k : hash.keySet()) {
			if (key.equals(k)) {
				s = hash.get(k);
			}
		}
		return s;
	}

	/**
	 * It inserts a uniqueID and an answer in the {@link HashMap}.
	 * @see java.util.HashMap#put(Object, Object)
	 * 
	 * @param uniqueID
	 * @param answ
	 */
	public void insert(String uniqueID, byte[] answ) {
		hash.put(uniqueID, answ);
	}
	
	/**
	 * This method wakes up the thread identified by uniqueID parameter by filling the answer.
	 * @see java.util.HashMap#replace(Object, Object)
	 * @see {@link java.lang.Object#notify()}
	 * 
	 * @param uniqueID
	 * @param answ
	 */
	public synchronized void fillAnswer(String uniqueID, byte[] answ){
		hash.replace(uniqueID, answ);
		this.notify();
	}

	
}
