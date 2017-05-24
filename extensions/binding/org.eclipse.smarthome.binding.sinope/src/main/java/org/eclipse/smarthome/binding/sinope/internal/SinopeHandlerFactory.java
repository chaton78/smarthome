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

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.binding.sinope.SinopeBindingConstants;
import org.eclipse.smarthome.binding.sinope.handler.SinopeGatewayHandler;
import org.eclipse.smarthome.binding.sinope.handler.SinopeThermostatHandler;
import org.eclipse.smarthome.binding.sinope.internal.discovery.SinopeThingsDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.osgi.framework.ServiceRegistration;

/**
 * {@link SinopeHandlerFactory} is a factory for {@link SinopeThermostatHandler}s and {@link SinopeGatewayHandler}s
 *
 * @author Pascal Larin - Initial contribution
 */

public class SinopeHandlerFactory extends BaseThingHandlerFactory {

    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = SinopeBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (SinopeBindingConstants.THING_TYPE_GATEWAY.equals(thingTypeUID)) {
            SinopeGatewayHandler bridge = new SinopeGatewayHandler((Bridge) thing);
            registerDiscoveryService(bridge);
            return bridge;
        } else if (SinopeBindingConstants.THING_TYPE_THERMO.equals(thingTypeUID)) {
            return new SinopeThermostatHandler(thing);
        }

        return null;
    }

    private synchronized void registerDiscoveryService(SinopeGatewayHandler bridge) {
        SinopeThingsDiscoveryService discoveryService = new SinopeThingsDiscoveryService(bridge);

        this.discoveryServiceRegs.put(bridge.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof SinopeGatewayHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                serviceReg.unregister();
                discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            }
        }
    }

}
