package com.higgsblock.global.chain.app.context;

import com.google.common.eventbus.EventBus;
import com.higgsblock.global.chain.app.blockchain.WitnessTimerProcessor;
import com.higgsblock.global.chain.app.common.handler.IMessageHandler;
import com.higgsblock.global.chain.app.net.ConnectionManager;
import com.higgsblock.global.chain.app.service.impl.BlockService;
import com.higgsblock.global.chain.app.sync.SyncBlockService;
import com.higgsblock.global.chain.app.task.BaseTask;
import com.higgsblock.global.chain.common.eventbus.listener.IEventBusListener;
import com.higgsblock.global.chain.network.PeerManager;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author baizhengwen
 * @date 2018/3/22
 */
@Component
public class AppContext {

    @Autowired
    private BlockService blockService;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private PeerManager peerManager;

    @Autowired
    private List<BaseTask> tasks;

    @Autowired
    private List<IMessageHandler> messageHandlers;

    @Autowired
    private SyncBlockService syncBlockService;

    @Autowired
    private List<IEventBusListener> eventBusListeners;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private WitnessTimerProcessor witnessTimerProcessor;

    public void start() throws Exception {
        checkAndRecoveryBlockData();

        startHandlers();

        startListeners();

        startSocketServer();

        loadSelfPeerInfo();

        loadOrFetchPeers();

        startPeerTimerTasks();

        syncBlocks();

        startWitnessTimer();

    }

    private void checkAndRecoveryBlockData() {
        blockService.loadAllBlockData();
    }

    private void startHandlers() {
        if (CollectionUtils.isNotEmpty(messageHandlers)) {
            messageHandlers.forEach(IMessageHandler::start);
        }
    }

    private void startListeners() {
        CollectionUtils.forAllDo(eventBusListeners, eventBus::register);
    }

    private void startSocketServer() {
        connectionManager.startServer();
    }

    private void loadSelfPeerInfo() {
        peerManager.loadSelfPeerInfo();
        peerManager.reportToRegistry();
    }

    private void loadOrFetchPeers() {
        peerManager.loadNeighborPeers();
        connectionManager.connectToPeers(1, 5, 20);
    }

    private void startPeerTimerTasks() {
        if (CollectionUtils.isNotEmpty(tasks)) {
            tasks.forEach(BaseTask::start);
        }
    }

    private void syncBlocks() {
        syncBlockService.startSyncBlock();
    }


    private void startWitnessTimer() {
        witnessTimerProcessor.initWitnessTime();
    }

}
