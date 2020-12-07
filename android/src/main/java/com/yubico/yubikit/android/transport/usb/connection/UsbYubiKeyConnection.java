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

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import com.yubico.yubikit.core.Logger;
import com.yubico.yubikit.core.YubiKeyConnection;

import java.util.concurrent.Semaphore;

abstract class UsbYubiKeyConnection implements YubiKeyConnection {
    private final Semaphore lock;
    private final UsbDeviceConnection usbDeviceConnection;
    private final UsbInterface usbInterface;

    /**
     * Base class for USB based Connections.
     *
     * @param usbDeviceConnection connection, which should already be open
     * @param usbInterface        USB interface, which should already be claimed
     * @param lock                USB Connection lock, which should already be acquired
     */
    protected UsbYubiKeyConnection(UsbDeviceConnection usbDeviceConnection, UsbInterface usbInterface, Semaphore lock) {
        if (lock.availablePermits() > 0) {
            throw new IllegalStateException("Lock must already be held");
        }
        this.usbDeviceConnection = usbDeviceConnection;
        this.usbInterface = usbInterface;
        this.lock = lock;
        Logger.d("USB connection opened: " + this);
    }

    @Override
    public void close() {
        usbDeviceConnection.releaseInterface(usbInterface);
        usbDeviceConnection.close();
        lock.release();
        Logger.d("USB connection closed: " + this);
    }
}
