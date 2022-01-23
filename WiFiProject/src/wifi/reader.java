//reader class

package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * Reads incoming packets.
 * @author brayanrodriguez, nathanielchuside, jordanpearson.
 *
 */
public class reader implements Runnable {
	
	// Instance variables
	private RF rf;
	LinkLayer link;
	ArrayBlockingQueue<Packet> input;
	ArrayBlockingQueue<Packet> acker;
	private short ourMAC;
	private Packet ack;
	private byte[] data = new byte[0];
	@SuppressWarnings("static-access")
	private int sifs = rf.aSIFSTime;
	private static int debug;
	private PrintWriter writer;
	
	/**
	 *  Constructor for creating the reader object.
	 * @param theRF The RF to use
	 * @param input The array blocking queue that will queue incoming packets.
	 * @param acker The array blocking queue that queues acks
	 * @param ourMAC Our Mac address we will use to check for our packets.
	 * @param debug The debugger input
	 * @param writer The printer writer to use.
	 */
	public reader(RF theRF, ArrayBlockingQueue<Packet> input, ArrayBlockingQueue<Packet> acker, short ourMAC, int debug, PrintWriter writer) {
		this.rf = theRF;
		this.input = input;
		this.acker = acker;
		this.ourMAC = ourMAC;
		this.debug = debug;
		this.writer = writer;
	}
	 
	/**
	 * 	Private method
	 * 
	 * Un-packs a packet that flew in from the channel, and makes sure it is processeda appropriately.
	 * @param packet The packet to be processed.
	 * @throws InterruptedException Exception error to be thrown.
	 */
	 private void unpackIt(Packet packet) throws InterruptedException {
		 long time = LinkLayer.clock(rf);
		 if (packet.getDestAddress() == ourMAC) {
			 try {
				 if (packet.getType() == 1) {
						this.acker.put(packet);
				 }
				 else {
					 System.out.println("giving data up");
					 this.input.put(packet);
				 }
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		 }
		 
		 if(packet.getDestAddress() == -1 && packet.getSourceAddress() != ourMAC && packet.getType() != 2) {
			 try {
				this.input.put(packet);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 }
		 
		 // Handle beacon frames and their time stamps
		 if(packet.getDestAddress() == -1 && packet.getType() == 2) {
			 // get packet time
			 byte[] incomingData = packet.getData();
			 long incomingTime = dataToTime(incomingData);
			 System.out.println("Beacon has arrived! Timestamp: " + incomingTime + "\n");
			 
			 if(debug == 1) {
	   			    writer.println("Got a beacon frame");   
	   		 }
			 
			 // Compare packet time to local
			 long timeNow = LinkLayer.clock(rf);
			 if(incomingTime > timeNow) {
				 LinkLayer.globalOffset = incomingTime - timeNow;  // Adjusting local clock
				 long offset = incomingTime - timeNow;
				 
				 if(debug == 1) {
		   			   writer.println("Advanced our clock by " + offset + " due to beacon:");   
		   			   writer.println("   incoming offset was " + incomingTime + " vs. our " + timeNow + ".  Time is now"); 
		   			   writer.println(incomingTime);  
		   		 }
				 
			 }else {
				 if(debug == 1) {
		   			    writer.println("Ignored beacon: incoming timestamp was " + incomingTime + " vs. our " + timeNow);   
		   		 }
			 }
		 }
		 
		 if (packet.getDestAddress() == ourMAC && packet.getType() == 0) {	
				ack = new Packet(1, 0, packet.getSeqNum(), ourMAC, packet.getSourceAddress(), data);
				System.out.println(ack);
				byte[] ackp = ack.packet;
				waitSifs();
				rf.transmit(ackp);
				
				System.out.println(debug);
				
				if(debug == 1) {
	   			    writer.println("Sending ACK back to " + ack.getDestAddress() + " : <ACK " + ack.getSeqNum() + " " + ack.getSourceAddress() + "-->" + ack.getDestAddress() + " [0 bytes] (" + ack.getChecksum() + ")>");   
	   		    }
	    		
				long diff = LinkLayer.clock(rf) - time;
				System.out.println(diff);
		}
	}

	 // Run thread
	@Override
	public void run() {
		System.out.println("Reader is alive and well");
		while(true) {
			byte[] packetBytes = rf.receive();
			Packet packet = new Packet(packetBytes);
			
			if(debug == 1 && packet.getType() == 0) {
   			    writer.println("Queued incoming DATA packet with good CRC: <DATA " + packet.getSeqNum() + " " + packet.getSourceAddress() + "-->" + packet.getDestAddress() + " [" + new String(packet.getData()) +  "] (" + packet.getChecksum() + ")>");   
   		    }
			if(debug == 1 && packet.getType() == 0) {
   			    writer.println("Receive has blocked, awaiting data");   
   		    }
			
			try {
				this.unpackIt(packet);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}		
	}
	
	/**
	 *  Private method
	 * 
	 *  This method will wait short inter frame.
	 */
	private void waitSifs() {
		try {
			Thread.sleep(sifs);
		} catch (InterruptedException e1) {			//wait ifs either sifs or difs
			e1.printStackTrace();
		}
		roundTo50(LinkLayer.clock(rf), rf);
	}
	
	/**
	 * Private method
	 * 
	 * Mods time by 50, then sleeps.
	 * 
	 * @param time
	 * @param rf
	 */
	private static void roundTo50(long time, RF rf) {
		long offset = time % 50;
		long off = Math.abs(50 - offset);
		//long newTime = time + off;
		
		 try {
			Thread.sleep(off);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * This method turns a byte[8] into a long.
	 * @param b The byte[] that will converted; must be byte[8].
	 * @return The long converted from the byte array.
	 */
	public static long dataToTime(byte[] b) {
	    long result = 0;
	    for (int i = 0; i < 8; i++) {
	        result <<= 8;
	        result |= (b[i] & 0xFF);
	    }
	    return result;
	}
		
	// This method sets the debugger.
	public synchronized static void setDebug(int debugger) {
		debug = debugger;
	}
}