/*
 * Copyright (c) 2018 WolkAbout Technology s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.wolkabout.wolk.firmwareupdate;

import com.wolkabout.wolk.firmwareupdate.model.FirmwareUpdateError;
import com.wolkabout.wolk.firmwareupdate.model.FirmwareUpdateStatus;

public abstract class FirmwareInstaller {

    private FirmwareUpdateProtocol protocol;

    void setFirmwareUpdateProtocol(FirmwareUpdateProtocol protocol) {
        this.protocol = protocol;
    }

    protected final void publishStatus(FirmwareUpdateStatus status) {
        protocol.sendStatusMessage(status);
        onFirmwareVersion();
    }

    protected final void publishError(FirmwareUpdateError error) {
        protocol.sendErrorMessage(error);
        onFirmwareVersion();
    }

    protected final void publishFirmwareVersion(String version) {
        protocol.publishFirmwareVersion(version);
    }

    public void onInstallCommandReceived(String fileName) {
        publishStatus(FirmwareUpdateStatus.INSTALLATION);
    }

    public void onAbortCommandReceived() {
        publishStatus(FirmwareUpdateStatus.ABORTED);
    }

    public abstract void onFirmwareVersion();
}