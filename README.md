# Unified Socket & Web-Based Group Chat (Text, Voice & Video)

An enterprise-grade, multi-modal Java group chat application providing **real-time text messaging**, **low-latency UDP voice chat**, **multi-participant video streaming (with Screen Sharing & Live Camera Feeds)**, and an **interactive Web Browser interface** built with pure Java standard libraries (Java Sockets, UDP Datagrams, Java Sound API, Java Swing, and standard HTTP/WebSockets).

---

## 🌟 Key Features

### 1. 🌐 Live Web Browser Interface
- **Zero-Install Web App**: Runs directly in Google Chrome, Microsoft Edge, Firefox, Safari, or any modern web browser.
- **Live Video Streaming**: Real-time webcam streaming and screen sharing via `navigator.mediaDevices`.
- **Live Voice Chat**: Web Audio API integration with live VU meter showing microphone activity.
- **WebSocket Synchronization**: Instant text messaging, participant video grids, and online member presence.
- **Session Persistence**: Stays logged in across page reloads and reconnects until the user explicitly clicks **Log Out**.

### 2. 💬 Real-Time Desktop Text Chat
- Multithreaded TCP client-server architecture.
- Instant message delivery and broadcasting to all connected peers.
- Synchronized online members list with dynamic join/leave notifications.
- Timestamped message history and built-in commands (`/users`, `/help`).

### 3. 🎙️ Desktop Voice Chat
- Direct microphone audio capture via `javax.sound.sampled.TargetDataLine`.
- Standardized low-latency PCM audio stream (16 kHz, 16-bit signed mono).
- Real-time speaker playback via `javax.sound.sampled.SourceDataLine`.
- **Microphone Mute / Unmute** toggle.
- **Deafen / Speaker Mute** toggle.
- **Real-Time VU Level Meter**: Visual bar indicating microphone input activity.

### 4. 📹 Desktop Video Chat & Screen Sharing
- High-efficiency UDP packetization and chunk reassembly with application-level frame headers.
- Real-time JPEG frame compression (0.65 quality factor at 15 FPS).
- **Two Video Modes**:
  - **Live Screen Sharing**: Captures real system screen/windows in real time via `java.awt.Robot`.
  - **Live Camera / Animated Avatar**: Generates interactive video stream with user initials, talking animation reacting to microphone sound levels, and live status badges.
- **Dynamic Video Grid (`VideoGridPanel`)**: Auto-adjusts layout (1x1, 1x2, 2x2, 2x3, 3x3) to fit all active video feeds seamlessly.

---

## 🔒 Security & Environment Configuration

This project is built following security best practices to protect sensitive data, environment configurations, and credentials:

### 1. Zero Hardcoded Secrets
- **No API keys, credentials, tokens, or private secrets** are embedded in the source code or tracked in the Git repository.
- All configuration parameters (ports, hosts, origins) are dynamically read from system environment variables with safe fallback defaults.

### 2. Environment Variables (`.env`)
A template file [`.env.example`](.env.example) is provided. To configure custom settings locally:
```bash
# Copy the template to your local environment file
cp .env.example .env
```
Supported environment variables:
| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Web HTTP & WebSocket server port (auto-set by cloud hosts). |
| `TEXT_CHAT_PORT` | `5000` | TCP port for desktop text chat. |
| `VOICE_PORT` | `5001` | UDP port for voice audio streaming. |
| `VIDEO_PORT` | `5002` | UDP port for video frame broadcasting. |
| `CHAT_SERVER_HOST` | `localhost` | Host address used by desktop clients to reach the server. |
| `SERVER_HOST` | `0.0.0.0` | Binding interface for network listening. |

### 3. Repository Exclusion Rules (`.gitignore`)
The project `.gitignore` strictly excludes:
- All `.env`, `*.env`, and local environment variable files.
- Configuration and properties files (`config.properties`, `local.properties`).
- Private keys, certificates, and keystores (`*.key`, `*.pem`, `*.crt`, `*.p12`, `*.jks`).
- Cloud service account credentials and tokens (`credentials*.json`, `token*.json`).
- Compiled `.class` binaries, packages, and crash dumps.

### 4. HTTP Security Headers
The built-in web server automatically enforces standard web security headers:
- `X-Content-Type-Options: nosniff` — Prevents MIME-sniffing vulnerabilities.
- `X-Frame-Options: SAMEORIGIN` — Mitigates clickjacking attacks.
- `X-XSS-Protection: 1; mode=block` — Activates browser Cross-Site Scripting filters.
- `Referrer-Policy: strict-origin-when-cross-origin` — Protects referrer privacy.
- `Cache-Control: no-cache, no-store, must-revalidate` — Prevents sensitive chat caching.

### 5. Production HTTPS / WSS Encryption
- Modern web browsers require **HTTPS** for camera and microphone access (`getUserMedia`).
- When deploying to cloud platforms (such as Render, Railway, or Fly.io), SSL/TLS certificates are automatically provisioned, securing all Web and WebSocket traffic with end-to-end HTTPS/WSS encryption.

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
| `WebChatServer.java` | Built-in HTTP server with security headers and RFC 6455 WebSocket engine. |
| `web/index.html` | Modern responsive Web UI featuring video tiles, screen sharing, audio VU meters, and chat. |
| `ChatClientGUI.java` | Desktop Swing GUI featuring the video grid, text chat, online member list, and media toolbar. |
| `AudioConfig.java` | Audio specifications (16 kHz, 16-bit Mono PCM), buffer sizes, and format helpers. |
| `VoiceServer.java` | UDP relay server broadcasting voice packets to all active participants. |
| `VoiceClient.java` | Client audio engine handling microphone capture, speaker playback, and VU level metering. |
| `VideoConfig.java` | Video resolution (480x360), frame rates (15 FPS), JPEG compression, and chunk parameters. |
| `VideoServer.java` | UDP relay server broadcasting fragmented/chunked video frames. |
| `VideoClient.java` | Client video engine managing screen sharing, live camera avatars, JPEG compression, and packet assembly. |
| `VideoGridPanel.java` | Adaptive grid panel rendering letterboxed video tiles with participant labels. |
| `.env.example` | Safe template for local environment configuration without exposing credentials. |
| `Dockerfile` | Multi-stage container definition for secure cloud deployment. |
| `render.yaml` | Infrastructure-as-code specification for 1-click cloud deployment. |

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
