package com.androidemu.nes;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.UUID;

import com.androidemu.Emulator;

public class NetPlayService {

	public static final int MESSAGE_CONNECTED = 1;
	public static final int MESSAGE_DISCONNECTED = 2;
	public static final int MESSAGE_POWER_ROM = 3;
	public static final int MESSAGE_RESET_ROM = 4;
	public static final int MESSAGE_SAVED_STATE = 5;

	public static final int E_CONNECT_FAILED = 1;
	public static final int E_PROTOCOL_INCOMPATIBLE = 2;
	public static final int E_CONNECTION_CLOSED = 3;

	private static final short PROTO_VERSION = 1;
	private static final short CMD_HELLO = 1;
	private static final short CMD_FRAME_UPDATE = 2;
	private static final short CMD_POWER_ROM = 3;
	private static final short CMD_RESET_ROM = 4;
	private static final short CMD_SAVED_STATE = 5;

	private static final String BT_SERVICE_NAME = "Nesoid";
	private static final UUID BT_SERVICE_UUID = UUID.fromString("8f996e39-374d-466c-bd0c-e0ced64b4e54");

	private static final int MAX_SAVED_STATE_SIZE = 2 * 1024 * 1024;

	private final Handler handler;
	private NetThread netThread;
	private PacketInputStream inputStream;
	private PacketOutputStream outputStream;
	private boolean isServer;
	private boolean waitOnMessage;
	private int maxFramesAhead;
	private int localFrameCount;
	private int remoteFrameCount;
	private int remoteKeys;
	private final Object frameLock = new Object();

	public NetPlayService(Handler h) {
		handler = h;
	}

	public int tcpListen(InetAddress addr, int port)
			throws IOException {
		isServer = true;

		TCPServerThread t = new TCPServerThread(addr, port);
		port = t.getLocalPort();
		start(t);
		return port;
	}

	public void tcpConnect(InetAddress addr, int port) {
		isServer = false;
		start(new TCPClientThread(addr, port));
	}

	public void bluetoothListen() throws IOException {
		isServer = true;
		start(new BluetoothServerThread());
	}

	public void bluetoothConnect(String address) throws IOException {
		isServer = false;
		start(new BluetoothClientThread(address));
	}

	public final boolean isServer() {
		return isServer;
	}

	public final boolean isClient() {
		return !isServer;
	}

	public final void setMaxFramesAhead(int max) {
		synchronized (frameLock) {
			maxFramesAhead = max;
			frameLock.notify();
		}
	}

	public void disconnect() {
		if (netThread == null)
			return;

		netThread.interrupt();
		netThread.cancel();
		try {
			netThread.join();
		} catch (InterruptedException ignored) {}
		netThread = null;
		outputStream = null;
	}

	private void sendHello() throws IOException {
		outputStream.writePacket(
				createPacket(CMD_HELLO, 2).putShort(PROTO_VERSION));
	}

	public int sendFrameUpdate(int keys)
			throws IOException, InterruptedException {

		ByteBuffer p = createPacket(CMD_FRAME_UPDATE, 8);
		p.putInt(localFrameCount);
		p.putInt(keys);
		outputStream.writePacket(p);

		synchronized (frameLock) {
			localFrameCount++;
			while (localFrameCount - remoteFrameCount > maxFramesAhead)
				frameLock.wait();
			return remoteKeys;
		}
	}

	public void sendPowerROM() throws IOException {
		resetFrame();
		outputStream.writePacket(createPacket(CMD_POWER_ROM));
	}

	public void sendResetROM() throws IOException {
		resetFrame();
		outputStream.writePacket(createPacket(CMD_RESET_ROM));
	}

	public void sendSavedState(byte[] state) throws IOException {
		resetFrame();
		outputStream.writePacket(
				createPacket(CMD_SAVED_STATE, 4).putInt(state.length));
		outputStream.writeBytes(state);
	}

	public void sendSyncClientMessage() {
	}

	private void start(NetThread t) {
		netThread = t;
		t.start();
	}

	private void resetFrame() {
		synchronized (frameLock) {
			localFrameCount = 0;
			remoteFrameCount = 0;
		}
	}

	private void manageConnection(InputStream in, OutputStream out)
			throws IOException {
		inputStream = new PacketInputStream(in);
		outputStream = new PacketOutputStream(out);

		// Handshake
		sendHello();
		ByteBuffer p = inputStream.readPacket();
		if (p.getShort() != CMD_HELLO)
			throw new ProtocolException();
		if (p.getShort() != PROTO_VERSION)
			throw new ProtocolException();

		// Main loop
		while (true) {
			p = inputStream.readPacket();
			switch (p.getShort()) {
			case CMD_FRAME_UPDATE:
				synchronized (frameLock) {
					remoteFrameCount = p.getInt();
					remoteKeys = p.getInt();
					frameLock.notify();
				}
				break;

			case CMD_POWER_ROM:
				handler.sendEmptyMessage(MESSAGE_POWER_ROM);
				break;

			case CMD_RESET_ROM:
				handler.sendEmptyMessage(MESSAGE_RESET_ROM);
				break;

			case CMD_SAVED_STATE:
				int len = p.getInt();
				if (len > MAX_SAVED_STATE_SIZE)
					throw new ProtocolException();
				byte[] state = inputStream.readBytes(new byte[len]);
				handler.sendMessage(handler.obtainMessage(MESSAGE_SAVED_STATE, state));
				break;

			default:
				throw new ProtocolException();
			}
		}
	}

