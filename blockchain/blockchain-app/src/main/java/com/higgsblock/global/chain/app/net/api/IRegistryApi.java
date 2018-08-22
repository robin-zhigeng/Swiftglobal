package com.higgsblock.global.chain.app.net.api;

import com.higgsblock.global.chain.app.net.peer.Peer;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import java.util.List;

/**
 * The interface Registry api.
 *
 * @author baizhengwen
 * @create 2018 -03-17
 */
public interface IRegistryApi {

    /**
     * Peers call.
     *
     * @return the call
     */
    @GET("/registry/peers")
    Call<List<Peer>> peers();

    /**
     * Report call.
     *
     * @param peer the peer
     * @return the call
     */
    @POST("/registry/report")
    Call<Boolean> report(@Body Peer peer);
}
