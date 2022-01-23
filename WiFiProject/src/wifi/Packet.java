package wifi;

import java.util.zip.CRC32;

/**
 * 
 * This class creates a Packet object along with methods for manipulating the data inside.
 *
 */
public class Packet {

	// Instance variables.
	public static int DATA = 0;
    public static int ACK = 1;
    public static int BEACON = 2;
    public static int CTS = 4;
    public static int RTS = 5;
    public byte[] packet;
    
    /**
     * This constructor creates a packet object from a byte[] holding the packets data according to specs.
     * @param packetBytes
     */
    public Packet(byte[] packetBytes) {
        final int dataSize = packetBytes.length - 10;
        if (dataSize > 2038) {
            throw new IllegalArgumentException("Too much data in the packet!");
        }
        if (dataSize < 0) {
            throw new IllegalArgumentException("Negative data in your packet, that's impossible.");
        }
        this.packet = new byte[packetBytes.length];
        for (int i = 0; i < packetBytes.length; ++i) {
            this.packet[i] = packetBytes[i];
        }
    }
    
    /**
     * This constructor creates a packet with the inclusion of a parameter for a setting the retry bit.
     * @param type Packet type.
     * @param reTry Sets retry bit.
     * @param seq The sequence number of the packet.
     * @param src The source address of the packet.
     * @param dest The destination address of the packet.
     * @param data The data which the packet will hold.
     */
    public Packet(int type, int reTry, short seq, short src, short dest, byte[] data) {
    	if (data.length > 2038) {
    		throw new IllegalArgumentException("Packet size too large.");
    	}
    	this.packet = new byte[10 + data.length];
    	this.setType(type);
        this.setReTry(reTry);
        this.setSeqNum(seq);
        this.setDestAddress(dest);
        this.setSourceAddress(src);
        this.setData(data);
        this.setChecksum();
    }
    
    /**
     * This constructor creates a packet.
     * @param type Packet type.
     * @param seq The sequence number of the packet.
     * @param src The source address of the packet.
     * @param dest The destination address of the packet.
     * @param data The data which the packet will hold.
     */
    public Packet(int type, short seq, short src, short dest, byte[] data) {
    	 if (data.length > 2038) {
             throw new IllegalArgumentException("Packet size too large.");
         }
    	this.packet = new byte[10 + data.length];
        this.setType(type);
        this.setReTry(0);
        this.setSeqNum(seq);
        this.setDestAddress(dest);
        this.setSourceAddress(src);
        this.setData(data);
        this.setChecksum();
    }
    
    // The main method.
    public static void main(String args[]) {
    	String str = "Hello world!";
    	byte[] data = str.getBytes();
    	int retry = 0;
    	short seq = 0;
    	short src = 564;
    	short dest = 312;
    	Packet packet = new Packet(DATA, retry, seq, src, dest, data);
    	Packet packet2 = new Packet(packet.packet);
    	System.out.println(packet2);
    }

    /**
     * Sets the retry bit in the packet.
     * @param retry The retry bit (0:= not a rentrans. / 1:= a retrans.).
     */
    public void setReTry(int retry) {
    	int mask = 0;
		mask = mask | 1;
		mask = mask << 4;
		
    	if (retry == 1) {
    		this.packet[0] = (byte) (this.packet[0] | mask);
    		return;
    	}
    	else if (retry == 0) {
    		return;
    	}
    	throw new IllegalArgumentException("Not a valid retry bit.");
    }
    
    /**
     * This method sets the type of the packet.
     * @param type The type of the packet.
     */
	public void setType(int type) {
        if (type >= DATA && type <= RTS && type != 3) {
            byte[] packet = this.packet;
            packet[0] &= 0x1F;
            packet[0] |= (byte)(type << 5);
            return;
        }
        throw new IllegalArgumentException("Unknown message type");
    }
	
	/**
	 * This method get the type of the packet.
	 * @return The type of the packet.
	 */
	public int getType() {
        return (this.packet[0] & 0xE0) >>> 5;
    }
    
	/**
	 * This method sets the sequence number of the packet.
	 * @param seqNum The sequence number of the packet.
	 */
    public void setSeqNum(short seqNum) {
        if (seqNum != (seqNum & 0xFFF)) {
            throw new IllegalArgumentException("Sequence number is not 12 bits.");
        }
        this.packet[1] = (byte)(seqNum & 0xFF);
    }
    
    /**
     * This method gets the sequence number of the packet.
     * @return The sequence number of the packet.
     */
    public short getSeqNum() {
        return this.translatorByteShort((byte)(this.packet[0] & 0xF), this.packet[1]);
    }
    
