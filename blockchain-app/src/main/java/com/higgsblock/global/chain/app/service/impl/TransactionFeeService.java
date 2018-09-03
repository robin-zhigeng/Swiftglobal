package com.higgsblock.global.chain.app.service.impl;

import com.google.common.collect.Lists;
import com.higgsblock.global.chain.app.blockchain.Rewards;
import com.higgsblock.global.chain.app.blockchain.script.LockScript;
import com.higgsblock.global.chain.app.blockchain.transaction.*;
import com.higgsblock.global.chain.app.service.ITransactionFeeService;
import com.higgsblock.global.chain.app.service.IWitnessService;
import com.higgsblock.global.chain.app.utils.ISizeCounter;
import com.higgsblock.global.chain.app.utils.JsonSizeCounter;
import com.higgsblock.global.chain.common.utils.Money;
import com.higgsblock.global.chain.crypto.ECKey;
import com.higgsblock.global.chain.crypto.KeyPair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: yezaiyong
 * @create: 2018-07-21 14:23
 **/
@Slf4j
@Service
public class TransactionFeeService implements ITransactionFeeService {
    public static final Money MINER_REWARDS_RATION = new Money("0.8");

    public static final Money WITNESS_REWARDS_RATION = new Money("0.2");

    public static final Money BLOCK_REWARDS = new Money("3");

    public static final Money BLOCK_REWARDS_MINER = new Money("0.8");

    public static final Money BLOCK_REWARDS_WITNESS = new Money("0.2");

    public static final long WITNESS_NUM = 11;

    public static final Money FEE_OF_PEER_KB_CAS = new Money("0.02");

    public static final BigDecimal ONE_KB_OF_BYTES = new BigDecimal("1024");

    final ISizeCounter sizeCounter = JsonSizeCounter.getJsonSizeCounter();

    /**
     * block transactions limit exclude coinBase
     */
    private static final int LIMITED_SIZE = 1024 * 1000 * 1;

    @Autowired
    private KeyPair peerKeyPair;

    @Autowired
    private UTXOServiceProxy utxoServiceProxy;

    @Autowired
    private IWitnessService witnessService;

    /**
     * count Miner and Witness Rewards
     *
     * @param feeMap fee map
     * @return minerRewards, witnessRewards
     */
    @Override
    public Rewards countMinerAndWitnessRewards(Map<String, Money> feeMap, long height) {

        Money minerTotal;

        LOGGER.debug("miner rewards ration：{} ", MINER_REWARDS_RATION.getValue());
        Money totalFee = new Money("0");
        for (Map.Entry<String, Money> entry : feeMap.entrySet()) {
            Money fee = entry.getValue();
            totalFee.add(fee);
        }
        Money totalMoney = new Money(BLOCK_REWARDS.getValue());
        totalMoney.add(totalFee);

        Rewards rewards = new Rewards();
        rewards.setTotalFee(totalFee);
        rewards.setTotalMoney(totalMoney);
        LOGGER.debug("totalFee:{}", totalFee.getValue());

        //count miner rewards
        minerTotal = new Money(MINER_REWARDS_RATION.getValue()).multiply(totalFee);

        minerTotal.add(new Money(BLOCK_REWARDS_MINER.getValue()).multiply(BLOCK_REWARDS));
        rewards.setMinerTotal(minerTotal);
        LOGGER.debug("Transactions miner rewards total : {}", minerTotal.getValue());

        //count witness rewards
        Money witnessTotal = new Money(BLOCK_REWARDS.getValue()).multiply(BLOCK_REWARDS_WITNESS.getValue());

        witnessTotal.add(new Money(totalFee.getValue()).multiply(WITNESS_REWARDS_RATION));
        Money singleWitnessMoney = new Money(witnessTotal.getValue()).divide(WITNESS_NUM);
        Money lastWitnessMoney = new Money(witnessTotal.getValue()).subtract(new Money(singleWitnessMoney.getValue()).multiply(WITNESS_NUM - 1));

        LOGGER.debug("Transactions witness rewards total : {}, topTenSingleWitnessMoney:{}, lastWitnessMoney:{}", witnessTotal.getValue(),
                singleWitnessMoney.getValue(), lastWitnessMoney.getValue());

        rewards.setTopTenSingleWitnessMoney(singleWitnessMoney);
        rewards.setLastWitnessMoney(lastWitnessMoney);

        if (!rewards.check()) {
            LOGGER.info("count is error block height:{}", height);
            throw new RuntimeException("count money is error");
        }
        return rewards;
    }

