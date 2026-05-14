FROM openjdk:11.0.11-jre-slim AS jre_source
# ubuntu 作为基础镜像。
FROM ubuntu:24.04

# 时区
ENV TZ="Asia/Shanghai"

# 安装依赖
RUN DEBIAN_FRONTEND=noninteractive \
    apt-get update && \
    apt-get install -y --no-install-recommends \
    gosu \
    ca-certificates \
    curl unzip \
    libgl1 fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache

ENV JAVA_HOME=/usr/local/openjdk-11
ENV LANG="en_US.UTF-8" \
    LANGUAGE="en_US.UTF-8" \
    LC_ALL="C" \
    PATH="$JAVA_HOME/bin:$PATH"
COPY --from=jre_source "$JAVA_HOME" "$JAVA_HOME"

COPY enterpoint.sh /enterpoint.sh
RUN chmod +x /enterpoint.sh && \
    useradd --shell /bin/bash -u 1001 -m java && \
    HOME=/home/java /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/JetBrains/JetBrainsMono/master/install_manual.sh)" && \
    chown -R java:java /home/java/.local

WORKDIR /app
COPY config.template.yml config.yml
COPY build/libs/http-server-linux.jar app.jar

ENTRYPOINT ["/enterpoint.sh"]
