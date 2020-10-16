/*
 * Copyright (C) 2020 Yubico.
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

package com.yubico.yubikit.android.transport.usb.connection;

import android.hardware.usb.*;
import android.util.Pair;
import com.yubico.yubikit.core.NotSupportedOperation;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class SmartCardConnectionHandler extends InterfaceConnectionHandler<UsbSmartCardConnection> {
    public SmartCardConnectionHandler() {
        super(UsbConstants.USB_CLASS_CSCID);
    }

    @Override
    public UsbSmartCardConnection createConnection(UsbDevice usbDevice, UsbDeviceConnection usbDeviceConnection, Semaphore connectionLock) throws IOException {
        UsbInterface usbInterface = getClaimedInterface(usbDevice, usbDeviceConnection);
        Pair<UsbEndpoint, UsbEndpoint> endpoints = findEndpoints(usbInterface);
        return new UsbSmartCardConnection(usbDeviceConnection, usbInterface, connectionLock, endpoints.first, endpoints.second);
    }

    private Pair<UsbEndpoint, UsbEndpoint> findEndpoints(UsbInterface usbInterface) {
        UsbEndpoint endpointIn = null;
        UsbEndpoint endpointOut = null;

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint;
                } else {
                    endpointOut = endpoint;
                }
            }
        }
        if (endpointIn != null && endpointOut != null) {
            return new Pair<>(endpointIn, endpointOut);
        }
        throw new NotSupportedOperation("Missing CCID bulk endpoints");
    }
}