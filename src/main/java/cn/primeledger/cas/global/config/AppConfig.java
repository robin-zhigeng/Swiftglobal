package cn.primeledger.cas.global.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

/**
 * system config
 *
 * @author baizhengwen
 * @create 2017-03-07 19:32
 */

@Getter
@Configuration
@PropertySource(value = "${spring.config.location}", name = "appConf")
public class AppConfig {

    @Autowired
    private Environment environment;

    @Value("${data.root.path}")
    private String rootDataPath;

    @Value("${data.blockchain.path}")
    private String blockChainDataPath;

    @Value("${data.blockchain.file}")
    private String blockChainDataFile;

    @Value("${peer.pubKey}")
    private String pubKey;

    @Value("${registry.center.ip}")
    private String registryCenterIp;

    @Value("${registry.center.port}")
    private int registryCenterPort;

    @Value("${peer.priKey}")
    private String priKey;

    @Value("${server.port}")
    private int httpServerPort;

    @Value("${p2p.maxOutboundConnections}")
    private int maxOutboundConnections;

    @Value("${p2p.maxInboundConnections}")
    private int maxInboundConnections;

    @Value("${p2p.serverListeningPort}")
    private int socketServerPort;

    @Value("${p2p.connectionTimeout}")
    private int connectionTimeout;

    @Value("${p2p.networkType}")
    private byte networkType;

    @Value("${p2p.clientPublicIp}")
    private String clientPublicIp;

    public String getValue(String key) {
        return environment.getProperty(key);
    }

    public <T> T getValue(String key, Class<T> clazz) {
        return environment.getProperty(key, clazz);
    }
}
