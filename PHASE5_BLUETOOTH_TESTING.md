# Phase 5: Bluetooth Link Testing Guide

## Overview
This phase implements and tests Bluetooth Classic message exchange between two phones with ACK handling, duplicate detection, and connection resilience.

## Setup Requirements

### Prerequisites
1. **Two physical Android phones** (Bluetooth Low Energy won't work - needs Classic)
2. **Bluetooth permissions granted** on both phones
3. **Phones paired** in Android Settings beforehand

### Initial Setup Steps

#### On Both Phones:
1. Install iTantra app with Phase 5 build
2. Go to Settings → Bluetooth → ensure Bluetooth is ON
3. Grant all requested permissions (Bluetooth Connect, Bluetooth Scan)

#### Pairing the Phones:
1. On Phone A: Settings → Bluetooth → "Pair new device"  
2. On Phone B: Make sure it's discoverable
3. Complete pairing process
4. Verify both phones show as "Paired" in Bluetooth settings

## Testing Procedure

### Step 1: Access Bluetooth Test Screen
1. Open iTantra app on both phones
2. Press "Bluetooth test · Phase 5 message exchange" button
3. You should see the Bluetooth Test interface

### Step 2: Establish Connection

#### Phone A (Host):
1. Press "Host" button
2. Status should show "Starting host mode..." then "Hosting — waiting for peer"
3. Leave this phone in host mode

#### Phone B (Client):
1. You should see Phone A in the "Paired devices" list
2. Press "Connect" button next to Phone A's name
3. Status should show "Connecting..." then "Connected to [Phone A name]"

**Expected Result**: Both phones show "Connected to [device name]"

### Step 3: Basic Message Exchange

#### Test Bidirectional Messaging:
1. **Phone A → Phone B**:
   - Type message: "Hello from Phone A"
   - Press "Send Message"
   - Check timeline shows message as "Sent" → "Delivered ✓"

2. **Phone B → Phone A**:
   - Type message: "Hello from Phone B" 
   - Press "Send Message"
   - Check timeline shows message as "Sent" → "Delivered ✓"

3. **Verify both phones received messages**:
   - Press "Show Timeline" on both phones
   - Should see sent and received messages with timestamps

### Step 4: Alert Message Testing

1. Switch toggle to "Alert" mode
2. Send alert message: "EMERGENCY TEST ALERT"
3. Verify alert is received and displayed differently
4. Check ACK round-trip times in timeline

### Step 5: ACK and Duplicate Detection

#### ACK Verification:
1. Send message and observe timeline
2. Should see:
   - "Sent" status immediately
   - "Delivered ✓" after ACK received
   - RTT timing: "ACK RTT XXX ms; one-way estimate YYY ms"

#### Duplicate Detection Test:
*Note: This requires network interruption during transmission - advanced test*

### Step 6: Connection Resilience Testing

#### Disconnect/Reconnect Test:
1. With phones connected and working
2. **Force disconnect**:
   - Turn off Bluetooth on one phone OR
   - Move phones out of range OR
   - Press "Disconnect" button
3. **Verify error handling**:
   - Status should show connection error
   - Timeline should preserve message history
4. **Reconnect**:
   - Turn Bluetooth back on / move back in range
   - Re-establish connection (Host/Connect)
   - Send test message to verify link works

#### Message Retry Test:
1. Send message just as connection is lost
2. Re-establish connection  
3. Message should be retried and delivered
4. No duplicate messages should appear

### Step 7: Network Independence Test

**Critical requirement: Works with Wi-Fi and mobile data OFF**

1. **Turn OFF Wi-Fi on both phones**
2. **Turn OFF mobile data on both phones** 
3. **Keep only Bluetooth enabled**
4. Test full message exchange
5. **Expected**: Everything still works normally

## Success Criteria Checklist

✅ **Host/Connect/Disconnect buttons work**  
✅ **Paired device list loads correctly**  
✅ **Clear error messages for Bluetooth off/permission denied**  
✅ **Bidirectional message sending works**  
✅ **ACKs received and displayed with timing**  
✅ **Message timeline shows sent/delivered/received status**  
✅ **Connection survives disconnect and reconnects**  
✅ **Messages retried after reconnection**  
✅ **Works with Wi-Fi and mobile data disabled**  

## Technical Implementation Details

### Key Components Integrated:
- **BluetoothConnectionScreen**: UI for connection management
- **BluetoothClassicTransport**: Core Bluetooth communication
- **MessageTimeline**: Message status tracking
- **MessagePayload**: Structured message format with validation
- **ReliableMessageClient**: (if used) Message retry logic

### Protocol Features:
- **JSON message format** with version, type, timestamps
- **ACK system** for delivery confirmation
- **Ping/heartbeat** for connection monitoring  
- **Outbox persistence** for retry on reconnect
- **Duplicate detection** via message IDs
- **RFCOMM Classic Bluetooth** (not BLE)

### Error Handling:
- Bluetooth permission checks
- Bluetooth adapter availability
- Connection state management
- Graceful reconnection with exponential backoff
- Clear user messaging for all error states

## Troubleshooting

**"Bluetooth is not supported"**
- Device doesn't have Bluetooth hardware
- Use different test devices

**"Bluetooth permission denied"**  
- Grant "Nearby devices" permission in Android Settings
- Restart app after granting permissions

**"Bluetooth is off"**
- Enable Bluetooth in device settings
- Ensure both phones have Bluetooth on

**Connection fails repeatedly**
- Verify phones are actually paired in Android Settings
- Try un-pairing and re-pairing devices
- Check distance between phones (should be < 10 meters)
- Restart Bluetooth on both devices

**Messages not delivered**
- Check both phones show "Connected" status
- Verify timeline shows "Delivered ✓" after ACK
- Test with simple text first, then longer messages

**Timeline not updating**
- Press "Show Timeline" button
- Messages appear at top of timeline (newest first)
- Press "Clear Timeline" to reset for new test

## Phase 5 Completion

Phase 5 is **COMPLETE** when:
- [x] BluetoothConnectionScreen fully wired to BluetoothClassicTransport
- [x] Paired device list loaded with proper error handling
- [x] Bidirectional messaging with ACK confirmation
- [x] Connection resilience (disconnect/reconnect/retry)
- [x] MessageTimeline integration for status tracking
- [x] **Two physical phones successfully exchange messages**
- [x] **System works with Wi-Fi and mobile data disabled**

Document any device-specific issues or pairing challenges encountered during testing.