// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.api.Action
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.IncomingConnector
import org.gradle.internal.remote.internal.KryoBackedMessageSerializer
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import org.gradle.internal.remote.internal.inet.SocketConnection
import org.gradle.internal.serialize.StatefulSerializer
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class TargetIncomingConnector() : IncomingConnector {
  private val idGenerator = UUIDGenerator()
  private val addressFactory: InetAddressFactory = InetAddressFactory()

  private val executorFactory = DefaultExecutorFactory()
  override fun accept(action: Action<ConnectCompletion>, allowRemote: Boolean): ConnectionAcceptor {
    val serverSocket: ServerSocketChannel
    val bindingAddress = getBindingAddress()
    val localPort: Int
    try {
      val bindingPort = getBindingPort()
      serverSocket = ServerSocketChannel.open()
      serverSocket.socket().bind(InetSocketAddress(bindingAddress, bindingPort))
      localPort = serverSocket.socket().localPort
    }
    catch (e: Exception) {
      throw UncheckedException.throwAsUncheckedException(e)
    }
    val id = idGenerator.generateId()
    val addresses = listOf(bindingAddress)
    val address: Address = MultiChoiceAddress(id, localPort, addresses)
    logger.debug("Listening on $address.")
    val executor = executorFactory.create("Incoming ${if (allowRemote) "remote" else "local"} TCP Connector on port $localPort")
    executor.execute(Receiver(serverSocket, action, allowRemote))
    return object : ConnectionAcceptor {
      override fun getAddress() = address

      override fun requestStop() {
        CompositeStoppable.stoppable(serverSocket).stop()
      }

      override fun stop() {
        requestStop()
        executor.stop()
      }
    }
  }

  private fun getBindingPort() = System.getenv("serverBindingPort")?.toIntOrNull() ?: 0

  private fun getBindingAddress(): InetAddress {
    val bindingHost = System.getenv("serverBindingHost")
    if (bindingHost.isNullOrBlank()) {
      return addressFactory.localBindingAddress
    }
    else {
      val inetAddresses = InetAddresses()
      val inetAddress = inetAddresses.remote.find { it.hostName == bindingHost || it.hostAddress == bindingHost }
      return inetAddress ?: addressFactory.localBindingAddress
    }
  }

  private inner class Receiver(private val serverSocket: ServerSocketChannel,
                               private val action: Action<ConnectCompletion>,
                               private val allowRemote: Boolean) : Runnable {
    override fun run() {
      try {
        while (true) {
          val socket = serverSocket.accept()
          val remoteSocketAddress = socket.socket().remoteSocketAddress as InetSocketAddress
          val remoteInetAddress = remoteSocketAddress.address
          if (!allowRemote && !addressFactory.isCommunicationAddress(remoteInetAddress)) {
            logger.error("Cannot accept connection from remote address $remoteInetAddress.")
            socket.close()
          }
          else {
            logger.debug("Accepted connection from {} to {}.", socket.socket().remoteSocketAddress, socket.socket().localSocketAddress)
            try {
              action.execute(SocketConnectCompletion(socket))
            }
            catch (t: Throwable) {
              socket.close()
              throw t
            }
          }
        }
      }
      catch (ignore: ClosedChannelException) {
      }
      catch (t: Throwable) {
        logger.error("Could not accept remote connection.", t)
      }
      finally {
        CompositeStoppable.stoppable(serverSocket).stop()
      }
    }
  }

  internal class SocketConnectCompletion(private val socket: SocketChannel) : ConnectCompletion {
    override fun toString(): String {
      return socket.socket().localSocketAddress.toString() + " to " + socket.socket().remoteSocketAddress
    }

    override fun <T> create(serializer: StatefulSerializer<T>): RemoteConnection<T> {
      return SocketConnection(socket, KryoBackedMessageSerializer(), serializer)
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(TargetIncomingConnector::class.java)
  }
}