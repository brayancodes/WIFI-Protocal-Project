//sender class

package wifi;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import rf.RF;

public class sender implements Runnable {
	
	// Instance variables
	private static RF rf;
	ArrayBlockingQueue<Packet> output;
	ArrayBlockingQueue<Packet> acker;
	ArrayBlockingQueue<Packet> limit;
	private Packet packet;
	Random rand = new Random();
	private byte[] curpack;
	private Packet beacon;
	private boolean gotAck = false;
	private int state = 0;
	private Packet theAck;
	private static boolean max;
	private static int debug;
	private static PrintWriter writer;
	private static short ourMAC;
	
	public static int interval = -1;
	
	
	/**
	 * Constructor creates a sender object.
	 * 
	 * @param theRF Rf layer to be used.
	 * @param output The array blocking queue from which queued packets will be sent.
	 * @param acker The array blocking queue from which acks are stored.
	 * @param limiter The limited
	 * @param maxCollisionWindow The max collision window
	 * @param debug
	 * @param writer The printwriter to be used.
	 * @param ourMAC Our mac address which will be used to send packets.
	 */
	public sender(RF theRF, ArrayBlockingQueue<Packet> output, ArrayBlockingQueue<Packet> acker, ArrayBlockingQueue<Packet> limiter, boolean maxCollisionWindow, int debug, PrintWriter writer, short ourMAC) {
		rf = theRF;
		this.output = output;
		this.acker = acker;
		this.limit = limiter;
		this.max = maxCollisionWindow;
		this.debug = debug;
		this.writer = writer;
		this.ourMAC = ourMAC;
	}
	
	

	@SuppressWarnings("static-access")
	private int sifs = rf.aSIFSTime;
	@SuppressWarnings("static-access")
	private int ifs = sifs + (rf.aSlotTime * 2);
	@SuppressWarnings("static-access")
	private int backoff = rf.aCWmin;
	private int slot = 0;
	@SuppressWarnings("static-access")
	private int slotTime = rf.aSlotTime;
	@SuppressWarnings("static-access")
	private int maxRetrys = rf.dot11RetryLimit;
	private int numRetrys = 0;
	@SuppressWarnings("static-access")
	private int maxBackoff = rf.aCWmax;
	
	// instance variables 2
	private long timeout = 1120 + sifs + slotTime;					//computed using the average time it takes to send an ack plus 
	private static final long OUTGOINGOFFSET = 1585;	// Computed for the avg. propagation of a beacon.
	private static final long BEACON_DELAY = 1740; 		// Computed for "real world" delivery of beacons.
	
