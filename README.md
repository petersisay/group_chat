# Unified Socket & Web-Based Group Chat (Text, Voice & Video)

An enterprise-grade, multi-modal Java group chat application providing **real-time text messaging**, **low-latency UDP voice chat**, **multi-participant video streaming (with Screen Sharing & Live Camera Feeds)**, and an **interactive Web Browser interface** built with pure Java standard libraries (Java Sockets, UDP Datagrams, Java Sound API, Java Swing, and standard HTTP/WebSockets).

---

## 🌟 Key Features

### 1. 🌐 Live Web Browser Interface (Port 8080)
- **Zero-Install Web App**: Runs directly in Google Chrome, Microsoft Edge, Firefox, Safari, or any modern web browser at `http://localhost:8080`.
- **Live Video Streaming**: Real-time webcam streaming and screen sharing via `navigator.mediaDevices`.
- **Live Voice Chat**: Web Audio API integration with live VU meter showing microphone activity.
- **WebSocket Synchronization**: Instant text messaging, participant video grids, and online member presence.

### 2. 💬 Real-Time Desktop Text Chat (TCP Port 5000)
- Multithreaded TCP client-server architecture.
- Instant message delivery and broadcasting to all connected peers.
- Synchronized online members list with dynamic join/leave notifications.
- Timestamped message history.
- Built-in slash commands (`/users`, `/help`).

### 3. 🎙️ Desktop Voice Chat (UDP Port 5001)
- Direct microphone audio capture via `javax.sound.sampled.TargetDataLine`.
- Standardized low-latency PCM audio stream (16 kHz, 16-bit signed mono).
- Real-time speaker playback via `javax.sound.sampled.SourceDataLine`.
- **Microphone Mute / Unmute** toggle.
- **Deafen / Speaker Mute** toggle.
- **Real-Time VU Level Meter**: Visual bar indicating microphone input activity.

### 4. 📹 Desktop Video Chat & Screen Sharing (UDP Port 5002)
- High-efficiency UDP packetization and chunk reassembly with application-level frame headers.
- Real-time JPEG frame compression (0.65 quality factor at 15 FPS).
- **Two Video Modes**:
  - **Live Screen Sharing**: Captures real system screen/windows in real time via `java.awt.Robot`.
  - **Live Camera / Animated Avatar**: Generates interactive video stream with user initials, talking animation reacting to microphone sound levels, and live status badges.
- **Dynamic Video Grid (`VideoGridPanel`)**: Auto-adjusts layout (1x1, 1x2, 2x2, 2x3, 3x3) to fit all active video feeds seamlessly.

---

## 🏗️ Architecture Overview

```text
               +-------------------------------------------------+
               |                GroupChatServer                  |
               |                                                 |
               |  [HTTP/WS: 8080] Web Browser Video/Voice/Chat   |
               |  [TCP:     5000] Desktop Text Server            |
               |  [UDP:     5001] Desktop Voice Broadcast Relay  |
               |  [UDP:     5002] Desktop Video Broadcast Relay  |
               +------------------------+------------------------+
                                        |
       +--------------------------------+--------------------------------+
       |                                                                 |
+------v---------------------+                            +--------------v-------------+
|    Web Browser Clients     |                            |   Desktop Swing Clients    |
| (http://localhost:8080)    |                            |                            |
| - Live Web Camera / Screen |                            | - ChatClientGUI            |
| - Web Audio API & VU Meter |                            | - VoiceClient (UDP 5001)   |
| - WebSocket Real-Time Chat |                            | - VideoClient (UDP 5002)   |
+----------------------------+                            +----------------------------+
```

---

## 📁 Project Structure

| File | Description |
|---|---|
| `GroupChatServer.java` | Core server orchestrating Web (8080), Text (5000), Voice (5001), and Video (5002). |
| `WebChatServer.java` | Built-in HTTP server and RFC 6455 WebSocket engine for browser clients. |
| `web/index.html` | Modern responsive Web UI featuring video tiles, screen sharing, audio VU meters, and live chat. |
| `ChatClientGUI.java` | Desktop Swing GUI featuring the video grid, text chat, online member list, and media toolbar. |
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
*The server will start listening on:*
- **Port 8080**: Live Web Browser Video/Voice/Chat (`http://localhost:8080`)
- **Port 5000**: Desktop Text Chat (TCP)
- **Port 5001**: Desktop Voice Chat (UDP)
- **Port 5002**: Desktop Video Chat (UDP)

### 3. Open in Your Browser
Navigate to:
```
http://localhost:8080
```
Open multiple tabs to test group video chat, voice activity, and live text messages!

### 4. Or Start Desktop Swing Client(s)
```bash
java ChatClientGUI
```