    /**
     * This method sets the destination address of the packet.
     * @param destAddress The destination of the packet.
     */
    public void setDestAddress(short destAddress) {
        this.packet[2] = (byte)(destAddress >>> 8 & 0xFF);
        this.packet[3] = (byte)(destAddress & 0xFF);
    }
    
    /**
     * This method gets the destination address of the packet.
     * @return The destination address of the packet.
     */
    public short getDestAddress() {
        return this.translatorByteShort(this.packet[2], this.packet[3]);
    }
    
    /**
     * This method sets the source address of the packet.
     * @param sourceAddress The source address of the packet.
     */
    public void setSourceAddress(short sourceAddress) {
        this.packet[4] = (byte)(sourceAddress >>> 8 & 0xFF);
        this.packet[5] = (byte)(sourceAddress & 0xFF);
    }
    
    /**
     * This method gets the source address of the packet.
     * @return The source address of the packet.
     */
    public short getSourceAddress() {
        return this.translatorByteShort(this.packet[4], this.packet[5]);
    }
    
    /**
     * This method sets the data into the packets.
     * @param data The data to be put into the packet.
     */
    public void setData(byte[] data) {
        if (data.length > 2038) {
            throw new IllegalArgumentException("Too much data to put in the packet.");
            }
        for (int i = 0; i < data.length; ++i) {
            this.packet[6 + i] = data[i];
        }
    }
    
    /**
     * This method retrieves the data out of the packet.
     * @return The data from the packet.
     */
    public byte[] getData() {
        final byte[] data = new byte[this.packet.length - 10];
        for (int i = 0; i < data.length; ++i) {
            data[i] = this.packet[6 + i];
        }
        return data;
    }
    
    /**
     * This method returns the state of the packet.
     * @Return String that is the state.
     */
    @Override
    public String toString() {
    	this.setChecksum();
    	String packetString = "";
        switch (this.getType()) {
            case 0: {
               packetString += "Type: DATA";
                break;
            }
            case 1: {
               packetString += "Type: ACK";
                break;
            }
            case 2: {
               packetString += "Type: BEACON";
                break;
            }
            case 4: {
               packetString += "Type: CTS";
                break;
            }
            case 5: {
               packetString += "Type: RTS";
                break;
            }
            default: {
               packetString += "Not a valid type.";
                break;
            }
        }
       packetString += "\nSequence Number:" + this.getSeqNum();
//        if (this.isRetry()) {
//           packetString += "\nIs a retry.";
//        }
       packetString += "\nSource Address: " + this.getSourceAddress();
       packetString += "\nDestination Address: " + this.getDestAddress();
       packetString += "\nData: [" + new String(this.getData()) + "]";
       packetString += "\nChecksum: (" + this.getChecksum() + ")";
        return "____________\n" + packetString + "\n____________";
    }
	
    /**
     * This method sets the checksum of the packet.
     */
    public void setChecksum() {
    	CRC32 checksum = new CRC32();
        checksum.update(this.packet, 0, this.packet.length - 4);
    	int checkSumValue = (int) checksum.getValue();
        int checksumIndex = this.packet.length - 4;
        this.packet[checksumIndex + 0] = (byte)(checkSumValue >>> 24 & 0xFF);
        this.packet[checksumIndex + 1] = (byte)(checkSumValue >>> 16 & 0xFF);
        this.packet[checksumIndex + 2] = (byte)(checkSumValue >>> 8 & 0xFF);
        this.packet[checksumIndex + 3] = (byte)(checkSumValue & 0xFF);
    }
    
    /**
     * This method returns the checksum of the packet.
     * @return The checksum of the packet.
     */
    public int getChecksum() {
        int checksumIndex = this.packet.length - 4;
        int incomingCRC = this.packet[checksumIndex + 3] & 0xFF;
        incomingCRC |= (this.packet[checksumIndex + 2] << 8 & 0xFF00);
        incomingCRC |= (this.packet[checksumIndex + 1] << 16 & 0xFF0000);
        return incomingCRC;
    }
    
    /**
     *  Private method
     * 
     * This private method performs bitwise operations.
     * @param half1 first half.
     * @param half2 second half.
     * @return The short that was constructed.
     */
    private short translatorByteShort(byte half1, byte half2) {
        int tempShort = half1 & 0xFF;
        tempShort = (tempShort << 8 | (half2 & 0xFF));
        return (short)tempShort;
    }
    
}
