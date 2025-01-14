package kyo.server

import sttp.tapir.server.netty.NettyKyoServer
import sttp.tapir.server.netty.NettyKyoServerBinding

trait RoutesPlatformSpecific:

    type Server = NettyKyoServer
    object Server:
        def apply(): Server = NettyKyoServer()

    type ServerBinding = NettyKyoServerBinding

end RoutesPlatformSpecific
