FROM openjdk:8-jdk as spigot-builder

WORKDIR /work
ADD https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar BuildTools.jar
RUN java -jar BuildTools.jar -o /artifact --rev 1.12.2

FROM openjdk:8-jdk

COPY --from=spigot-builder /artifact/ /spigot-files/
COPY docker/spigot/serverfiles/ /spigot-files/

RUN mkdir -p /spigot/
RUN rm -rf /spigot/plugins
RUN cp -Rf /spigot-files/* /spigot/

CMD cd /spigot/ && java -jar /spigot/spigot-1.12.2.jar nogui
