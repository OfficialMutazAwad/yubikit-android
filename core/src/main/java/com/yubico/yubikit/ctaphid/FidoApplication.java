/*
 * Copyright (C) 2020 Yubico AB - All Rights Reserved
 * Unauthorized copying and/or distribution of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.yubico.yubikit.ctaphid;

import com.yubico.yubikit.utils.Version;
import com.yubico.yubikit.exceptions.YubiKeyCommunicationException;
import com.yubico.yubikit.utils.Logger;
import com.yubico.yubikit.utils.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.annotation.Nullable;

public class FidoApplication implements Closeable {
    private static final int PACKET_SIZE = 64;

    public static final byte TYPE_INIT = (byte) 0x80;

    private static final byte CMD_PING = TYPE_INIT | 0x01;
    private static final byte CMD_APDU = TYPE_INIT | 0x03;
    private static final byte CMD_INIT = TYPE_INIT | 0x06;
    private static final byte CMD_WINK = TYPE_INIT | 0x08;
    private static final byte CMD_CANCEL = TYPE_INIT | 0x11;

    private static final byte STATUS_ERROR = TYPE_INIT | 0x3f;
    private static final byte STATUS_KEEPALIVE = TYPE_INIT | 0x3b;

    private final FidoConnection connection;

    private final Version version;
    private int channelId;

    public FidoApplication(FidoConnection connection) throws YubiKeyCommunicationException, IOException {
        this.connection = connection;

        // init
        byte[] nonce = new byte[8];
        new SecureRandom().nextBytes(nonce);

        channelId = 0xffffffff;
        ByteBuffer buffer = ByteBuffer.wrap(sendAndReceive(CMD_INIT, nonce, null));
        byte[] responseNonce = new byte[nonce.length];
        buffer.get(responseNonce);
        if (!MessageDigest.isEqual(nonce, responseNonce)) {
            throw new YubiKeyCommunicationException("Got wrong nonce!");
        }

        channelId = buffer.getInt();
        buffer.get(); // U2F HID version
        byte[] versionBytes = new byte[3];
        buffer.get(versionBytes);
        version = Version.parse(versionBytes);
        buffer.get(); // Capabilities
        Logger.d(String.format("fido connection set up with channel ID: 0x%08x", channelId));
    }

    public byte[] sendAndReceive(byte cmd, byte[] payload, @Nullable CommandState state) throws YubiKeyCommunicationException, IOException {
        ByteBuffer toSend = ByteBuffer.wrap(payload);
        byte[] buffer = new byte[PACKET_SIZE];
        ByteBuffer packet = ByteBuffer.wrap(buffer);
        byte seq = 0;

        // Send request
        packet.putInt(channelId).put(cmd).putShort((short) toSend.remaining());
        do {
            toSend.get(buffer, packet.position(), Math.min(toSend.remaining(), packet.remaining()));
            connection.sendPacket(buffer);
            Logger.d(buffer.length + " bytes sent over fido: " + StringUtils.bytesToHex(buffer));
            Arrays.fill(buffer, (byte) 0);
            packet.clear();
            packet.putInt(channelId).put((byte) (0x7f & seq++));
        } while (toSend.hasRemaining());

        // Read response
        seq = 0;
        ByteBuffer response = null;
        do {
            packet.clear();
            if (state != null && state.isCancelled()) {
                Logger.d("sending CTAP cancel...");
                Arrays.fill(buffer, (byte) 0);
                packet.putInt(channelId).put(CMD_CANCEL);
                connection.sendPacket(buffer);
                Logger.d(buffer.length + " bytes sent over fido: " + StringUtils.bytesToHex(buffer));
                packet.clear();
            }

            int recvLength = connection.readPacket(buffer);
            if (recvLength < 0) {
                throw new YubiKeyCommunicationException("Unable to read from endpoint");
            }
            Logger.d(recvLength + " bytes received over fido: " + StringUtils.bytesToHex(buffer));
            int responseChannel = packet.getInt();
            if (responseChannel != channelId) {
                throw new YubiKeyCommunicationException(String.format("Wrong Channel ID. Expecting: %d, Got: %d", channelId, responseChannel));
            }
            if (response == null) {
                byte responseCmd = packet.get();
                if (responseCmd == cmd) {
                    response = ByteBuffer.allocate(packet.getShort());
                } else if (responseCmd == STATUS_KEEPALIVE) {
                    byte keepaliveStatus = packet.get();
                    Logger.d(String.format("received keepalive status: %x", keepaliveStatus));
                    if (state != null) {
                        state.onKeepAliveStatus(keepaliveStatus);
                    }
                    continue;
                } else if (responseCmd == STATUS_ERROR) {
                    throw new CtapError(packet.get());
                } else {
                    throw new YubiKeyCommunicationException(String.format("Wrong response command. Expecting: %x, Got: %x", cmd, responseCmd));
                }
            } else {
                byte responseSeq = packet.get();
                if (responseSeq != seq++) {
                    throw new YubiKeyCommunicationException(String.format("Wrong sequence number. Expecting %d, Got: %d", seq - 1, responseSeq));
                }
            }
            response.put(buffer, packet.position(), Math.min(packet.remaining(), response.remaining()));
        } while (response == null || response.hasRemaining());

        return response.array();
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public void close() throws IOException {
        connection.close();
        Logger.d("fido connection closed");
    }
}