    /**
     * computer fee by  transaction size
     *
     * @param size size of transaction bytes
     * @return system fee
     */
    public Money computerFeeBySize(long size) {
        BigDecimal sizeBD = new BigDecimal(size);
        Money fee = new Money(FEE_OF_PEER_KB_CAS.getValue()).multiply(sizeBD.divide(ONE_KB_OF_BYTES, 0, RoundingMode.CEILING).toEngineeringString());
        LOGGER.debug("count fee:{}", fee.getValue());
        return fee;
    }

    /**
     * get current block can be package transaction
     *
     * @param cacheTransactions cache transactions
     * @return transaction order by fee weight desc
     */
    @Override
    public List<Transaction> getCanPackageTransactionsOfBlock(List<Transaction> cacheTransactions) {

        final List<Transaction> packageTransactionIng = new ArrayList<>();
        Long sumTransactionSize = 0L;
        for (Transaction tx : cacheTransactions) {
            sumTransactionSize += sizeCounter.calculateSize(tx);
            if (sumTransactionSize > LIMITED_SIZE) {
                break;
            }
            packageTransactionIng.add(tx);
        }

        return packageTransactionIng;
    }

    /**
     * system computer transaction fee
     *
     * @param tx tx
     * @return fee
     */
    @Override
    public Money getCurrencyFee(Transaction tx) {

        return computerFeeBySize(sizeCounter.calculateSize(tx));

    }

    /**
     * @param lockTime lock time
     * @param version  transaction version
     * @param feeMap   fee map
     * @return return coin base transaction
     */
    @Override
    public Transaction buildCoinBaseTx(long lockTime, short version, Map<String, Money> feeMap, long height) {
        LOGGER.debug("begin to build coinBase transaction");

        Rewards rewards = countMinerAndWitnessRewards(feeMap, height);
        Transaction transaction = new Transaction();
        transaction.setVersion(version);
        transaction.setLockTime(lockTime);

        List<TransactionOutput> outputList = Lists.newArrayList();

        String producerAddress = ECKey.pubKey2Base58Address(peerKeyPair);
        TransactionOutput minerCoinBaseOutput = generateTransactionOutput(producerAddress, rewards.getMinerTotal());
        if (minerCoinBaseOutput != null) {
            outputList.add(minerCoinBaseOutput);
        }

        List<TransactionOutput> witnessCoinBaseOutput = genWitnessCoinBaseOutput(rewards, height);
        outputList.addAll(witnessCoinBaseOutput);

        if (CollectionUtils.isEmpty(outputList)) {
            return null;
        }
        transaction.setOutputs(outputList);

        return transaction;
    }

