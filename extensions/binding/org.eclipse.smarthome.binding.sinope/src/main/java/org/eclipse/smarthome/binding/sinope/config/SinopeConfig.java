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

package org.eclipse.smarthome.binding.sinope.config;

/**
 * Holds Config for the Sinope Gateway
 *
 * @author Pascal Larin
 *
 */
public class SinopeConfig {
    /**
     * Hostname of the Sinope Gateway
     */
    public String hostname;
    /**
     * ip port
     */
    public Integer port;
    /**
     * Gateway ID
     */
    public String gatewayId;
    /**
     * API Key returned by the Gateway
     */
    public String apiKey;
    /**
     * The number of milliseconds between fetches from the sinope deivces
     */
    public Integer refresh;

    /**
     * Convert Hex Config String to byte
     */
    public static byte[] convert(String value) {
        if (value == null) {
            return null;
        }
        value = value.replace("-", "");
        value = value.replace("0x", "");
        value = value.replace(" ", "");
        if (value.length() % 2 == 0 && value.length() > 1) {
            byte[] b = new byte[value.length() / 2];

            for (int i = 0; i < value.length(); i = i + 2) {
                b[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
            }
            return b;
        } else {
            return null;
        }
    }
}
