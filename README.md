# Unified Socket-Based Group Chat (Text, Voice & Video)

An enterprise-grade, multi-modal Java group chat application providing **real-time text messaging**, **low-latency UDP voice chat**, and **multi-participant video streaming (with Screen Sharing & Live Camera Feeds)** built with pure Java standard libraries (Java Sockets, UDP Datagrams, Java Sound API, and Java Swing).

---

## 🌟 Key Features

### 1. 💬 Real-Time Text Chat (TCP Port 5000)
- Multithreaded TCP client-server architecture.
- Instant message delivery and broadcasting to all connected peers.
- Synchronized online members list with dynamic join/leave notifications.
- Timestamped message history.
- Built-in slash commands (`/users`, `/help`).

### 2. 🎙️ Live Voice Chat (UDP Port 5001)
- Direct microphone audio capture via `javax.sound.sampled.TargetDataLine`.
- Standardized low-latency PCM audio stream (16 kHz, 16-bit signed mono).
- Real-time speaker playback via `javax.sound.sampled.SourceDataLine`.
- **Microphone Mute / Unmute** toggle.
- **Deafen / Speaker Mute** toggle.
- **Real-Time VU Level Meter**: Visual bar indicating microphone input activity.
- Automatic server endpoint discovery and heartbeat keep-alive.

### 3. 📹 Multi-Participant Video Chat & Screen Sharing (UDP Port 5002)
- High-efficiency UDP packetization and chunk reassembly with application-level frame headers.
- Real-time JPEG frame compression (0.65 quality factor at 15 FPS).
- **Two Video Modes**:
  - **Live Screen Sharing**: Captures real system screen/windows in real time via `java.awt.Robot`.
  - **Live Camera / Animated Avatar**: Generates interactive video stream with user initials, talking animation reacting to microphone sound levels, and live status badges.
- **Dynamic Video Grid (`VideoGridPanel`)**: Auto-adjusts layout (1x1, 1x2, 2x2, 2x3, 3x3) to fit all active video feeds seamlessly.

---

## 🏗️ Architecture Overview

```text
               +---------------------------------------------+
               |              GroupChatServer                |
               |                                             |
               |  [TCP: 5000] Text Server & Presence         |
               |  [UDP: 5001] Voice Broadcast Relay          |
               |  [UDP: 5002] Video Broadcast Relay          |
               +----------------------+----------------------+
                                      |
         +----------------------------+----------------------------+
         |                                                         |
+--------v-------------------+                            +--------v-------------------+
|      ChatClientGUI         |                            |      ChatClientGUI         |
|                            |                            |                            |
| - Text Socket (TCP 5000)   |                            | - Text Socket (TCP 5000)   |
| - VoiceClient (UDP 5001)   |                            | - VoiceClient (UDP 5001)   |
| - VideoClient (UDP 5002)   |                            | - VideoClient (UDP 5002)   |
| - VideoGridPanel           |                            | - VideoGridPanel           |
+----------------------------+                            +----------------------------+
```

---

## 📁 Project Structure

| File | Description |
|---|---|
| `GroupChatServer.java` | Core server orchestrating TCP text chat, UDP voice server, and UDP video server. |
| `ChatClientGUI.java` | Modern Swing GUI featuring the video grid, text chat, online member list, and media toolbar. |
| `AudioConfig.java` | Audio specifications (16 kHz, 16-bit Mono PCM), buffer sizes, and format helpers. |
| `VoiceServer.java` | UDP relay server broadcasting voice packets to all active participants. |
| `VoiceClient.java` | Client audio engine handling microphone capture, speaker playback, and VU level metering. |
| `VideoConfig.java` | Video resolution (480x360), frame rates (15 FPS), JPEG compression, and chunk parameters. |
| `VideoServer.java` | UDP relay server broadcasting fragmented/chunked video frames. |
| `VideoClient.java` | Client video engine managing screen sharing, live camera avatars, JPEG compression, and packet assembly. |
| `VideoGridPanel.java` | Adaptive grid panel rendering letterboxed video tiles with participant labels. |

---

## 🚀 How to Run

### Prerequisites
- Java Development Kit (JDK 11, 17, 21 or newer).
- No external libraries or build tools required (pure standard Java).

### 1. Compile All Files
```bash
javac *.java
```

### 2. Start the Unified Server
```bash
java GroupChatServer
```
*The server will start listening on TCP port 5000 (Text), UDP port 5001 (Voice), and UDP port 5002 (Video).*

### 3. Start Clients
Launch one or more client instances in separate terminal windows:
```bash
java ChatClientGUI
```

1. Enter your chosen username when prompted.
2. The modern GUI window will open.
3. Use the bottom toolbar to:
   - Click `[Mic On]` to mute/unmute your microphone.
   - Click `[Audio On]` to deafen incoming audio.
   - Click `[Camera: Off]` to start streaming your live camera avatar.
   - Click `[Screen: Off]` to start live screen sharing.
   - Type messages in the chat box and press **Enter** or click **Send**.
