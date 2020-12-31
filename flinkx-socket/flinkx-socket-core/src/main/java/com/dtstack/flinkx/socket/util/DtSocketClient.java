/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.flinkx.socket.util;

import com.dtstack.flinkx.util.ExceptionUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.Serializable;
import java.util.concurrent.SynchronousQueue;

import static com.dtstack.flinkx.socket.constants.SocketCons.KEY_EXIT0;

/** 采用netty实现Socket Client
 * @author kunni.dtstack.com
 */

public class DtSocketClient implements Closeable, Serializable {

    private static final long serialVersionUID = 1L;

    protected String host;
    protected int port;

    protected String codeC;
    protected EventLoopGroup group = new NioEventLoopGroup();
    protected SynchronousQueue<Row> queue;

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    public Channel channel;

    public DtSocketClient(String host, int port, SynchronousQueue<Row> queue){
        this.host = host;
        this.port = port;
        this.queue = queue;
    }

    public void start() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new DtClientHandler(queue, codeC));
                    }
                });
        channel = bootstrap.connect(host, port).addListener(future -> {
            if(future.isSuccess()) {
                LOG.info("connect [{}:{}] success", host, port);
            }else {
                String error = String.format("connect [%s:%d] failed", host, port);
                try {
                    queue.put(Row.of(KEY_EXIT0 + error));
                } catch (InterruptedException ex) {
                    LOG.error(ExceptionUtil.getErrorMessage(ex));
                }
            }
        }).channel();
    }

    public void setCodeC(String codeC) {
        this.codeC = codeC;
    }

    @Override
    public void close() {
        LOG.error("close channel!!! ");
        if(channel != null){
            channel.close();
        }
        if(group != null) {
            group.shutdownGracefully();
        }
    }

}
