package com.higgsblock.global.chain.app.config;

import com.higgsblock.global.chain.crypto.KeyPair;
import com.higgsblock.global.chain.network.config.PeerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Su Jiulong
 * @date 2018-3-2
 */
@Configuration
public class KeyPairConfig {

    @Bean
    public KeyPair peerKeyPair(PeerConfig config) {
        KeyPair keyPair = new KeyPair();
        keyPair.setPriKey(config.getPriKey());
        keyPair.setPubKey(config.getPubKey());
        return keyPair;
    }
}
