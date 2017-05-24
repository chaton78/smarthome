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

package org.eclipse.smarthome.binding.sinope.internal.discovery;

import java.io.IOException;
import java.util.Set;

import org.eclipse.smarthome.binding.sinope.SinopeBindingConstants;
import org.eclipse.smarthome.binding.sinope.handler.SinopeGatewayHandler;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryListener;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.tulip.sinope.util.ByteUtil;

/**
 * The {@link AbstractDiscoveryService} provides methods which handle the {@link DiscoveryListener}s.
 *
 * Subclasses do not have to care about adding and removing those listeners.
 * They can use the protected methods {@link #thingDiscovered(DiscoveryResult)} and {@link #thingRemoved(String)} in
 * order to notify the registered {@link DiscoveryListener}s.
 *
 * @author Pascal Larin - Initial contribution
 *
 */
public class SinopeThingsDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(SinopeThingsDiscoveryService.class);

    private final static int SEARCH_TIME = 120;

    private SinopeGatewayHandler sinopeGatewayHandler;

    public SinopeThingsDiscoveryService(SinopeGatewayHandler sinopeGatewayHandler) {
        super(SEARCH_TIME);
        this.sinopeGatewayHandler = sinopeGatewayHandler;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return SinopeBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    public void startScan() {
        logger.info("Sinope Things starting scan");
        try {
            sinopeGatewayHandler.startSearch(this);
        } catch (IOException e) {
            logger.error("Search failed with an exception", e);
        }
    }

    @Override
    protected synchronized void stopScan() {
        try {
            sinopeGatewayHandler.stopSearch();
        } catch (IOException e) {
            logger.error("Can't stop search with an exception", e);
        } finally {
            super.stopScan();
            removeOlderResults(getTimestampOfLastScan());
            logger.info("Sinope Things scan stopped");
        }
    }

    public void newThermostat(byte[] deviceId) {
        logger.info("Sinope Things service discovered a new device with id: " + ByteUtil.toString(deviceId));
        ThingTypeUID thingTypeUID = SinopeBindingConstants.THING_TYPE_THERMO;
        ThingUID bridgeUID = sinopeGatewayHandler.getThing().getUID();
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, toUID(deviceId));

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                .withBridge(bridgeUID)
                .withProperty(SinopeBindingConstants.CONFIG_PROPERTY_DEVICE_ID, ByteUtil.toString(deviceId)).build();

        thingDiscovered(discoveryResult);

    }

    private static String toUID(byte[] deviceId) {

        StringBuilder sb = new StringBuilder();
        for (byte b : deviceId) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();

    }

}
