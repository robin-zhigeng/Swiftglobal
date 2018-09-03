package com.higgsblock.global.chain.app.task;

import com.higgsblock.global.chain.app.blockchain.BlockIndex;
import com.higgsblock.global.chain.app.blockchain.IBlockChainService;
import com.higgsblock.global.chain.app.blockchain.listener.MessageCenter;
import com.higgsblock.global.chain.app.service.impl.BlockIndexService;
import com.higgsblock.global.chain.app.sync.message.Inventory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author baizhengwen
 * @date 2018/3/23
 */
@Slf4j
@Component
public class InventoryTask extends BaseTask {

    @Autowired
    private MessageCenter messageCenter;

    @Autowired
    private IBlockChainService blockChainService;

    @Autowired
    private BlockIndexService blockIndexService;

    @Override
    protected void task() {
        long height = blockChainService.getMaxHeight();
        BlockIndex blockIndex = blockIndexService.getBlockIndexByHeight(height);
        if (blockIndex != null &&
                CollectionUtils.isNotEmpty(blockIndex.getBlockHashs())) {
            Set<String> set = new HashSet<>(blockIndex.getBlockHashs());
            Inventory inventory = new Inventory(height, set);
            messageCenter.broadcast(inventory);
        }

    }

    @Override
    protected long getPeriodMs() {
        return TimeUnit.SECONDS.toMillis(3);
    }
}
