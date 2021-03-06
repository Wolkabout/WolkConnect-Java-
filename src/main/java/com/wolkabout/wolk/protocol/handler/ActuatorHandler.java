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
package com.wolkabout.wolk.protocol.handler;

import com.wolkabout.wolk.Wolk;
import com.wolkabout.wolk.model.ActuatorCommand;
import com.wolkabout.wolk.model.ActuatorStatus;

import java.lang.ref.WeakReference;

public abstract class ActuatorHandler {

    private WeakReference<Wolk> wolk;

    protected Wolk getWolk() {
        return wolk.get();
    }

    public void setWolk(Wolk wolk) {
        if (this.wolk != null) {
            throw new IllegalStateException("Wolk instance already set.");
        }

        this.wolk = new WeakReference<>(wolk);
    }

    /**
     * When the actuation command is given from the platform, it will be delivered to this method.
     * This method should pass the new value for the actuator to device.
     *
     * @param actuatorCommand {@link ActuatorCommand}
     */
    public abstract void onActuationReceived(ActuatorCommand actuatorCommand);

    /**
     * Reads the status of actuator from device and returns it as ActuatorStatus object.
     *
     * @param ref of the actuator.
     * @return ActuatorStatus object.
     */
    public abstract ActuatorStatus getActuatorStatus(String ref);
}
