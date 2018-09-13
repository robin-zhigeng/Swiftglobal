package com.higgsblock.global.chain.app.context;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.higgsblock.global.chain.app.blockchain.OrphanBlockCacheManager;
import com.higgsblock.global.chain.app.blockchain.WitnessTimer;
import com.higgsblock.global.chain.app.blockchain.consensus.handler.OriginalBlockHandler;
import com.higgsblock.global.chain.app.blockchain.consensus.handler.VoteTableHandler;
import com.higgsblock.global.chain.app.blockchain.consensus.handler.VotingBlockRequestHandler;
import com.higgsblock.global.chain.app.blockchain.consensus.handler.VotingBlockResponseHandler;
import com.higgsblock.global.chain.app.blockchain.handler.BlockHandler;
import com.higgsblock.global.chain.app.blockchain.listener.MiningListener;
import com.higgsblock.global.chain.app.blockchain.transaction.handler.TransactionHandler;
import com.higgsblock.global.chain.app.common.SystemStatus;
import com.higgsblock.global.chain.app.common.SystemStatusManager;
import com.higgsblock.global.chain.app.common.SystemStepEnum;
import com.higgsblock.global.chain.app.common.event.SystemStatusEvent;
import com.higgsblock.global.chain.app.net.connection.ConnectionManager;
import com.higgsblock.global.chain.app.net.handler.HelloAckMessageHandler;
import com.higgsblock.global.chain.app.net.handler.HelloMessageHandler;
import com.higgsblock.global.chain.app.net.handler.PeersMessageHandler;
import com.higgsblock.global.chain.app.net.listener.ChannelChangedListener;
import com.higgsblock.global.chain.app.service.impl.BlockService;
import com.higgsblock.global.chain.app.service.impl.VoteService;
import com.higgsblock.global.chain.app.sync.SyncBlockInRunningService;
import com.higgsblock.global.chain.app.sync.SyncBlockInStartupService;
import com.higgsblock.global.chain.app.sync.handler.*;
import com.higgsblock.global.chain.app.task.*;
import com.higgsblock.global.chain.common.eventbus.listener.IEventBusListener;
import com.higgsblock.global.chain.network.socket.MessageReceiver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author baizhengwen
 * @date 2018/3/22
 */
@Component
@Slf4j
public class AppContext implements IEventBusListener {

    @Autowired
    private BlockService blockService;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private SyncBlockInStartupService syncBlockInStartupService;

    @Autowired
    private SyncBlockInRunningService syncBlockInRunningService;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SystemStatusManager systemStatusManager;

    /**
     * =================all tasks:
     */
    @Autowired
    private InventoryTask inventoryTask;
    @Autowired
    private GuarderTask guarderTask;
    @Autowired
    private WitnessTimer witnessTimer;
    @Autowired
    private PeerManageTask peerManageTask;
    @Autowired
    private InetAddressCheckTask inetAddressCheckTask;
    @Autowired
    private ConnectionManageTask connectionManageTask;
    @Autowired
    private GetMaxHeightTask getMaxHeightTask;
    @Autowired
    private DBMonitorTask dbMonitorTask;

    /**
     * =================all handlers:
     */
    @Autowired
    private PeersMessageHandler peersMessageHandler;
    @Autowired
    private HelloAckMessageHandler helloAckMessageHandler;
    @Autowired
    private OriginalBlockHandler originalBlockHandler;
    @Autowired
    private BlockRequestHandler blockRequestHandler;
    @Autowired
    private BlockResponseHandler blockResponseHandler;
    @Autowired
    private InventoryHandler inventoryHandler;
    @Autowired
    private TransactionHandler transactionHandler;
    @Autowired
    private MaxHeightResponseHandler maxHeightResponseHandler;
    @Autowired
    private VotingBlockResponseHandler votingBlockResponseHandler;
    @Autowired
    private VoteTableHandler voteTableHandler;
    @Autowired
    private VotingBlockRequestHandler votingBlockRequestHandler;
    @Autowired
    private MaxHeightRequestHandler maxHeightRequestHandler;
    @Autowired
    private BlockHandler blockHandler;
    @Autowired
    private HelloMessageHandler helloMessageHandler;

    /**
     * =================all listener:
     */
    @Autowired
    private OrphanBlockCacheManager orphanBlockCacheManager;
    @Autowired
    private MiningListener miningListener;
    @Autowired
    private ChannelChangedListener channelChangedListener;

    @Autowired
    private VoteService voteService;

    @Autowired
    private MessageReceiver messageReceiver;

    public void start() throws Exception {
        LOGGER.info("Application starting ...");

        checkAndRecoveryBlockData();
        LOGGER.info("First Step: Application startup detection is complete ==>>");

        startFirstStepHandlers();
        LOGGER.info("Second Step: StartFirstStepHandlers is complete ==>>");

        startFirstStepListeners();
        LOGGER.info("Third Step: StartFirstStepListeners is complete ==>>");

        startSocketServer();
        LOGGER.info("Fourth Step: StartSocketServer is complete ==>>");

        startFirstStepTasks();
        LOGGER.info("Fifth Step: StartFirstStepTasks is complete ==>>");

        syncBlocks();
        LOGGER.info("Sixth Step: SyncBlocks is complete ==>>");

        LOGGER.info("Application service started successfully.");
    }

    private void checkAndRecoveryBlockData() {
        blockService.loadAllBlockData();
    }

    private void startFirstStepHandlers() {
        peersMessageHandler.start();
        helloAckMessageHandler.start();
        helloMessageHandler.start();
        blockResponseHandler.start();
        maxHeightResponseHandler.start();
        maxHeightRequestHandler.start();
    }

    private void startHandlersAfterSyncedBlocks() {
        transactionHandler.start();
        inventoryHandler.start();
        blockRequestHandler.start();
        blockHandler.start();
        originalBlockHandler.start();
        votingBlockResponseHandler.start();
        voteTableHandler.start();
        votingBlockRequestHandler.start();
    }

    private void startSocketServer() {
        connectionManager.startServer();
    }

    private void startFirstStepTasks() {
        peerManageTask.start();
        inetAddressCheckTask.start();
        connectionManageTask.start();
        getMaxHeightTask.start();
        dbMonitorTask.start();
    }

    private void startTasksAfterSyncedBlocks() {
        inventoryTask.start();
        guarderTask.start();

    }

    private void startFirstStepListeners() {
        eventBus.register(channelChangedListener);
        eventBus.register(messageReceiver);
        eventBus.register(orphanBlockCacheManager);
        eventBus.register(this);
    }

    private void startListenersAfterSyncedBlocks() {
        miningListener.start();
        eventBus.register(miningListener);
        voteService.start();
        eventBus.register(voteService);
        witnessTimer.start();
        eventBus.register(witnessTimer);
        eventBus.unregister(syncBlockInStartupService);
        eventBus.register(syncBlockInRunningService);
        eventBus.register(guarderTask);
    }

    private void syncBlocks() {
        syncBlockInStartupService.startSyncBlock();
        eventBus.register(syncBlockInStartupService);
    }

    @Subscribe
    public void process(SystemStatusEvent event) {
        LOGGER.info("start flow process SystemStatusEvent: {}", event);
        SystemStatus state = event.getSystemStatus();
        if (SystemStatus.SYNC_FINISHED == state) {
            startHandlersAfterSyncedBlocks();
            startListenersAfterSyncedBlocks();
            startTasksAfterSyncedBlocks();
            getMaxHeightTask.stop();
            systemStatusManager.setSysStep(SystemStepEnum.START_FINISHED);
        }
    }
}
