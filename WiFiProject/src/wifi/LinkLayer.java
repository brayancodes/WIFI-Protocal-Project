package wifi;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface 
{
	public static final int SUCCESS = 1;
    public static final int UNSPECIFIED_ERROR = 2;
    public static final int RF_INIT_FAILED = 3;
    public static final int TX_DELIVERED = 4;
    public static final int TX_FAILED = 5;
    public static final int BAD_ADDRESS = 7;
    public static final int ILLEGAL_ARGUMENT = 9;
    public static final int INSUFFICIENT_BUFFER_SPACE = 10;
	
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	
	public boolean maxCollisionWindow;
	private int debug;
	private int statusCode;
	private boolean displayed = false;
	public static long globalOffset;
	
	public reader read;
	HashMap<Short, Short> destSeqNums;

    // create object of ArrayBlockingQueue 
    ArrayBlockingQueue<Packet> packetHolder = new ArrayBlockingQueue<Packet>(1000);   
    ArrayBlockingQueue<Packet> packetHolderIn = new ArrayBlockingQueue<Packet>(1000);
    ArrayBlockingQueue<Packet> ackHolder = new ArrayBlockingQueue<Packet>(1000);
    ArrayBlockingQueue<Packet> limiter = new ArrayBlockingQueue<Packet>(1000);
    
	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		globalOffset = 0;
		this.ourMAC = ourMAC;
		this.output = output;
		output.println("LinkLayer: Constructor ran.");
		this.destSeqNums = new HashMap<Short, Short>();
		theRF = new RF(null, null);
		sender send = new sender(theRF, packetHolder, ackHolder, limiter, maxCollisionWindow, debug, output, ourMAC);
		(new Thread(send)).start();
		read = new reader(theRF, packetHolderIn, ackHolder, ourMAC, debug, output);
		(new Thread(read)).start();
	}
	
	
	  public short calcNextSeqNum(short i) {
	        short seqNum;
	        if (this.destSeqNums.containsKey(i)) {
	            seqNum = this.destSeqNums.get(i);
	        }
	        else {
	        	seqNum = 0;
	        }
	        this.destSeqNums.put(i, (short)(seqNum + 1));
	        return seqNum;
	    }

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		if (data.length < len) {
			this.statusCode = ILLEGAL_ARGUMENT;
		}
		if (this.packetHolder.size() >= 4) {
            System.out.println("Send ignored, too many outgoing packets.");
            return 0;
        }
		if (limiter.size() < 4) {
			if(debug == 1) {
			output.println("Queuing "+len+" bytes for "+dest);
			}
			Packet pack = new Packet(0, 0, calcNextSeqNum(dest), ourMAC, dest, data);
			
			// Packet beacon = new Packet(1, , ourMac, dest, data);
			
			packetHolder.add(pack);
			limiter.add(pack);
			this.statusCode = SUCCESS;
			return len;
		}
		else {
			return 0;
		}
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		if (!displayed) {
			output.println("Link Layer initialized with MAC address " + ourMAC);
			output.println("Send command 0 to see a list of supported commands");
			displayed = true;
		}
		while(true) {
			if(packetHolderIn.size() < 4) {
				 try {
					 Packet packet = this.read.input.take();
			         byte[] data = packet.getData();
			         this.statusCode = SUCCESS;
			         t.setSourceAddr(packet.getSourceAddress());
			         t.setDestAddr(packet.getDestAddress());
			         t.setBuf(data);
			         System.out.println("Test packet :" + packet);
			         return data.length;
				 } catch (InterruptedException e) {
					 System.out.println("Something went wrong with the incoming data.");
					 e.printStackTrace();
				 }  
			}
	      }
	 } 
	
	/**
	 * This method produces the synchronized time.
	 * @return The current time.
	 */
	public static long clock(RF theRF) {
		RF rf = theRF;
		return rf.clock() + globalOffset;
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		return this.statusCode;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmnd, int value) {
		switch (cmnd) {
			case 0: {
				output.println("------------ Commands and Settings -------------");
				output.println("Cmd #0: Display command options and current settings");
				output.println("Cmd #1: Set debug level.  Currently at " + this.debug + "\n Use 1 for full debug output, 0 for no output");
				String collision;
				if (this.maxCollisionWindow) {
					collision = "max";
				}
				else {
					collision = "random";
				}
				output.println("Cmd #2: Set slot selection method.  Currently " + collision + "\n  Use 0 for random slot selection, any other value to use maxCW");
				
				if (sender.interval >= 0) {
					output.println("Cmd #3: Set beacon interval.  Currently at " + sender.interval + " seconds" + "\n  Value specifies seconds between the start of beacons; -1 disables");
				}
				else {
					output.println("Cmd #3: Set beacon interval.  Currently disabled. \n  Value specifies seconds between the start of beacons; -1 disables");
				}
				
				output.println("------------------------------------------------");
				return 0;
			}
			case 1: {
				int prevDebug = this.debug;
				this.debug = value;
				output.println("Setting debug to " + value);
				sender.setDebug(debug);
				reader.setDebug(debug);
				return prevDebug;
			}
			case 2: {
				if (value != 0) {
					this.maxCollisionWindow = true;
					sender.setCollisionWindow(value);
				}
				else {
					this.maxCollisionWindow = false;
					sender.setCollisionWindow(value);
				}
				if (this.maxCollisionWindow) {
					output.println("Using the maximum Collision Window value each time");
					return 0;
				}
				output.println("Using a random Collision Window value each time");
				return 0;
			}
			case 3: {
				if (value < 0) {
					System.out.println("Beacon frames will never be sent");
					sender.setBeaconInterval(value);
					return 0;
				}
				System.out.println("Beacon frames will be sent every " + value + " seconds");
				sender.setBeaconInterval(value);
				return 0;
			}
		}
		System.out.println("Unknown command: " + cmnd);
		return 0;
	}
}