    /**
     * order transaction by transaction fee weight
     * if transactions size <= limit size don't order and return fee
     *
     * @param cacheTransactions cache transactions
     * @return return key is hash and  value is fee
     */
    @Override
    public SortResult orderTransaction(String preBlockHash, List<Transaction> cacheTransactions) {

        final Map<String, Money> transactionFees = new HashMap<>(cacheTransactions.size());
        long size = 0L;

        for (Transaction tx : cacheTransactions) {
            size += sizeCounter.calculateSize(tx);
            if (size > LIMITED_SIZE) {
                break;
            }
            Money txFee = getOneTransactionFee(preBlockHash, tx);

            transactionFees.put(tx.getHash(), txFee);
        }

        if (size <= LIMITED_SIZE) {
            return new SortResult(false, transactionFees);
        }

        //if size > limit size ,so order transaction by fee weight
        cacheTransactions.sort((tx1, tx2) -> {

            Money tx1Fee = transactionFees.get(tx1.getHash());
            if (tx1Fee == null) {
                tx1Fee = getOneTransactionFee(preBlockHash, tx1);
            }
            long tx1Size = sizeCounter.calculateSize(tx1);
            Money tx1Weight = tx1Fee.divide(new Money(tx1Size).divide(new Money(ONE_KB_OF_BYTES.toEngineeringString())));

            Money tx2Fee = transactionFees.get(tx2.getHash());
            if (tx2Fee == null) {
                tx2Fee = getOneTransactionFee(preBlockHash, tx2);
            }
            long tx2Size = sizeCounter.calculateSize(tx2);
            Money tx2Weight = tx2Fee.divide(new Money(tx2Size).divide(ONE_KB_OF_BYTES.toEngineeringString()));

            transactionFees.put(tx1.getHash(), tx1Fee);

            return tx2Weight.compareTo(tx1Weight);
        });

        return new SortResult(true, transactionFees);
    }


    private Money getOneTransactionFee(String preBlockHash, Transaction transaction) {
        List<TransactionInput> inputs = transaction.getInputs();
        List<TransactionOutput> outputs = transaction.getOutputs();

        Money preOutMoney = new Money("0");
        for (TransactionInput input : inputs) {
            String preOutKey = input.getPrevOut().getKey();

            UTXO utxo = utxoServiceProxy.getUnionUTXO(preBlockHash, preOutKey);
            if (null == utxo) {
                LOGGER.warn("get utxo is null:{}", preOutKey);
                throw new RuntimeException("uxto is null:" + preOutKey);
            }
            TransactionOutput output = utxo.getOutput();
            if (output.isCASCurrency()) {
                preOutMoney.add(output.getMoney());
            }
        }
        LOGGER.debug("Transactions' pre-output amount : {}", preOutMoney.getValue());

        Money outPutMoney = new Money("0");
        for (TransactionOutput output : outputs) {
            if (output.isCASCurrency()) {
                outPutMoney.add(output.getMoney());
            }
        }
        LOGGER.debug("Transactions' output amount : {}", outPutMoney.getValue());

        return preOutMoney.subtract(outPutMoney);
    }

    private TransactionOutput generateTransactionOutput(String address, Money money) {
        if (!ECKey.checkBase58Addr(address)) {
            return null;
        }
        if (!money.checkRange()) {
            return null;
        }
        TransactionOutput output = new TransactionOutput();
        output.setMoney(money);
        LockScript lockScript = new LockScript();
        lockScript.setAddress(address);
        output.setLockScript(lockScript);
        return output;
    }

    private List<TransactionOutput> genWitnessCoinBaseOutput(Rewards rewards, Long height) {
        List<TransactionOutput> outputList = Lists.newArrayList();
        int witnessSize = witnessService.getWitnessSize();
        long lastReward = height % WITNESS_NUM;
        for (int i = 0; i < witnessSize; i++) {
            if (lastReward == i) {
                TransactionOutput transactionOutput = generateTransactionOutput(WitnessService.WITNESS_ADDRESS_LIST.get(i), rewards.getLastWitnessMoney());
                outputList.add(transactionOutput);
            } else {
                TransactionOutput transactionOutput = generateTransactionOutput(WitnessService.WITNESS_ADDRESS_LIST.get(i), rewards.getTopTenSingleWitnessMoney());
                outputList.add(transactionOutput);
            }
        }

        return outputList;
    }

    /**
     * check count money
     *
     * @param coinBaseTransaction coin base transaction
     * @return if count money == totalRewardsMoney result true else result false
     */
    public boolean checkCoinBaseMoney(Transaction coinBaseTransaction, Money totalRewardsMoney) {
        final Money countMoney = new Money();
        coinBaseTransaction.getOutputs().forEach(item -> {
            countMoney.add(item.getMoney());
        });
        return countMoney.compareTo(totalRewardsMoney) == 0;
    }

}