	// Run thread
	@Override
	public void run() {
		System.out.println("Writer is alive and well");
		
		try {
			if (interval < 0) {
				packet = output.poll(5000, TimeUnit.MILLISECONDS);
			}
			else {
				packet = output.poll(((interval*1000) - BEACON_DELAY), TimeUnit.MILLISECONDS);		// Will break out from waiting for packet so beacon can be sent.
				sendBeacon();
			}
			System.out.println(packet);
			if(packet != null) {
			curpack = packet.packet;
			}
		} catch (InterruptedException e1) {			
			e1.printStackTrace();
		}
		
		 
		// If breaks out above for beacon then will not attempt to send now if queue is empty
		while(true) {
			
				if(packet != null) {
				transmit(); 
				
				long time = LinkLayer.clock(rf);
				
				if (packet.getDestAddress() != -1) {
					waitForAck();
				}
				
				long diff = LinkLayer.clock(rf) - time;
				
				limit.remove();                //if packet was acked remove from limiter
				
		    	try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
	    	try {
	    		
	    		if(debug == 1 && packet != null) {
	   			    writer.println("Moving to AWAIT_PACKET after broadcasting DATA");   
	   		    }
	    		
	    		if (interval < 0) {
					packet = output.poll(5000, TimeUnit.MILLISECONDS); // if beacons are -1 in command.
				}
				else {
					packet = output.poll(((interval*1000) - BEACON_DELAY), TimeUnit.MILLISECONDS); // breaks out for beacons.
					sendBeacon();
				}
				if(packet != null) {
					curpack = packet.packet;
				}
				
				gotAck = false;
				state = 0;
				numRetrys = 0;

			} catch (InterruptedException e) {			
				e.printStackTrace();
			}
	     }
			
	}
	
	/**
	 *  Private method
	 * 
	 * This method sends beacons
	 */
	private void sendBeacon() {
		if(rf.inUse()) {		// Checks if sending.
			waitWhileBusy();
			//calculate time for sending.
			byte[] data = makeTime();
			// type: 2, retry: 0, seq: 0, ourMac, dest set to bcast: -1. data
			beacon = new Packet(2, 0, (short) 0, ourMAC, (short) -1, data);
			byte[] currentBeacon = beacon.packet;
			rf.transmit(currentBeacon);
		}
		else {
			byte[] data = makeTime();
			beacon = new Packet(2, 0, (short) 0, ourMAC, (short) -1, data);
			byte[] currentBeacon = beacon.packet;
			rf.transmit(currentBeacon);
		}
	}
	
		/**
		 * 	Private method
		 * 
		 * This method creates the time stamp for the outgoing beacons.
		 * @return The time stamp in type long.
		 */
		private static byte[] makeTime() {
		long timeNow = LinkLayer.clock(rf);
		long timeToSend = (long) (timeNow + OUTGOINGOFFSET); // Off set added.
		return timeToData(timeToSend);
		
	}
	
	/**
	 * 
	 * @param This method turns a long into a byte[8]
	 * @return The byte[8[ holding the time stamp
	 */
	public static byte[] timeToData(long l) {
	    byte[] result = new byte[8];
	    for (int i = 7; i >= 0; i--) {
	        result[i] = (byte)(l & 0xFF);
	        l >>= 8;
	    }
	    return result;
	}
	
	
	
	/**
	 * Private method
	 * 
	 * Waits inter frame space.
	 */
	private void waitIfs() {
		if(debug == 1) {	  
			if (state == 0) {
				writer.println("Moving to IDLE_DIFS_WAIT with pending DATA");
			}
			else {
				writer.println("Moving to BUSY_DIFS_WAIT after ACK timeout.  (slotCount = " + slot + ")");
			}
		}
		
		if(debug == 1 && state == 1) {
			 writer.println("Waiting for DIFS to elapse after current Tx...");   
		}

		try {
			Thread.sleep(ifs);
		} catch (InterruptedException e1) {			//wait difs
			e1.printStackTrace();
		}
		roundTo50(LinkLayer.clock(rf), rf);					//round up to nearest 50 ms
	}
	
	/**
	 * 	Private method
	 * 
	 * This method puts the thread to sleep if channel is in use.
	 */
	private void waitWhileBusy() {
		while(rf.inUse()) {                  //sleep while channel is busy
			 try {
					Thread.sleep(20);
			 } catch (InterruptedException e) {
				e.printStackTrace();
			 }
		 }
	}
	
	/**
	 * 	Private method
	 * 
	 * This method transmits data packets on the channel.
	 */
	private void transmit() {
		
		if(debug == 1 && numRetrys <= 1) {
			writer.println("Starting collision window at [0..." + backoff + "]");   
		}
		
		if(max) {
			slot = backoff;												//if command is in 2 set slot to max value
		}
		else {
			slot = rand.nextInt(backoff + 1);							//set slot to some random number
		}
		
		if(rf.inUse()) {  					  //if channel is busy change state to one
			 state = 1;
		 }
		 
		 waitWhileBusy();						//wait while busy

		 waitIfs();
		 
		 if(rf.inUse()) {  					  //if channel is busy change state to one
			 state = 1;
		 }		 

		 waitWhileBusy();	
		 
		 if(state == 1) {
			 if(debug == 1) {
				 writer.println("DIFS wait is over, starting slot countdown (" + slot + ")");   
			 }
			 backoff();
		 }		 
		 
		 if(debug == 1 && state == 0) {
			 writer.println("Transmitting DATA after simple DIFS wait at " + LinkLayer.clock(rf));   
		 }
		 if(debug == 1 && state == 1) {
			 writer.println("Transmitting DATA after DIFS+SLOTs wait at " + LinkLayer.clock(rf));   
		 }		 
		 
		 rf.transmit(curpack); 				//send current packet
	
	}
	
	/**
	 * 
	 * Private method
	 * 
	 * Waits for ACKS.
	 */
	private void waitForAck() {
		
		if(debug == 1) {
			 writer.println("Moving to AWAIT_ACK after sending DATA");   
		 }
		
		 numRetrys = 0;
		 while(!gotAck) {							 //wait for ack with the correct sequence number
			try {
				theAck = this.acker.poll(timeout, TimeUnit.MILLISECONDS);         //start timer using poll to specify timeout
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			if(theAck == null) {							//if ack equals null there was a timeout and we should retransmit the packet and increase the contention window
				
				if(debug == 1) {
					 writer.println("Ack timer expired at " + LinkLayer.clock(rf));   
				}
				
				packet.setReTry(1);
				System.out.println("resending");
				System.out.println(packet);
				numRetrys++;
				state = 1;
				
				if(numRetrys != 1) {
					
					if(backoff * 2 + 1 >= maxBackoff) {			//increase the collision window
						backoff = maxBackoff;
					}
					else {					
					    backoff = (backoff * 2) + 1;
					}
					
					if(debug == 1) {
						 writer.println("Doubled collision window -- is now [0..." + backoff + "]");   
					}
				}

				transmit();
			}
			
			if (theAck != null && theAck.getSeqNum() == packet.getSeqNum()) {       //check if ack has correct sequence number
				
				if(debug == 1) {
					
					writer.println("Got a valid ACK: <ACK " + theAck.getSeqNum() + " " + theAck.getSourceAddress() + "-->" + theAck.getDestAddress() + " [0 bytes] (" + theAck.getChecksum() + ")>"); 
				}
				
				gotAck = true;
			}
			else if(numRetrys == maxRetrys) { 						 //checks if retry limit is met and moves to next packet if it is
				gotAck = true;
				if(debug == 1) {
					 writer.println("Moving to AWAIT_PACKET after exceeding retry limit");   
				}
			}
		 } 
	}
	
	/**
	 * 
	 * 	Private method
	 *  Implements back off.
	 */
	private void backoff() {
		 
		System.out.println(slot);
		 while (state == 1 && slot != 0) {							//if the channel was not idle wait additional time

			 try {
				Thread.sleep(slotTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			 
			roundTo50(LinkLayer.clock(rf), rf);
			slot--;
			
			if (rf.inUse()) {
				waitWhileBusy();
				waitIfs();	
			}			
		 }
	}
	
	/**
	 *  Private method
	 * 
	 * This method rounds to 50.
	 * @param time The time
	 * @param rf the RF to use.
	 */
	private static void roundTo50(long time, RF rf) {				//code for rounding to nearest 50 ms
		long offset = time % 50;
		long off = Math.abs(50 - offset);
		//long newTime = time + off;
		
		 try {
			Thread.sleep(off);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(debug == 1) {
			 writer.println("Idle waited until " + LinkLayer.clock(rf));   
		}
	}
	
	/**
	 *  This method sets the collision window size.
	 * @param maxCol The max collision size.
	 */
	public synchronized static void setCollisionWindow(int maxCol) {
		if (maxCol != 0) {
			max = true;
		}else {
			max = false;
		}
	}
	
	// Sets beacon interval from command 
	public synchronized static void setBeaconInterval(int beaconInterval) {
		interval = beaconInterval;
	}
		
	// Sets debugger from command
	public synchronized static void setDebug(int debugger) {
		debug = debugger;
	}
}
