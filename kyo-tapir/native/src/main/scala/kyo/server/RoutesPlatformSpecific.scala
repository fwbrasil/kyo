package kyo.server

trait RoutesPlatformSpecific:

    type Server = Nothing
    object Server:
        def apply(): Server = ???

    type ServerBinding = Nothing

end RoutesPlatformSpecific
