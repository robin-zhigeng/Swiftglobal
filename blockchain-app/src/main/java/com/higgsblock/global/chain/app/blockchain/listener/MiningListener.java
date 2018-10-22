package com.higgsblock.global.chain.app.blockchain.listener;

import com.google.common.eventbus.Subscribe;
import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.blockchain.BlockIndex;
import com.higgsblock.global.chain.app.common.event.BlockPersistedEvent;
import com.higgsblock.global.chain.app.net.peer.PeerManager;
import com.higgsblock.global.chain.app.service.IDposService;
import com.higgsblock.global.chain.app.service.IOriginalBlockService;
import com.higgsblock.global.chain.app.service.impl.BlockIndexService;
import com.higgsblock.global.chain.app.service.impl.BlockService;
import com.higgsblock.global.chain.common.eventbus.listener.IEventBusListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * @author baizhengwen
 * @date 2018/4/2
 */
@Slf4j
@Component
public class MiningListener implements IEventBusListener {

    @Autowired
    private IOriginalBlockService originalBlockService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private PeerManager peerManager;
    @Autowired
    private IDposService dposService;

    @Autowired
    private BlockIndexService blockIndexService;

    /**
     * the block height which is produced recently
     */
    private volatile long miningHeight;
    private ExecutorService executorService = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(3), new ThreadPoolExecutor.DiscardOldestPolicy());
    private Future<?> future;
    private boolean isMining;

    @Subscribe
    public void process(BlockPersistedEvent event) {
        LOGGER.info("process event: {}", event);
        if (!isMining) {
            LOGGER.info("The system is not ready, cannot mining");
            return;
        }
        process(event.getBlockHash(), event.getHeight());
    }

    public void start() {
        isMining = true;
        //add by huangshengli  input pre blockhash when try to produce new block 2018-07-09
        BlockIndex lastBlockIndex = blockIndexService.getLastBlockIndex();
        LOGGER.info("The system is ready, start mining,max height={}", lastBlockIndex.getHeight());
        if (lastBlockIndex.hasBestBlock()) {
            process(lastBlockIndex.getBestBlockHash(), lastBlockIndex.getHeight());
        } else {
            process(lastBlockIndex.getFirstBlockHash(), lastBlockIndex.getHeight());
        }
    }

    /**
     * produce a block with a specified height
     */
    private synchronized void process(String persistBlockHash, long maxHeight) {
        long expectHeight = maxHeight + 1;

        if (expectHeight < miningHeight) {
            LOGGER.info("block is produced, height={}", expectHeight);
            return;
        }
        if (expectHeight == miningHeight) {
            LOGGER.info("mining task is running, height={}", miningHeight);
            return;
        }

        // cancel running task
        if (null != future) {
            future.cancel(true);
            future = null;
            LOGGER.info("cancel mining task, height={}", miningHeight);
        }
        // check if my turn now
        String address = peerManager.getSelf().getId();
        boolean isMyTurn = dposService.canPackBlock(expectHeight, address, persistBlockHash);
        if (!isMyTurn) {
            return;
        }

        miningHeight = expectHeight;

        future = executorService.submit(() -> mining(expectHeight, persistBlockHash));
        int queueSize = ((ThreadPoolExecutor) executorService).getQueue().size();
        int poolSize = ((ThreadPoolExecutor) executorService).getPoolSize();

        LOGGER.info("try to produce block, height={},preBlockHash={},queueSize={},poolSize={}", expectHeight, persistBlockHash, queueSize, poolSize);
    }

    private void mining(long expectHeight, String blockHash) {
        while ((miningHeight == expectHeight) && !doMining(expectHeight, blockHash)) {
            try {
                TimeUnit.MILLISECONDS.sleep(1000 + RandomUtils.nextInt(10) * 500);
            } catch (InterruptedException e) {
                LOGGER.error(String.format("mining task[height=%s] is interrupted,exit current thread", expectHeight), e);
                return;
            } catch (Exception e) {
                LOGGER.error(String.format("mining exception,height=%s", expectHeight), e);
            }
        }
    }

    private boolean doMining(long expectHeight, String blockHash) {
        try {
            LOGGER.info("begin to packageNewBlock,height={}", expectHeight);
            Block block = blockService.packageNewBlock(blockHash);
            if (block == null) {
                LOGGER.info("can not produce a new block,height={}", expectHeight);
                return false;
            }
            if (expectHeight != block.getHeight()) {
                LOGGER.warn("the expect height={}, but {}", expectHeight, block.getHeight());
                return true;
            }
            originalBlockService.sendOriginBlockToWitness(block);
            return true;
        } catch (Exception e) {
            LOGGER.error(String.format("mining exception,height=%s", expectHeight), e);
        }
        return false;
    }
}
