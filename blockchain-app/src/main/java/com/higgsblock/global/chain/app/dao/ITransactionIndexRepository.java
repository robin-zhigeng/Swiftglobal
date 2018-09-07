package com.higgsblock.global.chain.app.dao;

import com.higgsblock.global.chain.app.dao.entity.TransactionIndexEntity;
import com.higgsblock.global.chain.app.keyvalue.annotation.IndexQuery;
import com.higgsblock.global.chain.app.keyvalue.repository.IKeyValueRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * @author yangshenghong
 * @date 2018-07-13
 */
public interface ITransactionIndexRepository extends IKeyValueRepository<TransactionIndexEntity, Long> {

    @Override
    @CachePut(value = "TransactionIndex", key = "#p0.transactionHash", condition = "null != #p0 && null != #p0.transactionHash")
    TransactionIndexEntity save(TransactionIndexEntity entity);

    /**
     * find by txHash
     *
     * @param txHash
     * @return
     */
    @IndexQuery("transactionHash")
    @Cacheable(value = "TransactionIndex", key = "#p0", condition = "null != #p0", unless = "#result == null")
    TransactionIndexEntity findByTransactionHash(String txHash);
}
