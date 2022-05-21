package org.apache.dubbo.rpc;

import org.apache.dubbo.common.extension.SPI;

import java.util.concurrent.CompletableFuture;

@SPI("default")
public interface AdaptiveFutureFactory {

    CompletableFuture<Object> getAdaptiveFuture(AsyncRpcResult asyncResult);
}
