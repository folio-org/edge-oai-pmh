ARG docker_version=19.03

FROM folioci/alpine-jre-openjdk17:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

ENV VERTICLE_FILE edge-oai-pmh-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles
ENV DOCKER_API_VERSION=1.39

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
