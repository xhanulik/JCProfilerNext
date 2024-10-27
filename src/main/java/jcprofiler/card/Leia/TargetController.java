/**
 * Code Copyright (c) 2024, Veronika Hanulikova <xhanulik@gmail.com>
 * Python driver for the LEIA Smart Reader (https://github.com/cw-leia/smartleia) Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 */

package jcprofiler.card.Leia;

import com.fazecast.jSerialComm.*;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TargetController {
    final int RESPONSE_LEN_SIZE = 4;
    final int COMMAND_LEN_SIZE = 4;
    private SerialPort serialPort = null;
    private final int USB_VID = 0x3483;
    private final int USB_PID = 0x0BB9;

    private final Object lock = new Object();

    /**
     * Try to detect connected Leia card reader and open serial port for communication.
     */
    public boolean open() {
        SerialPort[] availablePorts = SerialPort.getCommPorts();
        int count = 0;

        // Iterate over available ports, check VID and PID and get the count
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                count++;
            }
        }

        if (count > 2 || count == 0) {
            // Do not throw exception, so we can call it in loop
            return false;
        }

        // Test connection to the ports and try to open the final one
        for (SerialPort port : availablePorts) {
            if (port.getVendorID() == USB_VID && port.getProductID() == USB_PID) {
                try {
                    port.setBaudRate(115200);
                    // Python code uses timeout=1s ~ get bytes immediately when the requested number of bytes are available, otherwise wait until the timeout expires
                    // blocking for write might not be working on other OS than Windows
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                            2000, 0);
                    if (port.openPort()) {
                        serialPort = port;
                        System.out.printf("Serial port %s (%d/%d) is open and ready for communication\n",
                                serialPort.getDescriptivePortName(), USB_VID, USB_PID);
                        break;
                    }
                } catch (Exception e) {
                    port.closePort();
                    throw new RuntimeException("Cannot connect to LEIA device!");
                }
            }
        }

        // read all bytes from port
        readAvailableBytes();
        testWaitingFlag();
        return true;
    }

    /**
     * Check for open valid port
     */
    private void isValidPort() {
        if (serialPort == null ||  !serialPort.isOpen()) {
            throw new RuntimeException("No serial connection created!");
        }
    }

    /**
     * Read available bytes from connection. Used also for emptying the read buffer.
     * @return read bytes
     */
    private byte[] readAvailableBytes() {
        isValidPort();

        int availableBytes = serialPort.bytesAvailable();
        byte[] buffer = new byte[availableBytes];  // Create a buffer with an appropriate size

        // 'while' cycle might be needed in future
        if (serialPort.bytesAvailable() > 0) {
            serialPort.readBytes(buffer, availableBytes, 0);
        }
        return buffer;
    }

    /**
     * Wait for given amount of time
     * @param milliseconds time to wait
     */
    private void wait(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {
            System.out.println("Sleep is overrated");
        }
    }

    /**
     *  Verify the presence of the waiting flag.
     */
    private void testWaitingFlag() {
        isValidPort();
        readAvailableBytes(); // empty read buffer

        byte[] command = new byte[] { ' ' }; // b" "
        serialPort.writeBytes(command, command.length, 0);
        wait(100); // wait for 0.1s

        // Read 1 + all available bytes
        byte[] singleByte = new byte[1];
        int bytesRead = serialPort.readBytes(singleByte, 1);
        byte[] allBytes = readAvailableBytes();
        if (bytesRead == 0 && allBytes.length == 0)
            throw new RuntimeException();

        // combine
        byte[] buffer = new byte[1 + allBytes.length];
        buffer[0] = singleByte[0];
        System.arraycopy(allBytes, 0, buffer, 1, allBytes.length);

        if (buffer[buffer.length - 1] != 87) { // "W"
            throw new RuntimeException("Cannot connect to LEIA.");
        }
    }

    /**
     * Verify the presence of the status flag.
     */
    private void checkStatus() {
        isValidPort();
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);

        if (readBytes == 0)
            throw new RuntimeException("No status flag received.");

        while(status[0] == 'w') {
            // reading wait extension flag, try to read again
            readBytes = serialPort.readBytes(status, status.length);
            if (readBytes == 0)
                throw new RuntimeException("No status flag received.");
        }

        if (status[0] == 'U')
            throw new RuntimeException("LEIA firmware do not handle this command.");
        else if (status[0] == 'E')
            throw new RuntimeException("Unknown error (E).");
        else if (status[0] != 'S')
            throw new RuntimeException("Invalid status flag '{s}' received.");

        readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0)
            throw new RuntimeException("Status not received.");
        else if (status[0] != 0x00)
            throw new RuntimeException("Error status!");
    }

    /**
     * Verify the presence of acknowledge flag.
     */
    private void checkAck() {
        isValidPort();
        byte[] status = new byte[1];
        int readBytes = serialPort.readBytes(status, status.length);
        if (readBytes == 0 || status[0] != 'R')
            throw new RuntimeException("No response ack received.");
    }

    /**
     * Send command to board
     * @param command command in bytes
     * @param struct data to be sent
     */
    private void sendCommand(byte[] command, DataStructure struct) {
        isValidPort();
        testWaitingFlag();
        // Send command first
        serialPort.writeBytes(command, command.length, 0);

        if (struct == null) {
            // send simple byte command filled with zeroes aligned to command len size
            byte[] zeroCommand = new byte[COMMAND_LEN_SIZE]; // BigEndian
            serialPort.writeBytes(zeroCommand, zeroCommand.length, 0);
        } else {
            // pack structure into byte array
            byte[] packedData = struct.pack();
            // wrap packed size into 4 bytes
            byte[] size = ByteBuffer.allocate(COMMAND_LEN_SIZE).putInt(packedData.length).array();
            serialPort.writeBytes(size, size.length, 0);
            serialPort.writeBytes(packedData, packedData.length, 0);
        }
        checkStatus();
        checkAck();
    }

    /**
     * Read response size after sending a command
     * @return response size
     * @implNote response size is LittleEndian!
     */
    private int readResponseSize() {
        isValidPort();
        byte[] response = new byte[RESPONSE_LEN_SIZE];
        int readBytes = serialPort.readBytes(response, RESPONSE_LEN_SIZE);
        if (readBytes != RESPONSE_LEN_SIZE)
            throw new RuntimeException("Unexpected bytes for response size! " + readBytes);
        // Omit creation of response size struct as in python
        return ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * test whether the card is inserted to the board
     * @return True if card is inserted, false otherise
     * @implNote command ID: "?"
     */
    public boolean isCardInserted() {
        isValidPort();
        byte[] response;
        synchronized (lock) {
            this.sendCommand("?".getBytes(), null);
            int resSize = this.readResponseSize();
            if (resSize != 1) {
                throw new RuntimeException("Invalid response size for 'isCardInserted' (?) command.");
            }
            response = new byte[1];
            serialPort.readBytes(response, resSize);
        }
        return response[0] == 1;
    }

    /**
     * Configure connected smartcard reader, simplified support.
     * @param protocolToUse value of ConfigureSmartcardCommand.T, no support for automatic choice
     * @param ETUToUse 0 for letting the reader negotiate ETU
     * @param freqToUse 0 for letting the reader negotiate frequency
     * @param negotiatePts true if yes, false otherwise
     * @param negotiateBaudrate true if yes, false otherwise
     * @implNote command ID: "c" + LEIA structure
     */
    public void configureSmartcard(ConfigureSmartcardCommand.T protocolToUse, int ETUToUse, int freqToUse, boolean negotiatePts, boolean negotiateBaudrate) {
        isValidPort();
        if (!isCardInserted())
            throw new RuntimeException("Error: card not inserted! Please insert a card to configure it.");
        synchronized (lock) {
            testWaitingFlag();

            // simplified scenario - no support for automatic choice
            if (protocolToUse == null) {
                protocolToUse = ConfigureSmartcardCommand.T.T1;
            }

            try {
                ConfigureSmartcardCommand struct = new ConfigureSmartcardCommand(protocolToUse.value(), ETUToUse, freqToUse, negotiatePts, negotiateBaudrate);
                sendCommand("c".getBytes(), struct);
            } catch (Exception e) {
                throw new RuntimeException("Error: configure_smartcard failed with the asked parameters!: " + e.getMessage());
            }
        }
    }

    /**
     * Fill the ATR object with information received from board
     * @implNote command ID: "t"
     */
    public ATR getATR() {
        isValidPort();
        ATR atr = new ATR();
        synchronized (lock) {
            sendCommand("t".getBytes(), null);
            int resSize = this.readResponseSize();
            if (resSize != 55) // size of ATR arguments
                throw new RuntimeException("Unexpected response size! Cannot parse ATR.");
            byte[] response = new byte[55];
            serialPort.readBytes(response, resSize);
            atr.unpack(response);
        }
        return atr;
    }

    /**
     * Simplified set_trigger_strategy routine for resetting all trigger strategies to none
     * @implNote target.set_trigger_strategy(1, point_list=[], delay=0)
     * @implNote command ID: "O" + trigger strategy struct
     */
    public void resetTriggerStrategy() {
        isValidPort();
        synchronized (lock) {
            SetTriggerStrategy strategy = new SetTriggerStrategy(true);
            sendCommand("O".getBytes(), strategy);
        }
    }

    /**
     * Simplified set_trigger_strategy routine for setting only pre-send APDu strategy
     * @implNote target.set_trigger_strategy(1, point_list=[TriggerPoints.TRIG_PRE_SEND_APDU], delay=0)
     * @implNote command ID: "O" + trigger strategy struct
     */
    public void setPreSendAPDUTriggerStrategy() {
        isValidPort();
        synchronized (lock) {
            SetTriggerStrategy strategy = new SetTriggerStrategy(false);
            sendCommand("O".getBytes(), strategy);
        }
    }

    /**
     * Send APDU to card connected in Leia car reader
     * @param commandApdu APDU to send
     * @return filled ResponseAPDU structure
     */
    public ResponseAPDU sendAPDU(CommandAPDU commandApdu) {
        isValidPort();
        APDU apdu = new APDU((byte) commandApdu.getCLA(), (byte) commandApdu.getINS(), (byte) commandApdu.getP1(),
                (byte) commandApdu.getP2(), commandApdu.getData());
        RESP response = new RESP(); //  for unpacking the answer data
        ResponseAPDU responseApdu; // for return
        synchronized (lock) {
            sendCommand("a".getBytes(), apdu);
            int resSize = this.readResponseSize();
            if (resSize < 14)
                throw new RuntimeException("Unexpected response size! Cannot parse ATR.");
            byte[] responseBytes = new byte[resSize];
            serialPort.readBytes(responseBytes, resSize);
            response.unpack(responseBytes);

            // convert into ResponseAPDU
            responseApdu = new ResponseAPDU(response.toArray());
        }
        return responseApdu;
    }

    /**
     * Close opened port for LEIA device
     */
    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            System.out.printf("Closing serial port %s (%d/%d)\n", serialPort.getDescriptivePortName(), USB_VID, USB_PID);
            serialPort.closePort();
            serialPort = null;
        }
    }
}
