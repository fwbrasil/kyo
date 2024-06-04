package kyo.grpc.compiler

object Types {

    val unit = "_root_.scala.Unit"

    def future(t: String) = s"_root_.scala.concurrent.Future[$t]"

    def pending(t: String, s: String) = s"_root_.kyo.<[$t, $s]"

    val grpcResponses = "_root_.kyo.grpc.GrpcResponses"

    def pendingGrpcResponses(t: String) = s"_root_.kyo.<[$t, $grpcResponses]"

    def streamObserver(v: String) = s"_root_.io.grpc.stub.StreamObserver[$v]"

}
