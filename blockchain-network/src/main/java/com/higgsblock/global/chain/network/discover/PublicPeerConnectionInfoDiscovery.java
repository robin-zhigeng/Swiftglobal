package com.higgsblock.global.chain.network.discover;

import com.higgsblock.global.chain.network.upnp.UpnpManager;
import com.higgsblock.global.chain.network.utils.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The type Public peer connection info discovery.
 *
 * @author baizhengwen
 * @date 2018 -4-11
 */
@Slf4j
@Component("publicDiscovery")
public class PublicPeerConnectionInfoDiscovery implements IPeerConnectionInfoDiscovery {

    /**
     * The Upnp manager.
     */
    @Autowired
    private UpnpManager upnpManager;

    @Override
    public String getIp() {
        return IpUtil.getPublicIp();
    }

    @Override
    public int getSocketPort() {
        return upnpManager.getSocketMappingPort();
    }

    @Override
    public int getHttpPort() {
        return upnpManager.getHttpMappingPort();
    }
}