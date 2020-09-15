/*
 * Copyright (C) 2019 Yubico.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yubico.yubikit.android.transport.nfc;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.yubico.yubikit.utils.Interface;
import com.yubico.yubikit.iso7816.Iso7816Connection;
import com.yubico.yubikit.utils.Logger;
import com.yubico.yubikit.utils.StringUtils;

import java.io.IOException;

/**
 * NFC service for interacting with the YubiKey
 */
public class NfcIso7816Connection implements Iso7816Connection {

    /**
     * Provides access to ISO-DEP (ISO 14443-4) properties and I/O operations on a {@link Tag}.
     */
    private IsoDep card;

    /**
     * Instantiates session for nfc tag interaction
     *
     * @param card the tag that has been discovered
     */
    NfcIso7816Connection(IsoDep card) {
        this.card = card;
        Logger.d("nfc connection opened");
    }

    @Override
    public Interface getInterface() {
        return Interface.NFC;
    }

    @Override
    public byte[] transceive(byte[] apdu) throws IOException {
        Logger.d("sent: " + StringUtils.bytesToHex(apdu));
        byte[] received = card.transceive(apdu);
        Logger.d("received: " + StringUtils.bytesToHex(received));
        return received;
    }

    @Override
    public void close() throws IOException {
        card.close();
        Logger.d("nfc connection closed");
    }
}