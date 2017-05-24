/**
 *
 *  Copyright (c) 2017 by the respective copyright holders.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Pascal Larin
 *  https://github.com/chaton78
 *
*/

package org.eclipse.smarthome.binding.sinope.internal;

import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;

/**
 * The {@link SinopeConfigStatusMessage} defines
 * the keys to be used for {@link ConfigStatusMessage}s.
 *
 * @author Pascal Larin
 *
 */
public enum SinopeConfigStatusMessage {
    HOST_MISSING("missing-host-configuration"),
    PORT_MISSING("missing-port-configuration"),
    GATEWAY_ID_INVALID("invalid-gateway-id-configuration"),
    API_KEY_INVALID("invalid-api-key-configuration");

    private String messageKey;

    private SinopeConfigStatusMessage(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
