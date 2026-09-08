# Mosh Protocol Analysis for Android

## How Mosh Works

Mosh (Mobile Shell) is fundamentally different from SSH:

### Key Differences:

**SSH (TCP-based):**
- Reliable ordered delivery via TCP
- Connection breaks on IP change
- High latency for small payloads
- No roaming between networks
- Connection drops on packet loss

**Mosh (UDP-based SSP):**
- Uses State-Synchronization Protocol (SSP) over UDP
- Both client and server maintain screen state snapshots
- Predictive local echo (instant feedback)
- Roaming between IP addresses supported
- Handles packet loss gracefully
- Synchronizes state efficiently with diffs

### Mosh Protocol (SSP) Features:

1. **State Synchronization**
   - Client and server both maintain terminal state
   - Server sends state diffs (not full screen)
   - Client applies diffs to local state
   - Timestamps for ordering out-of-order packets

2. **Predictive Local Echo**
   - Client predicts keystroke effects locally
   - Instant feedback without round-trip
   - Server corrections if prediction wrong

3. **UDP with Sequence Numbers**
   - No connection state
   - Handles packet reordering
   - Timestamps for latency compensation

4. **Roaming Support**
   - IP address changes don't break connection
   - Works across network switches (WiFi <-> Cellular)

5. **Keepalive**
   - Periodic UDP packets to maintain NAT mappings
   - Detects connection loss

## Feasibility for Android

### Implementation Options:

### Option 1: Full Mosh Implementation
**Pros:**
- True mosh behavior (roaming, predictive echo, UDP resilience)
- Best mobile experience
- Handles poor connections excellently

**Cons:**
- **Requires server-side mosh-server** (must be installed on Herdr server)
- Complex protocol implementation (SSP)
- Requires UDP port opening (60000-61000)
- Significant development effort
- Terminal emulator integration needed
- Not a drop-in replacement for SSH

**Implementation Requirements:**
- Implement SSP protocol in Kotlin
- Terminal emulator component
- UDP socket management
- State synchronization logic
- Server must run `mosh-server`

### Option 2: SSH with Mosh-like Features (Current Implementation)
**Pros:**
- Works with existing SSH servers (no server changes needed)
- Simpler implementation
- Uses standard SSH port (22)
- Already implemented with JSch
- Some resilience features added

**Cons:**
- Still TCP-based (no true roaming)
- No predictive local echo
- Connection breaks on IP change
- Higher latency than true mosh

**Current Features:**
- Keepalive monitoring (30s intervals)
- Automatic reconnection with exponential backoff
- Connection timeout handling
- Server alive configuration

### Option 3: Hybrid Approach
**Pros:**
- Use SSH for authentication and initial connection
- Use UDP for data transfer (similar to mosh)
- Can fall back to pure SSH if UDP blocked

**Cons:**
- Still requires custom protocol
- Server-side changes needed
- More complex than pure SSH
- Development effort similar to full mosh

## Recommendation

### For Current Project: Keep SSH with Resilience

**Rationale:**
1. **Server compatibility** - Works with any SSH server, no changes needed
2. **Simplicity** - Already implemented and tested
3. **Sufficient for most use cases** - Reconnection handles temporary network issues
4. **No additional dependencies** - JSch is already added
5. **Quick to deploy** - No server-side installation required

**Current Implementation Strengths:**
- Automatic reconnection (5 attempts with exponential backoff)
- Keepalive monitoring
- Connection timeout handling
- Proper cleanup on lifecycle events
- Command execution capability

### For Future Enhancement: Consider Full Mosh

**When to implement:**
- Users report frequent roaming issues
- Need predictive local echo
- Poor network conditions (high packet loss)
- Server-side changes are acceptable
- Development resources available

**Implementation Path:**
1. Install mosh-server on Herdr server
2. Implement SSP protocol in Kotlin
3. Add terminal emulator component
4. Integrate UDP socket management
5. Test roaming scenarios
6. Add fallback to SSH if UDP blocked

## Conclusion

The current SSH implementation with resilience features provides a good balance between:
- **Functionality** - Handles reconnection and keepalive
- **Complexity** - Simple, well-tested implementation
- **Compatibility** - Works with existing SSH servers
- **Development effort** - Already complete

True mosh would be superior for mobile use cases but requires:
- Server-side installation (mosh-server)
- Complex protocol implementation
- Terminal emulator integration
- UDP port management
- Significant development effort

For the current use case (remote agent control from phone), SSH with resilience is sufficient. If roaming and predictive echo become critical requirements, full mosh implementation can be added later.
