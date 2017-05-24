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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.binding.sinope.SinopeBindingConstants;
import org.eclipse.smarthome.binding.sinope.config.SinopeConfig;
import org.eclipse.smarthome.binding.sinope.internal.SinopeConfigStatusMessage;
import org.eclipse.smarthome.binding.sinope.internal.discovery.SinopeThingsDiscoveryService;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.tulip.sinope.core.SinopeApiLoginAnswer;
import ca.tulip.sinope.core.SinopeApiLoginRequest;
import ca.tulip.sinope.core.SinopeDeviceReportAnswer;
import ca.tulip.sinope.core.internal.SinopeAnswer;
import ca.tulip.sinope.core.internal.SinopeDataAnswer;
import ca.tulip.sinope.core.internal.SinopeDataRequest;
import ca.tulip.sinope.core.internal.SinopeRequest;
import ca.tulip.sinope.util.ByteUtil;

/**
 * The {@link SinopeGatewayHandler} is responsible for handling commands for the Sinopé Gateway.
 *
 * @author Pascal Larin - Initial contribution
 */
public class SinopeGatewayHandler extends ConfigStatusBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SinopeGatewayHandler.class);
    private ScheduledFuture<?> pollFuture;
    private long refreshInterval;
    private final List<SinopeThermostatHandler> thermostatHandlers = new CopyOnWriteArrayList<>();
    private int seq = 1;
    private Socket clientSocket;

    public SinopeGatewayHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Sinope Gateway");
        super.initialize();
        SinopeConfig config = getConfigAs(SinopeConfig.class);
        if (config.hostname == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway hostname must be set");
        } else if (config.port == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway port must be set");
        } else if (config.gatewayId == null || SinopeConfig.convert(config.gatewayId) == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway Id must be set");
        } else if (config.apiKey == null || SinopeConfig.convert(config.apiKey) == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Api Key must be set");
        } else {
            refreshInterval = config.refresh;
            updateStatus(ThingStatus.ONLINE);
            schedulePoll();
        }

    }

    @Override
    public void dispose() {
        super.dispose();
        stopPoll();
    }

    void pollNow() {
        schedulePoll();
    }

    void schedulePoll() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }
        logger.debug("Scheduling poll for 500ms out, then every {} ms", refreshInterval);
        pollFuture = scheduler.scheduleAtFixedRate(pollingRunnable, 500, refreshInterval, TimeUnit.MILLISECONDS);
    }

    synchronized void stopPoll() {
        if (pollFuture != null && !pollFuture.isCancelled()) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    private synchronized void poll() {
        if (thermostatHandlers.size() > 0) {
            logger.debug("Polling for state");
            try {
                if (connectToBridge()) {
                    logger.debug("Connected to bridge");
                    for (SinopeThermostatHandler sinopeThermostatHandler : thermostatHandlers) {
                        sinopeThermostatHandler.update();
                    }
                }
            } catch (IOException e) {
                logger.debug("Could not connect to gateway", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            } finally {
                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        logger.warn("Unexpected error when closing connection to gateway", e);
                    }
                }
            }
        } else {
            logger.debug("nothing to poll");
        }
    }

    boolean connectToBridge() throws UnknownHostException, IOException {
        SinopeConfig config = getConfigAs(SinopeConfig.class);
        if (this.clientSocket == null || !this.clientSocket.isConnected()) {
            this.clientSocket = new Socket(config.hostname, config.port);
            SinopeApiLoginRequest loginRequest = new SinopeApiLoginRequest(SinopeConfig.convert(config.gatewayId),
                    SinopeConfig.convert(config.apiKey));
            SinopeApiLoginAnswer loginAnswer = (SinopeApiLoginAnswer) execute(loginRequest);
            return loginAnswer.getStatus() == 0;
        }
        return true;
    }

    public synchronized byte[] newSeq() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(seq++).array();
    }

    synchronized SinopeAnswer execute(SinopeRequest command) throws UnknownHostException, IOException {
        Socket clientSocket = this.getClientSocket();
        OutputStream outToServer = clientSocket.getOutputStream();
        InputStream inputStream = clientSocket.getInputStream();
        outToServer.write(command.getPayload());
        outToServer.flush();
        SinopeAnswer answ = command.getReplyAnswer(inputStream);

        return answ;

    }

    SinopeAnswer execute(SinopeDataRequest command) throws UnknownHostException, IOException {
        Socket clientSocket = this.getClientSocket();
        OutputStream outToServer = clientSocket.getOutputStream();
        InputStream inputStream = clientSocket.getInputStream();

        outToServer.write(command.getPayload());

        SinopeDataAnswer answ = command.getReplyAnswer(inputStream);

        while (answ.getMore() == 0x01) {
            answ = command.getReplyAnswer(inputStream);

        }
        return answ;

    }

    private Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    public boolean registerThermostatHandler(SinopeThermostatHandler thermostatHandler) {
        if (thermostatHandler == null) {
            throw new NullPointerException("It's not allowed to pass a null thermostatHandler.");
        }
        boolean result = thermostatHandlers.add(thermostatHandler);
        if (result) {
            schedulePoll();
        }
        return result;
    }

    public boolean unregisterThermostatHandler(SinopeThermostatHandler thermostatHandler) {
        boolean result = thermostatHandlers.remove(thermostatHandler);
        if (result) {
            schedulePoll();
        }
        return result;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        Collection<ConfigStatusMessage> configStatusMessages = new LinkedList<>();

        SinopeConfig config = getConfigAs(SinopeConfig.class);
        if (config.hostname == null) {
            configStatusMessages.add(ConfigStatusMessage.Builder.error(SinopeBindingConstants.CONFIG_PROPERTY_HOST)
                    .withMessageKeySuffix(SinopeConfigStatusMessage.HOST_MISSING.getMessageKey())
                    .withArguments(SinopeBindingConstants.CONFIG_PROPERTY_HOST).build());
        }
        if (config.port == null) {
            configStatusMessages.add(ConfigStatusMessage.Builder.error(SinopeBindingConstants.CONFIG_PROPERTY_PORT)
                    .withMessageKeySuffix(SinopeConfigStatusMessage.PORT_MISSING.getMessageKey())
                    .withArguments(SinopeBindingConstants.CONFIG_PROPERTY_PORT).build());
        }

        if (config.gatewayId == null || SinopeConfig.convert(config.gatewayId) == null) {
            configStatusMessages
                    .add(ConfigStatusMessage.Builder.error(SinopeBindingConstants.CONFIG_PROPERTY_GATEWAY_ID)
                            .withMessageKeySuffix(SinopeConfigStatusMessage.GATEWAY_ID_INVALID.getMessageKey())
                            .withArguments(SinopeBindingConstants.CONFIG_PROPERTY_GATEWAY_ID).build());
        }
        if (config.apiKey == null || SinopeConfig.convert(config.apiKey) == null) {
            configStatusMessages.add(ConfigStatusMessage.Builder.error(SinopeBindingConstants.CONFIG_PROPERTY_API_KEY)
                    .withMessageKeySuffix(SinopeConfigStatusMessage.API_KEY_INVALID.getMessageKey())
                    .withArguments(SinopeBindingConstants.CONFIG_PROPERTY_API_KEY).build());
        }

        return configStatusMessages;
    }

    public void startSearch(final SinopeThingsDiscoveryService sinopeThingsDiscoveryService)
            throws UnknownHostException, IOException {
        // Stopping current polling
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }

        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    if (connectToBridge()) {
                        logger.info("Successful login");
                        try {
                            while (clientSocket.isConnected()) {
                                SinopeDeviceReportAnswer answ;
                                answ = new SinopeDeviceReportAnswer(clientSocket.getInputStream());
                                logger.debug("Got report answer: {}", answ);
                                logger.debug("Your device id is: {}", ByteUtil.toString(answ.getDeviceId()));
                                sinopeThingsDiscoveryService.newThermostat(answ.getDeviceId());
                            }

                        } finally {
                            clientSocket.close();
                            clientSocket = null;
                            schedulePoll();
                        }
                    }
                } catch (IOException e) {
                    logger.error("Cannot complete search with exception", e);
                }

            }
        }, 0, TimeUnit.SECONDS);

    }

    public void stopSearch() throws IOException {
        if (this.clientSocket != null && this.clientSocket.isConnected()) {
            this.clientSocket.close();
        }

    }

    public Socket getClientSocket() {
        return clientSocket;
    }
}
