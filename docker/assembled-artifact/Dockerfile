FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 as builder

WORKDIR /work
COPY / /work/
RUN sbt assembly
RUN cp /work/target/build/BungeeSemaphore.jar /build

FROM scratch
COPY --from=builder /work/target/build/BungeeSemaphore.jar /build/BungeeSemaphore.jar
