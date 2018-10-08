package com.higgsblock.global.chain.app.task;

import com.google.common.eventbus.Subscribe;
import com.higgsblock.global.chain.app.blockchain.Block;
import com.higgsblock.global.chain.app.blockchain.BlockIndex;
import com.higgsblock.global.chain.app.blockchain.IBlockChainService;
import com.higgsblock.global.chain.app.common.event.BlockPersistedEvent;
import com.higgsblock.global.chain.app.net.peer.PeerManager;
import com.higgsblock.global.chain.app.service.IBlockService;
import com.higgsblock.global.chain.app.service.IOriginalBlockService;
import com.higgsblock.global.chain.app.service.impl.BlockIndexService;
import com.higgsblock.global.chain.common.eventbus.listener.IEventBusListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: yezaiyong
 * @create: 2018-07-17 10:44
 **/
@Component
@Slf4j
public class GuarderTask extends BaseTask implements IEventBusListener {
    private static volatile long currHeight = 1;
    private static final long WAIT_MINER_TIME = 30;
    private static volatile long curSec = 0;
    private static final long TASK_TIME = 5;


    @Autowired
    private IBlockChainService blockChainService;
    @Autowired
    private IBlockService blockService;
    @Autowired
    private IOriginalBlockService originalBlockProcessor;
    @Autowired
    private PeerManager peerManager;
    @Autowired
    private BlockIndexService blockIndexService;

    @Subscribe
    public void process(BlockPersistedEvent event) {
        long maxHeight = event.getHeight();
        LOGGER.debug("BlockPersistedEvent to Guarder height={}", maxHeight);
        if (maxHeight > currHeight) {
            currHeight = maxHeight;
            curSec = 0;
        }
    }

    /**
     * Task.
     */
    @Override
    protected void task() {
        curSec += TASK_TIME;
        currHeight = blockChainService.getMaxHeight();
        if (curSec >= WAIT_MINER_TIME) {
            LOGGER.info("guarder begin doming curSec={} currHeight={}", curSec, currHeight);
            doMining();
        }
    }

    /**
     * Gets period ms.
     *
     * @return the period ms
     */
    @Override
    protected long getPeriodMs() {
        return TimeUnit.SECONDS.toMillis(TASK_TIME);
    }


    private void doMining() {
        long expectHeight = currHeight + 1;
        try {
            BlockIndex maxBlockIndex = blockIndexService.getBlockIndexByHeight(currHeight);
            if (maxBlockIndex == null) {
                LOGGER.info("the blockIndex not found ,current height={}", currHeight);
                return;
            }
            for (String blockHash : maxBlockIndex.getBlockHashs()) {
                String address = peerManager.getSelf().getId();
                if (!blockChainService.isGuarder(address, blockHash)) {
                    LOGGER.info("this miner no guarder currency, address={}, block hash={}", address, blockHash);
                    return;
                }
                LOGGER.info("begin to packageNewBlock,height={},preBlcokHash={},this guarder address ={}", expectHeight, blockHash, address);
                Block block = blockService.packageNewBlock(blockHash);
                if (block == null) {
                    LOGGER.info("can not produce a new block,height={},preBlcokHash={}", expectHeight, blockHash);
                    return;
                }
                long maxHeight = blockChainService.getMaxHeight();
                if (block.getHeight() <= maxHeight) {
                    LOGGER.info("the expect block height={}, but max height={}", block.getHeight(), maxHeight);
                    return;
                }

                if (expectHeight != block.getHeight()) {
                    LOGGER.info("the expect height={}, but block height={}", expectHeight, block.getHeight());
                    return;
                }
                originalBlockProcessor.sendOriginBlockToWitness(block);
            }
        } catch (Exception e) {
            LOGGER.error("doMining exception,height={}", expectHeight, e);
        }
        LOGGER.info("guarder produce a new block,height={}", expectHeight);
    }
}