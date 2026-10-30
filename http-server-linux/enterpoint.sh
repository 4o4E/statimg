#!/bin/bash

# 修正文件归属
chown -R java /app

# 填充占位符
sed -i s/{SERVER_HOST}/$SERVER_HOST/ config.yml
sed -i s/{SERVER_PORT}/$SERVER_PORT/ config.yml
sed -i s/{PROXY_TYPE}/$PROXY_TYPE/ config.yml
sed -i s/{PROXY_HOST}/$PROXY_HOST/ config.yml
sed -i s/{PROXY_PORT}/$PROXY_PORT/ config.yml
sed -i s/{GITHUB_TOKEN}/$GITHUB_TOKEN/ config.yml

if [ ! -n "$START_CMD" ]; then
  START_CMD="java -jar app.jar"
fi
gosu java $START_CMD
