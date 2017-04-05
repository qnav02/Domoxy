package web;

/**
 * This class is used to permit a mutual exclusive access to the {@link Server} accept methods.
 */
public class Lock {
	
	private static boolean lock;

	/**
	 * Constructor.
	 */
	public Lock() {
		this.lock = true;
	}

	/**
	 * It sets the value of the lock according to the boolean parameter. 
	 * 
	 * @param bool
	 */
	public synchronized void changeLock(boolean bool){
		this.lock = bool;
		notify();
	}
	
	/**
	 * @return the current value of the lock.
	 */
	public synchronized boolean getLock(){
		return this.lock;
	}
	
}
