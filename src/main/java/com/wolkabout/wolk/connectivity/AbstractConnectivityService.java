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
package com.wolkabout.wolk.connectivity;

import com.wolkabout.wolk.connectivity.model.InboundMessage;

import java.io.IOException;

public abstract class AbstractConnectivityService implements ConnectivityService {
    private Listener listener;

    public void setListener(final ConnectivityService.Listener listener) {
        this.listener = listener;
    }

    protected void listenerOnConnected() {
        if (listener != null) {
            listener.onConnected();
        }
    }

    protected void listenerOnInboundMessage(final InboundMessage inboundMessage) {
        if (listener != null) {
            listener.onInboundMessage(inboundMessage);
        }
    }
}