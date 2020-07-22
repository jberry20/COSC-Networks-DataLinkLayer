// =============================================================================
// IMPORTS


import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.Iterator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
// =============================================================================


// =============================================================================
/**
 * @file   ParityDataLinkLayer.java
 * @author Justin Berry (jberry20@amherst.edu)
 *
 */
public class ParityDataLinkLayer extends DataLinkLayer {
// =============================================================================
 
   // =============================================================================
    protected byte[] createFrame (byte[] data) {

	Queue<Byte> frame = new LinkedList<Byte>();
	// Add start tag, frame # and parity to frame.
	frame.add(startTag);
	frame.add(frameSent);
	frame.add(generateParity(data));
	
	for (int i = 0; i < data.length; i++) {
	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
	    if ((data[i] == startTag) ||
		(data[i] == stopTag) ||
		(data[i] == responseTag)||
		(data[i] == ~(responseTag)) ||
		 (data[i] == escapeTag)) {

		frame.add(escapeTag);
	    }
	     // Add the data byte itself.	    
	    frame.add(data[i]);
	}
	// End with a stop tag.
	frame.add(stopTag);	
	// lastFrameSent is a class member that stores the last frame sent.
	lastFrameSent = createArray(frame);

	// -1 value signals we are waiting for a response.
	status = -1;
	return lastFrameSent;
    } // createFrame()
  // =============================================================================

    
    // =============================================================================
    
    private byte[] createResponseFrame(byte[] data) {
	Queue<Byte> frame = new LinkedList<Byte>();
	// Add start tag, frame # and parity to frame.
	frame.add(startTag);
	frame.add(frameSent);
	frame.add(generateParity(data));
	for (int i = 0; i < data.length; i++) {
	    frame.add(data[i]);
	}
	frame.add(stopTag);
	lastResponseFrame = createArray(frame);
	return lastResponseFrame;
    } // createResponseFrame()
  // =============================================================================

    
    // =============================================================================
	
    protected Queue<Byte> processFrame () {

	Queue<Byte> extractedData = new LinkedList<Byte>();	
	Iterator<Byte> receiveBufferIterator = receiveBuffer.iterator();
	boolean startTagFound = false;
	boolean stopTagFound = false;
	
	while (!stopTagFound && receiveBufferIterator.hasNext()) {
	    byte nextByte = receiveBufferIterator.next();
	    if (nextByte == escapeTag) {
		// We found escape tag - next byte is data, not tag.
		if (receiveBufferIterator.hasNext()) {
		    nextByte = receiveBufferIterator.next();
		    extractedData.add(nextByte);
		}
	    } else if (startTagFound && (nextByte == responseTag) || (nextByte == ~(responseTag))) {
		response = nextByte;
	    } else if (nextByte == stopTag) {
		// After Start Tag found - store bytes until stop tag found.
		stopTagFound = true;
		// Once Stop Tag is found remove elements from buffer.
		cleanBufferUpTo(receiveBufferIterator);
	    } else if (nextByte == startTag) {
		//If a start tag is found - look for meta/data.
		startTagFound = true;
	    } else if (nextByte != startTag && nextByte != stopTag && nextByte != escapeTag && nextByte != responseTag && nextByte != ~(responseTag)) {
		// All else is data.
		extractedData.add(nextByte);
	    }
	}
       
	if (stopTagFound == true) return extractedData;

	return null;	   
    } // processFrame ()
    // ===============================================================


    
    // =============================================================================
    protected void finishFrameSend () {

	// If no response from recipent - then keep sending 
	for (int i = 0; i < 10; i++) {
	    if (status != (byte)1) {
		transmit(lastFrameSent);
	    } else {
		break;
	    }
	}
	frameSent += 0x01;	
    } // finishFrameSend ()
    // =============================================================================


    // =============================================================================
    protected void finishFrameReceive (Queue<Byte> data) {

	// fetch meta data - first byte is frame number, second byte is parity.
	frameNumber = (byte)-1;
	senderParity = (byte)-1;
	Iterator<Byte> dataIterator = data.iterator();			
	if (dataIterator.hasNext()) {
	    frameNumber = dataIterator.next();
	    dataIterator.remove();
	}
	if (dataIterator.hasNext()) {
	    senderParity = dataIterator.next();
	    dataIterator.remove();
	}

	byte[] responseFrame = new byte[1];	
	byte[] extractedData = createArray(data);
	byte receiverParity = generateParity(extractedData);
	responseFrame[0] = ~(responseTag);
	
	if (senderParity != receiverParity) {
	    // send a nack
	    responseFrame[0] = ~(responseTag);
	    transmit(createResponseFrame(responseFrame));
	    return;
	}
	if (response == responseTag) {
	    // ack rec
	    response = 0;
	    status = 1;
	    return;
	}
	if (response == ~(responseTag)) {
	    // nack rec
	    if (lastFrameSent == null) {
		transmit(lastResponseFrame);
	    }
	    response = 0;
	    return;
	}
	// good msg rec
	if (frameNumber == frameSent) {
	    client.receive(extractedData);	
	    frameSent += 0x01;
	    responseFrame[0] = responseTag;
	} 	 
	transmit(createResponseFrame(responseFrame));
	return;
    } // finishFrameReceive ()
    // =============================================================================

    // ===============================================================
    private void cleanBufferUpTo (Iterator<Byte> end) {

	Iterator<Byte> i = receiveBuffer.iterator();
	while (i.hasNext() && i != end) {
	    i.next();
	    i.remove();
	}

    } // createBufferUpTo ()
    // =============================================================================


    // =============================================================================
    protected byte generateParity(byte[] data) {

	int parityCount = 0;
	byte dataByte;	
	for (int x = 0; x < data.length; x++) {
	    for (int i = BITS_PER_BYTE - 1; i >= 0; i--) {
		boolean bit = ((1 << i) & data[x]) != 0;
		if (bit == true) parityCount++;
	    }
	}
	if (parityCount % 2 == 0) return (byte)0;
	return (byte)1;
    } // generateParity () 
    // =============================================================================


    // ===============================================================
    // DATA MEMBERS
    // ===============================================================
    private byte[] lastFrameSent;
    private byte[] lastResponseFrame;    
    private int status;
    private byte senderParity = -1;
    private byte frameNumber = -1;
    private byte response = 0;    
    private byte frameSent = 0;
    //    private byte frameRecv = 0;
    private final boolean DEBUG_FLAG = true;

    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    private final byte responseTag = (byte)'~';
    // ===============================================================
    



// ===================================================================
} // class ParityDataLinkLayer
// ===================================================================
