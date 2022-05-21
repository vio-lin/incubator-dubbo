package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

@SPI("default")
public interface InvokeModeParser {

    InvokeMode parse(URL url, Invocation invocation);
}
