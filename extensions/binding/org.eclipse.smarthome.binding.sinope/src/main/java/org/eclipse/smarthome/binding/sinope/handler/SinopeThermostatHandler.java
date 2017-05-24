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

package org.eclipse.smarthome.binding.sinope.handler;

import java.io.IOException;
import java.net.UnknownHostException;

import org.eclipse.smarthome.binding.sinope.SinopeBindingConstants;
import org.eclipse.smarthome.binding.sinope.config.SinopeConfig;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.tulip.sinope.core.SinopeDataReadRequest;
import ca.tulip.sinope.core.SinopeDataWriteRequest;
import ca.tulip.sinope.core.appdata.SinopeHeatLevelData;
import ca.tulip.sinope.core.appdata.SinopeOutTempData;
import ca.tulip.sinope.core.appdata.SinopeRoomTempData;
import ca.tulip.sinope.core.appdata.SinopeSetPointModeData;
import ca.tulip.sinope.core.appdata.SinopeSetPointTempData;
import ca.tulip.sinope.core.internal.SinopeDataAnswer;
import ca.tulip.sinope.util.ByteUtil;

/**
 * The {@link SinopeThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Pascal Larin - Initial contribution
 */
public class SinopeThermostatHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(SinopeThermostatHandler.class);
    private String deviceId;

    private SinopeGatewayHandler gatewayHandler;

    public SinopeThermostatHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        Channel channel = getThing().getChannel(channelUID.getId());
        if (channel != null && SinopeBindingConstants.CHANNEL_SETTEMP.equals(channelUID.getId())) {
            try {
                if (command instanceof DecimalType) {
                    setSetpointTemp(this, ((DecimalType) command).floatValue());
                }
            } catch (IOException e) {
                logger.error("Cannot set point temp: {}", e.getLocalizedMessage());
            }
        }

        if (channel != null && SinopeBindingConstants.CHANNEL_SETMODE.equals(channelUID.getId())) {
            try {
                if (command instanceof StringType) {
                    setSetpointMode(this, Integer.parseInt(((StringType) command).toString()));
                }
            } catch (IOException e) {
                logger.error("Cannot set point mode: {}", e.getLocalizedMessage());
            }
        }
    }

    public void setSetpointTemp(SinopeThermostatHandler sinopeThermostatHandler, float temp)
            throws UnknownHostException, IOException {
        int newTemp = (int) (temp * 100.0);
        SinopeGatewayHandler gateway = getSinopeGatewayHandler();
        gateway.stopPoll();

        if (gateway.connectToBridge()) {

            logger.debug("Connected to bridge");
            byte[] deviceId = SinopeConfig.convert(sinopeThermostatHandler.getDeviceId());
            SinopeDataWriteRequest req = new SinopeDataWriteRequest(gateway.newSeq(), deviceId,
                    new SinopeSetPointTempData());
            ((SinopeSetPointTempData) req.getAppData()).setSetPointTemp(newTemp);

            SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);

            if (answ.getStatus() == 0) {
                logger.debug("Setpoint temp is now: {} C", newTemp);
            } else {
                logger.debug("Cannot Setpoint temp, status: {}", answ.getStatus());
            }
        } else {
            logger.error("Could not connect to bridge to update Setpoint Temp");
        }
        gateway.schedulePoll();
    }

    public void setSetpointMode(SinopeThermostatHandler sinopeThermostatHandler, int mode)
            throws UnknownHostException, IOException {

        SinopeGatewayHandler gateway = getSinopeGatewayHandler();
        gateway.stopPoll();

        if (gateway.connectToBridge()) {
            logger.debug("Connected to bridge");
            byte[] deviceId = SinopeConfig.convert(sinopeThermostatHandler.getDeviceId());
            SinopeDataWriteRequest req = new SinopeDataWriteRequest(gateway.newSeq(), deviceId,
                    new SinopeSetPointModeData());
            ((SinopeSetPointModeData) req.getAppData()).setSetPointMode((byte) mode);

            SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);

            if (answ.getStatus() == 0) {
                logger.debug("Setpoint mode is now : {}", mode);
            } else {
                logger.debug("Cannot Setpoint mode, status: {}", answ.getStatus());
            }
        } else {
            logger.error("Could not connect to bridge to update Setpoint Mode");
        }
        gateway.schedulePoll();

    }

    @Override
    public void initialize() {
        logger.debug("Initializing Sinopé Thermostat");
        super.initialize();
        initializeThing((getBridge() == null) ? null : getBridge().getStatus());

    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {}", bridgeStatusInfo);
        initializeThing(bridgeStatusInfo.getStatus());
    }

    private void initializeThing(ThingStatus bridgeStatus) {
        logger.debug("initializeThing thing {} bridge status {}", getThing().getUID(), bridgeStatus);
        String configDeviceId = (String) getConfig().get(SinopeBindingConstants.CONFIG_PROPERTY_DEVICE_ID);
        if (configDeviceId != null) {
            this.deviceId = configDeviceId;
            if (getSinopeGatewayHandler() != null && bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Device ID not set");
        }
    }

    private synchronized SinopeGatewayHandler getSinopeGatewayHandler() {
        if (this.gatewayHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof SinopeGatewayHandler) {
                this.gatewayHandler = (SinopeGatewayHandler) handler;
                this.gatewayHandler.registerThermostatHandler(this);
            } else {
                return null;
            }
        }
        return this.gatewayHandler;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void updateOutsideTemp(double temp) {
        updateState(SinopeBindingConstants.CHANNEL_OUTTEMP, new DecimalType(temp));

    }

    public void updateRoomTemp(double temp) {
        updateState(SinopeBindingConstants.CHANNEL_INTEMP, new DecimalType(temp));
    }

    public void updateSetPointTemp(double temp) {
        updateState(SinopeBindingConstants.CHANNEL_SETTEMP, new DecimalType(temp));
    }

    public void updateSetPointMode(int mode) {
        updateState(SinopeBindingConstants.CHANNEL_SETMODE, new StringType(Integer.toString(mode)));
    }

    public void updateHeatLevel(int heatLevel) {
        updateState(SinopeBindingConstants.CHANNEL_HEATLEVEL, new DecimalType(heatLevel));
    }

    public void update() throws UnknownHostException, IOException {
        byte[] deviceId = SinopeConfig.convert(this.getDeviceId());
        SinopeGatewayHandler gateway = getSinopeGatewayHandler();
        if (deviceId != null && deviceId.length > 0) {
            this.updateOutsideTemp(readOutsideTemp(gateway, deviceId));
            this.updateRoomTemp(readRoomTemp(gateway, deviceId));
            this.updateSetPointTemp(readSetpointTemp(gateway, deviceId));
            this.updateSetPointMode(readSetpointMode(gateway, deviceId));
            this.updateHeatLevel(readHeatLevel(gateway, deviceId));

        } else {
            logger.error(
                    String.format("Device id is invalid: %s  for Thing UID: %s", getDeviceId(), getThing().getUID()));
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
        }

    }

    private double readRoomTemp(SinopeGatewayHandler gateway, byte[] deviceId)
            throws UnknownHostException, IOException {
        logger.debug("Reading room temp for device id : {}", ByteUtil.toString(deviceId));

        SinopeDataReadRequest req = new SinopeDataReadRequest(gateway.newSeq(), deviceId, new SinopeRoomTempData());

        SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);
        double temp = ((SinopeRoomTempData) answ.getAppData()).getRoomTemp() / 100.0;
        logger.debug(String.format("Room temp is : %2.2f C", temp));
        return temp;
    }

    private double readOutsideTemp(SinopeGatewayHandler gateway, byte[] deviceId)
            throws UnknownHostException, IOException {
        SinopeDataReadRequest req = new SinopeDataReadRequest(gateway.newSeq(), deviceId, new SinopeOutTempData());
        logger.debug("Reading outside temp for device id: {}", ByteUtil.toString(deviceId));
        SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);
        double temp = ((SinopeOutTempData) answ.getAppData()).getOutTemp() / 100.0;
        logger.debug(String.format("Outside temp is : %2.2f C", temp));
        return temp;

    }

    private double readSetpointTemp(SinopeGatewayHandler gateway, byte[] deviceId)
            throws UnknownHostException, IOException {
        SinopeDataReadRequest req = new SinopeDataReadRequest(gateway.newSeq(), deviceId, new SinopeSetPointTempData());
        logger.debug("Reading Set Point temp for device id: {}", ByteUtil.toString(deviceId));
        SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);
        double temp = ((SinopeSetPointTempData) answ.getAppData()).getSetPointTemp() / 100.0;
        logger.debug(String.format("Set Point temp is : %2.2f C", temp));
        return temp;
    }

    private int readSetpointMode(SinopeGatewayHandler gateway, byte[] deviceId)
            throws UnknownHostException, IOException {
        SinopeDataReadRequest req = new SinopeDataReadRequest(gateway.newSeq(), deviceId, new SinopeSetPointModeData());
        logger.debug("Reading Set Point mode for device id: {}", ByteUtil.toString(deviceId));
        SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);
        int mode = ((SinopeSetPointModeData) answ.getAppData()).getSetPointMode();
        logger.debug(String.format("Set Point mode is : %d", mode));
        return mode;
    }

    private int readHeatLevel(SinopeGatewayHandler gateway, byte[] deviceId) throws UnknownHostException, IOException {
        SinopeDataReadRequest req = new SinopeDataReadRequest(gateway.newSeq(), deviceId, new SinopeHeatLevelData());
        logger.debug("Reading Heat Level for device id: {}", ByteUtil.toString(deviceId));
        SinopeDataAnswer answ = (SinopeDataAnswer) gateway.execute(req);
        int level = ((SinopeHeatLevelData) answ.getAppData()).getHeatLevel();
        logger.debug(String.format("Heat level is  : %d", level));
        return level;
    }

}