	private void onConnectionFailed(int reason) {
		handler.sendMessage(handler.obtainMessage(MESSAGE_DISCONNECTED, reason, 0));
		disconnect();
	}

	private void onConnectionLost() {
		onConnectionFailed(E_CONNECTION_CLOSED);
	}

    // Helper methods to create packet with command in payload
	private static ByteBuffer createPacket(short cmd, int len) {
		return PacketOutputStream.createPacket(len + 2).putShort(cmd);
	}

	private static ByteBuffer createPacket(short cmd) {
		return createPacket(cmd, 0);
	}

	private abstract class NetThread extends Thread {
		public NetThread() {
			super("NetPlayService");
		}

		protected abstract void runIO() throws IOException;
		public abstract void cancel();

		@Override
		public void run() {
			try {
				runIO();
			} catch (ProtocolException e) {
				onConnectionFailed(E_PROTOCOL_INCOMPATIBLE);
			} catch (IOException e) {
				onConnectionLost();
			}
		}
	}

	private class TCPServerThread extends NetThread {
		private final ServerSocket serverSocket;

		public TCPServerThread(InetAddress addr, int port)
				throws IOException {
			serverSocket = new ServerSocket(port, 0, addr);
		}

		public int getLocalPort() {
			return serverSocket.getLocalPort();
		}

		@Override
		protected void runIO() throws IOException {
			Socket socket = serverSocket.accept();
			serverSocket.close();
			manageConnection(socket.getInputStream(), socket.getOutputStream());
		}

		@Override
		public void cancel() {
			try {
				serverSocket.close();
			} catch (IOException ignored) {}
		}
	}

	private class TCPClientThread extends NetThread {
		private InetSocketAddress socketAddr;
		private final Socket socket;

		public TCPClientThread(InetAddress addr, int port) {
			socketAddr = new InetSocketAddress(addr, port);
			socket = new Socket();
		}

		@Override
		protected void runIO() throws IOException {
			socket.connect(socketAddr);
			socketAddr = null;

			manageConnection(socket.getInputStream(),
					socket.getOutputStream());
		}

		@Override
		public void cancel() {
			try {
				socket.close();
			} catch (IOException ignored) {}
		}
	}

	private class BluetoothServerThread extends NetThread {
		private final BluetoothServerSocket serverSocket;

		public BluetoothServerThread() throws IOException {
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			serverSocket = adapter.listenUsingRfcommWithServiceRecord(
					BT_SERVICE_NAME, BT_SERVICE_UUID);
		}

		@Override
		protected void runIO() throws IOException {
			BluetoothSocket socket = serverSocket.accept();
			serverSocket.close();
			manageConnection(socket.getInputStream(), socket.getOutputStream());
		}

		@Override
		public void cancel() {
			try {
				serverSocket.close();
			} catch (IOException ignored) {}
		}
	}

	private class BluetoothClientThread extends NetThread {
		private final BluetoothSocket socket;

		public BluetoothClientThread(String address) throws IOException {
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			BluetoothDevice device = adapter.getRemoteDevice(address);
			socket = device.createRfcommSocketToServiceRecord(BT_SERVICE_UUID);
		}

		@Override
		protected void runIO() throws IOException {
			socket.connect();
			manageConnection(socket.getInputStream(), socket.getOutputStream());
		}

		@Override
		public void cancel() {
			try {
				socket.close();
			} catch (IOException ignored) {}
		}
	}

	private static class PacketInputStream {
		private final InputStream stream;
		private final byte[] twoBytes = new byte[2];

		public PacketInputStream(InputStream s) {
			stream = s;
		}

		public ByteBuffer readPacket() throws IOException {
			readBytes(twoBytes);
			int len = ((twoBytes[0] << 8) & 0xff00) | twoBytes[1];
			byte[] buffer = new byte[len];
			return ByteBuffer.wrap(readBytes(buffer));
		}

		public byte[] readBytes(byte[] buffer) throws IOException {
			int bytes = 0;
			while (bytes < buffer.length) {
				int n = stream.read(buffer, bytes, buffer.length - bytes);
				if (n < 0)
					throw new IOException();
				bytes += n;
			}
			return buffer;
		}
	}

	private static class PacketOutputStream {
		private final OutputStream stream;

		public static ByteBuffer createPacket(int len) {
			return ByteBuffer.allocate(len + 2).putShort((short) len);
		}

		public PacketOutputStream(OutputStream out) {
			stream = out;
		}

		public void writePacket(ByteBuffer buffer) throws IOException {
			writeBytes(buffer.array());
		}

		public void writeBytes(byte[] buffer) throws IOException {
			stream.write(buffer);
			stream.flush();
		}
	}
}
